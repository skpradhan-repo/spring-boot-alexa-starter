package com.ibm.rnd.alexa.exception;

/** Thrown when the request body is not a structurally valid Alexa {@code RequestEnvelope}. */
public class InvalidAlexaRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidAlexaRequestException(String message) {
        super(message);
    }

    public InvalidAlexaRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
