package com.ibm.rnd.alexa.speech;

import java.util.List;

/**
 * Port to the local speech recognition process. The rest of the application depends on this interface only, so
 * the model behind it (today AI4Bharat IndicConformer in a Python service) can change without touching the API.
 */
interface SpeechRecognizer {

    /** @param state {@code ready}, {@code loading}, {@code model_missing} or {@code error} (as the service reports it) */
    record Status(String state, List<String> languages) {

        boolean ready() {
            return "ready".equals(state);
        }
    }

    /** Never throws: an unreachable service is reported as state {@code unreachable}. */
    Status status();

    /**
     * @param wav  validated 16 kHz mono 16-bit PCM WAV
     * @param lang model language code, already checked against {@link SpeechLanguages}
     * @return the recognised text in the language's own script
     * @throws com.ibm.rnd.alexa.exception.SpeechUnavailableException when the service cannot answer
     */
    String transcribe(byte[] wav, String lang);
}
