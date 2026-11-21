package com.ibm.rnd.alexa.knowledge;

/** Lifecycle of an uploaded document. Only {@link #INDEXED} documents are searchable. */
public enum DocumentStatus {
    PENDING, INDEXED, FAILED
}
