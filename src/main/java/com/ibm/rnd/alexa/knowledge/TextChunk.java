package com.ibm.rnd.alexa.knowledge;

/** One retrievable passage of a document. {@code index} is its position within the document. */
record TextChunk(int index, String text) {
}
