package com.ibm.rnd.alexa.exception;

/** The file extension is not on the allow-list, or the content does not match what the extension claims. */
public class UnsupportedDocumentTypeException extends InvalidDocumentException {

    public UnsupportedDocumentTypeException(String message) {
        super(message);
    }
}
