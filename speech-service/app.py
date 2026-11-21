"""Local speech recognition service for the assistant.

Runs AI4Bharat IndicConformer (MIT licence, 22 Indian languages including Odia) on this machine and exposes two
loopback-only endpoints that the Spring app calls:

    GET  /health                   -> {"status": "...", "languages": [...]}
    POST /transcribe?lang=or       body: 16 kHz mono 16-bit WAV -> {"text": "..."}

Audio never leaves the computer. The model is downloaded once from Hugging Face (free account and token needed,
because the model is gated) and is then served from the local cache.

The model-specific call lives in ``load_model`` and ``IndicConformer.transcribe`` only; everything else is
model-independent and is tested with a fake model (see tests/).
"""

from __future__ import annotations

import io
import logging
import os
import threading
import wave
from typing import Callable, Protocol

import numpy as np
from fastapi import FastAPI, HTTPException, Query, Request

log = logging.getLogger("speech-service")

MODEL_ID = "ai4bharat/indic-conformer-600m-multilingual"
# Pinned on purpose: loading this model runs Python code that ships inside the model repository
# (trust_remote_code). Pinning to a reviewed commit stops that code from changing underneath you.
MODEL_REVISION = os.environ.get("SPEECH_MODEL_REVISION", "2a77d305038dfc502fd9464e7d028b90381976cb")

SAMPLE_RATE = 16_000
MAX_AUDIO_BYTES = int(os.environ.get("SPEECH_MAX_AUDIO_BYTES", "1000000"))
MIN_AUDIO_BYTES = 44 + SAMPLE_RATE // 4 * 2  # header + a quarter of a second

# Languages the model has an output head for (one joint_post_net_<code>.onnx per language in the repository).
SUPPORTED_LANGUAGES = frozenset(
    "as bn brx doi gu hi kn kok ks mai ml mni mr ne or pa sa sat sd ta te ur".split()
)


class Recognizer(Protocol):
    def transcribe(self, samples: np.ndarray, lang: str) -> str: ...


class ModelUnavailable(Exception):
    """The model is not on this machine or could not be loaded."""


def decode_wav(data: bytes) -> np.ndarray:
    """Returns float32 samples in [-1, 1] from a 16 kHz mono 16-bit PCM WAV, or raises ValueError."""
    if len(data) > MAX_AUDIO_BYTES:
        raise ValueError("The recording is too long")
    if len(data) < MIN_AUDIO_BYTES:
        raise ValueError("The recording is too short")
    try:
        with wave.open(io.BytesIO(data), "rb") as wav:
            if (wav.getnchannels(), wav.getsampwidth(), wav.getframerate()) != (1, 2, SAMPLE_RATE):
                raise ValueError("The audio must be 16 kHz mono 16-bit PCM")
            frames = wav.readframes(wav.getnframes())
    except (wave.Error, EOFError) as error:
        raise ValueError("The audio must be a WAV file") from error
    return np.frombuffer(frames, dtype="<i2").astype(np.float32) / 32768.0


class IndicConformer:
    """Thin adapter around the model's own ``forward(wav, lang, decoding)`` entry point."""

    def __init__(self, model, decoding: str = "ctc") -> None:
        self._model = model
        self._decoding = decoding

    def transcribe(self, samples: np.ndarray, lang: str) -> str:
        import torch  # imported here so the service and its tests start without torch installed

        wav = torch.from_numpy(samples).unsqueeze(0)  # shape (1, n_samples)
        with torch.inference_mode():
            result = self._model(wav, lang, self._decoding)
        return str(result).strip()


def load_model() -> Recognizer:
    """Loads the model from the local Hugging Face cache, downloading it on first use.

    Raises ModelUnavailable with a message that says what to do, never the raw exception text, so tokens and
    paths cannot leak into responses.
    """
    try:
        from transformers import AutoModel
    except ImportError as error:
        raise ModelUnavailable("transformers is not installed; run: pip install -r requirements.txt") from error
    try:
        model = AutoModel.from_pretrained(MODEL_ID, revision=MODEL_REVISION, trust_remote_code=True)
    except Exception as error:  # noqa: BLE001 - any failure here means "model not usable", reported generically
        log.error("Could not load %s: %s", MODEL_ID, type(error).__name__)
        raise ModelUnavailable(
            "The model could not be loaded. Accept its terms on Hugging Face and set HF_TOKEN, then restart."
        ) from error
    return IndicConformer(model)


class ModelHolder:
    """Loads the model once in the background and reports its state; never blocks request handling."""

    def __init__(self, loader: Callable[[], Recognizer]) -> None:
        self._loader = loader
        self._lock = threading.Lock()
        self._state = "loading"
        self._recognizer: Recognizer | None = None
        self._infer_lock = threading.Lock()  # one inference at a time; the model already uses every core

    def start(self) -> None:
        threading.Thread(target=self._load, name="model-loader", daemon=True).start()

    def _load(self) -> None:
        try:
            recognizer = self._loader()
        except ModelUnavailable:
            with self._lock:
                self._state = "model_missing"
            return
        except Exception:  # noqa: BLE001
            log.exception("Unexpected error while loading the model")
            with self._lock:
                self._state = "error"
            return
        with self._lock:
            self._recognizer = recognizer
            self._state = "ready"

    @property
    def state(self) -> str:
        with self._lock:
            return self._state

    def transcribe(self, samples: np.ndarray, lang: str) -> str:
        with self._lock:
            recognizer = self._recognizer
        if recognizer is None:
            raise ModelUnavailable("The model is not ready")
        with self._infer_lock:
            return recognizer.transcribe(samples, lang)


def create_app(loader: Callable[[], Recognizer] = load_model, autostart: bool = True) -> FastAPI:
    app = FastAPI(title="Local speech recognition", docs_url=None, redoc_url=None, openapi_url=None)
    holder = ModelHolder(loader)
    app.state.holder = holder
    if autostart:
        holder.start()

    @app.get("/health")
    def health() -> dict:
        return {"status": holder.state, "languages": sorted(SUPPORTED_LANGUAGES)}

    @app.post("/transcribe")
    async def transcribe(request: Request, lang: str = Query(min_length=2, max_length=3)) -> dict:
        if lang not in SUPPORTED_LANGUAGES:
            raise HTTPException(status_code=400, detail="Unsupported language")
        body = await request.body()
        try:
            samples = decode_wav(body)
        except ValueError as error:
            raise HTTPException(status_code=400, detail=str(error)) from error
        if holder.state != "ready":
            raise HTTPException(status_code=503, detail="The model is not ready")
        try:
            text = holder.transcribe(samples, lang)
        except ModelUnavailable as error:
            raise HTTPException(status_code=503, detail="The model is not ready") from error
        except Exception:  # noqa: BLE001
            log.exception("Transcription failed")
            raise HTTPException(status_code=500, detail="Transcription failed") from None
        return {"text": text}

    return app


app = create_app()
