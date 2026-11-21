package com.ibm.rnd.alexa.exception;

/**
 * No document with this id is visible to the caller. Deliberately also used for "exists but belongs to
 * another access group" so the API does not reveal which documents exist.
 */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException() {
        super("Document not found");
    }
}
