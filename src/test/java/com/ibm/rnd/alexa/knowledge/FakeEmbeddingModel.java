package com.ibm.rnd.alexa.knowledge;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/** Deterministic stand-in for the embedding model: vectors of a fixed size, or a configured failure. */
class FakeEmbeddingModel implements EmbeddingModel {

    private final int dimensions;
    private RuntimeException failure;

    FakeEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (failure != null) {
            throw failure;
        }
        List<Embedding> result = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            result.add(new Embedding(vector(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(result);
    }

    @Override
    public float[] embed(Document document) {
        return vector(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    private float[] vector(String text) {
        float[] v = new float[dimensions];
        v[0] = text == null ? 0 : text.length();
        return v;
    }
}
