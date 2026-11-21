package com.ibm.rnd.alexa.exception;

/** The local speech recognition service is not running, not ready, or failed. Details stay in the server log. */
public class SpeechUnavailableException extends RuntimeException {

    public SpeechUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public SpeechUnavailableException(String message) {
        super(message);
    }
}
