package com.ibm.rnd.alexa.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Knowledge ingestion settings (prefix {@code assistant.knowledge}).
 *
 * @param enabled             creates the knowledge beans; set by the {@code knowledge} profile (needs the database)
 * @param maxUploadSize       largest accepted upload; keep {@code spring.servlet.multipart.max-file-size} in step
 * @param chunkTokens         target upper bound of one chunk, in estimated tokens
 * @param chunkOverlapTokens  tokens repeated at the start of the next chunk so facts that straddle a boundary survive
 * @param embeddingBatchSize  chunks sent to the embedding model per call
 * @param embeddingDimensions size of the vectors the schema stores (bge-m3 = 1024); a mismatch fails indexing
 * @param maxExtractedChars   upper bound on text extracted from one file (guards against decompression bombs)
 * @param ingestionThreads    parallel indexing jobs (kept low: embedding competes with chat for the CPU)
 * @param ingestionQueueSize  uploads waiting for a worker; further uploads are rejected with 429
 * @param retrievalTopK       most similar chunks handed to the model when answering a question
 * @param retrievalMinScore   cosine similarity below which a chunk is treated as unrelated (no model call if none pass)
 * @param retrievalContextChars upper bound on source text sent to the model; small on purpose, a CPU model has a short context
 */
@Validated
@ConfigurationProperties(prefix = "assistant.knowledge")
public record KnowledgeProperties(
        @DefaultValue("false") boolean enabled,
        @NotNull @DefaultValue("20MB") DataSize maxUploadSize,
        @Min(100) @Max(2000) @DefaultValue("500") int chunkTokens,
        @Min(0) @DefaultValue("60") int chunkOverlapTokens,
        @Min(1) @Max(64) @DefaultValue("16") int embeddingBatchSize,
        @Min(1) @DefaultValue("1024") int embeddingDimensions,
        @Min(1000) @DefaultValue("2000000") int maxExtractedChars,
        @Min(1) @Max(4) @DefaultValue("1") int ingestionThreads,
        @Min(1) @Max(100) @DefaultValue("10") int ingestionQueueSize,
        @Min(1) @Max(10) @DefaultValue("3") int retrievalTopK,
        @DecimalMin("0.0") @DecimalMax("1.0") @DefaultValue("0.4") double retrievalMinScore,
        @Min(500) @DefaultValue("3000") int retrievalContextChars) {

    public KnowledgeProperties {
        if (chunkOverlapTokens >= chunkTokens) {
            throw new IllegalArgumentException("assistant.knowledge.chunk-overlap-tokens must be smaller than chunk-tokens");
        }
    }
}
