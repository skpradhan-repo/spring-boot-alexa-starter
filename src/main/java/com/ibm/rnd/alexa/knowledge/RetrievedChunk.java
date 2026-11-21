package com.ibm.rnd.alexa.knowledge;

/**
 * A chunk returned by similarity search, with where it came from.
 *
 * @param score cosine similarity in [-1, 1]; higher is closer to the question
 */
public record RetrievedChunk(String documentName, int version, int chunkIndex, String content, double score) {
}
