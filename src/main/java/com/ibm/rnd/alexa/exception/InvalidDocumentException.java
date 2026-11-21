package com.ibm.rnd.alexa.exception;

/**
 * An uploaded document or its parameters failed validation. The message is written by our own code
 * (never derived from file content) so it is safe to return to the client.
 */
public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException(String message) {
        super(message);
    }
}
