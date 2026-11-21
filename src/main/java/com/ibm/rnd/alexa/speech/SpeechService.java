package com.ibm.rnd.alexa.speech;

import java.util.concurrent.Semaphore;

import com.ibm.rnd.alexa.exception.InvalidAudioException;
import com.ibm.rnd.alexa.exception.SpeechUnavailableException;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Validates a recording and its language, then hands it to the recogniser. Only one transcription runs at a
 * time: the model uses all CPU cores, and a second concurrent request would slow both down. A request that
 * finds the recogniser busy fails fast instead of queueing behind a long clip.
 */
class SpeechService {

    private final SpeechRecognizer recognizer;
    private final SpeechProperties properties;
    private final MeterRegistry registry;
    private final Semaphore slot = new Semaphore(1);

    SpeechService(SpeechRecognizer recognizer, SpeechProperties properties, MeterRegistry registry) {
        this.recognizer = recognizer;
        this.properties = properties;
        this.registry = registry;
    }

    SpeechRecognizer.Status status() {
        return recognizer.status();
    }

    String transcribe(byte[] wav, String lang) {
        if (!SpeechLanguages.isSupported(lang)) {
            throw new InvalidAudioException("The local engine does not support this language");
        }
        WavValidator.validate(wav, properties.maxAudioBytes());

        if (!slot.tryAcquire()) {
            registry.counter("assistant.speech.transcriptions", "outcome", "busy").increment();
            throw new SpeechUnavailableException("Another recording is being processed");
        }
        try {
            String text = recognizer.transcribe(wav, lang);
            registry.counter("assistant.speech.transcriptions", "outcome", text.isBlank() ? "empty" : "ok").increment();
            return text;
        } catch (SpeechUnavailableException e) {
            registry.counter("assistant.speech.transcriptions", "outcome", "unavailable").increment();
            throw e;
        } finally {
            slot.release();
        }
    }
}
