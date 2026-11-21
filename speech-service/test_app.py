"""Tests for the speech service with a fake model: no model download, no torch, no network."""

import io
import struct
import time
import wave

import numpy as np
import pytest
from fastapi.testclient import TestClient

import app as service

ODIA = "\u0b15\u0b30\u0b4d\u0b2e\u0b1a\u0b3e\u0b30\u0b40\u0b19\u0b4d\u0b15 \u0b1b\u0b41\u0b1f\u0b3f"


def make_wav(seconds=1.0, rate=16000, channels=1, width=2):
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(channels)
        wav.setsampwidth(width)
        wav.setframerate(rate)
        wav.writeframes(b"\x00" * int(rate * seconds) * channels * width)
    return buffer.getvalue()


class FakeRecognizer:
    def __init__(self, text=ODIA):
        self.text = text
        self.calls = []

    def transcribe(self, samples, lang):
        self.calls.append((samples.shape, samples.dtype, lang))
        return self.text


def ready_client(recognizer=None):
    recognizer = recognizer or FakeRecognizer()
    client = TestClient(service.create_app(loader=lambda: recognizer))
    wait_for(client, "ready")
    return client, recognizer


def wait_for(client, state, timeout=5.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if client.get("/health").json()["status"] == state:
            return
        time.sleep(0.02)
    raise AssertionError(f"state never became {state}: {client.get('/health').json()}")


def test_health_reports_ready_and_the_22_languages_including_odia():
    client, _ = ready_client()
    body = client.get("/health").json()
    assert body["status"] == "ready"
    assert "or" in body["languages"] and "hi" in body["languages"]
    assert "en" not in body["languages"]
    assert len(body["languages"]) == 22


def test_health_reports_model_missing_when_the_loader_says_so():
    def loader():
        raise service.ModelUnavailable("no model")

    client = TestClient(service.create_app(loader=loader))
    wait_for(client, "model_missing")
    response = client.post("/transcribe?lang=or", content=make_wav())
    assert response.status_code == 503


def test_health_reports_error_for_an_unexpected_loader_failure():
    def loader():
        raise RuntimeError("boom with /secret/path and token hf_abc")

    client = TestClient(service.create_app(loader=loader))
    wait_for(client, "error")
    assert "hf_abc" not in client.get("/health").text


def test_transcribe_returns_unicode_text_unchanged_and_passes_float_samples():
    client, fake = ready_client()
    response = client.post("/transcribe?lang=or", content=make_wav(1.0))
    assert response.status_code == 200
    assert response.json() == {"text": ODIA}
    shape, dtype, lang = fake.calls[0]
    assert shape == (16000,)
    assert dtype == np.float32
    assert lang == "or"


def test_unsupported_language_is_rejected_without_calling_the_model():
    client, fake = ready_client()
    assert client.post("/transcribe?lang=en", content=make_wav()).status_code == 400
    assert client.post("/transcribe?lang=zz", content=make_wav()).status_code == 400
    assert fake.calls == []


def test_bad_audio_is_rejected_with_a_clear_message():
    client, fake = ready_client()
    assert "WAV" in client.post("/transcribe?lang=or", content=b"x" * 50_000).json()["detail"]
    assert "16 kHz" in client.post("/transcribe?lang=or", content=make_wav(rate=44100)).json()["detail"]
    assert "16 kHz" in client.post("/transcribe?lang=or", content=make_wav(channels=2)).json()["detail"]
    assert "short" in client.post("/transcribe?lang=or", content=make_wav(seconds=0.05)).json()["detail"]
    assert fake.calls == []


def test_oversized_audio_is_rejected(monkeypatch):
    monkeypatch.setattr(service, "MAX_AUDIO_BYTES", 40_000)
    client, _ = ready_client()
    assert "long" in client.post("/transcribe?lang=or", content=make_wav(2.0)).json()["detail"]


def test_a_model_exception_is_a_generic_500_without_internals():
    class Exploding:
        def transcribe(self, samples, lang):
            raise RuntimeError("internal detail /home/user/model.onnx")

    client, _ = ready_client(Exploding())
    response = client.post("/transcribe?lang=or", content=make_wav())
    assert response.status_code == 500
    assert "internal detail" not in response.text


def test_missing_lang_is_422():
    client, _ = ready_client()
    assert client.post("/transcribe", content=make_wav()).status_code == 422


def test_decode_wav_scales_samples_to_unit_range():
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(16000)
        wav.writeframes(struct.pack("<h", 16384) * 8000 + struct.pack("<h", -32768) * 8000)
    samples = service.decode_wav(buffer.getvalue())
    assert samples.dtype == np.float32
    assert samples[0] == pytest.approx(0.5)
    assert samples[-1] == pytest.approx(-1.0)


def test_supported_languages_match_the_spring_side():
    # Kept in step with SpeechLanguages.SUPPORTED in the Java code (also 22 codes).
    expected = set("as bn brx doi gu hi kn kok ks mai ml mni mr ne or pa sa sat sd ta te ur".split())
    assert set(service.SUPPORTED_LANGUAGES) == expected


def test_docs_endpoints_are_not_exposed():
    client, _ = ready_client()
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404
