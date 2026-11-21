package com.ibm.rnd.alexa.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Turns uploaded bytes into searchable chunks: extract, normalize, detect language, chunk, embed, store.
 * Runs on the ingestion executor, never on a request thread. A failure at any step leaves the document
 * FAILED with a coarse reason; document text is never written to logs.
 */
class DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexer.class);

    private final DocumentExtractor extractor;
    private final TextNormalizer normalizer;
    private final LanguageDetector languageDetector;
    private final TextChunker chunker;
    private final EmbeddingModel embeddingModel;
    private final DocumentRepository repository;
    private final KnowledgeProperties properties;
    private final MeterRegistry registry;

    DocumentIndexer(DocumentExtractor extractor, TextNormalizer normalizer, LanguageDetector languageDetector,
            TextChunker chunker, EmbeddingModel embeddingModel, DocumentRepository repository,
            KnowledgeProperties properties, MeterRegistry registry) {
        this.extractor = extractor;
        this.normalizer = normalizer;
        this.languageDetector = languageDetector;
        this.chunker = chunker;
        this.embeddingModel = embeddingModel;
        this.repository = repository;
        this.properties = properties;
        this.registry = registry;
    }

    void index(UUID documentId, byte[] content) {
        Timer.Sample sample = Timer.start(registry);
        FailureReason failure = null;
        try {
            indexOrThrow(documentId, content);
        } catch (IndexingException e) {
            failure = e.reason;
            log.warn("Indexing failed: document={} reason={}", documentId, e.reason, e.getCause());
            markFailed(documentId, e.reason);
        } catch (RuntimeException e) {
            failure = FailureReason.STORAGE_FAILED;
            log.error("Indexing failed unexpectedly: document={}", documentId, e);
            markFailed(documentId, FailureReason.STORAGE_FAILED);
        } finally {
            sample.stop(registry.timer("assistant.knowledge.indexing", "outcome", failure == null ? "indexed" : "failed"));
        }
    }

    private void indexOrThrow(UUID documentId, byte[] content) {
        String text;
        try {
            text = normalizer.normalize(extractor.extractText(content));
        } catch (DocumentExtractor.ExtractionException e) {
            throw new IndexingException(FailureReason.EXTRACTION_FAILED, e);
        }
        List<TextChunk> chunks = chunker.chunk(text);
        if (chunks.isEmpty()) {
            throw new IndexingException(FailureReason.EMPTY_DOCUMENT, null);
        }
        List<EmbeddedChunk> embedded = embed(chunks);
        try {
            repository.completeIndexing(documentId, languageDetector.detect(text), embedded);
        } catch (RuntimeException e) {
            throw new IndexingException(FailureReason.STORAGE_FAILED, e);
        }
        log.info("Indexed document={} chunks={}", documentId, embedded.size());
    }

    private List<EmbeddedChunk> embed(List<TextChunk> chunks) {
        List<EmbeddedChunk> result = new ArrayList<>(chunks.size());
        int batchSize = properties.embeddingBatchSize();
        for (int from = 0; from < chunks.size(); from += batchSize) {
            List<TextChunk> batch = chunks.subList(from, Math.min(from + batchSize, chunks.size()));
            List<float[]> vectors;
            try {
                vectors = embeddingModel.embed(batch.stream().map(TextChunk::text).toList());
            } catch (RuntimeException e) {
                throw new IndexingException(FailureReason.EMBEDDING_UNAVAILABLE, e);
            }
            if (vectors == null || vectors.size() != batch.size()) {
                throw new IndexingException(FailureReason.EMBEDDING_MISMATCH, null);
            }
            for (int i = 0; i < batch.size(); i++) {
                if (vectors.get(i).length != properties.embeddingDimensions()) {
                    throw new IndexingException(FailureReason.EMBEDDING_MISMATCH, null);
                }
                result.add(new EmbeddedChunk(batch.get(i), vectors.get(i)));
            }
        }
        return result;
    }

    private void markFailed(UUID documentId, FailureReason reason) {
        try {
            repository.markFailed(documentId, reason.name());
        } catch (RuntimeException e) {
            log.error("Could not record failure: document={}", documentId, e);
        }
    }

    private static final class IndexingException extends RuntimeException {
        private final transient FailureReason reason;

        IndexingException(FailureReason reason, Throwable cause) {
            super(reason.name(), cause);
            this.reason = reason;
        }
    }
}
