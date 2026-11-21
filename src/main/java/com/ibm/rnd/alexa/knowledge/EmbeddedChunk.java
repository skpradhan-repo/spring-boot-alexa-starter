package com.ibm.rnd.alexa.knowledge;

/** A chunk together with its embedding vector, ready to be stored. */
record EmbeddedChunk(TextChunk chunk, float[] vector) {
}
