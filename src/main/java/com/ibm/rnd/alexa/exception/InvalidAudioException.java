package com.ibm.rnd.alexa.exception;

/** The uploaded recording is unusable (empty, wrong format, too long). The message is ours and safe to show. */
public class InvalidAudioException extends RuntimeException {

    public InvalidAudioException(String message) {
        super(message);
    }
}
