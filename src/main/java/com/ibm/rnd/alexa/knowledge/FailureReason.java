package com.ibm.rnd.alexa.knowledge;

/** Coarse, client-safe reasons a document ends up FAILED. Detailed causes are logged server-side only. */
enum FailureReason {
    EXTRACTION_FAILED,
    EMPTY_DOCUMENT,
    EMBEDDING_UNAVAILABLE,
    EMBEDDING_MISMATCH,
    STORAGE_FAILED
}
