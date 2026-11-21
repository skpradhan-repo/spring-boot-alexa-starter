package com.ibm.rnd.alexa.exception;

/** The ingestion queue is full; the client may retry the upload later. */
public class IngestionBusyException extends RuntimeException {

    public IngestionBusyException() {
        super("Ingestion queue is full");
    }
}
