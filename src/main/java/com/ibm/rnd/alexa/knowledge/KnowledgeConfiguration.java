package com.ibm.rnd.alexa.knowledge;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Wires the knowledge service. Only active with {@code assistant.knowledge.enabled=true} (the
 * {@code knowledge} profile), because it needs PostgreSQL and the embedding model; without it the
 * Alexa simulator and chat run exactly as before.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KnowledgeProperties.class)
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "enabled", havingValue = "true")
public class KnowledgeConfiguration {

    @Bean
    DocumentRepository documentRepository(JdbcClient jdbc, PlatformTransactionManager transactionManager) {
        return new JdbcDocumentRepository(jdbc, new TransactionTemplate(transactionManager));
    }

    /**
     * Bounded on both axes (few threads, short queue) and separate from the request threads, so a burst
     * of uploads cannot starve chat. When the queue is full, submissions are rejected (HTTP 429).
     */
    @Bean(destroyMethod = "shutdown")
    ExecutorService ingestionExecutor(KnowledgeProperties properties, MeterRegistry registry) {
        AtomicInteger counter = new AtomicInteger();
        ArrayBlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(properties.ingestionQueueSize());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(properties.ingestionThreads(), properties.ingestionThreads(),
                0L, TimeUnit.MILLISECONDS, queue, runnable -> {
                    Thread thread = new Thread(runnable, "ingestion-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        registry.gauge("assistant.knowledge.queue.size", queue, ArrayBlockingQueue::size);
        return executor;
    }

    @Bean
    DocumentExtractor documentExtractor(KnowledgeProperties properties) {
        return new DocumentExtractor(properties.maxExtractedChars());
    }

    @Bean
    DocumentIndexer documentIndexer(DocumentExtractor extractor, EmbeddingModel embeddingModel,
            DocumentRepository repository, KnowledgeProperties properties, MeterRegistry registry) {
        return new DocumentIndexer(extractor, new TextNormalizer(), new LanguageDetector(),
                new TextChunker(properties.chunkTokens(), properties.chunkOverlapTokens()),
                embeddingModel, repository, properties, registry);
    }

    @Bean
    DocumentIngestionService documentIngestionService(DocumentRepository repository, DocumentIndexer indexer,
            DocumentExtractor extractor, ExecutorService ingestionExecutor, KnowledgeProperties properties,
            MeterRegistry registry) {
        return new DocumentIngestionService(repository, indexer, extractor, ingestionExecutor, properties, registry);
    }
}
