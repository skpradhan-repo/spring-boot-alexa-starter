package com.ibm.rnd.alexa.knowledge;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

import com.ibm.rnd.alexa.exception.DocumentNotFoundException;
import com.ibm.rnd.alexa.exception.IngestionBusyException;
import com.ibm.rnd.alexa.exception.InvalidDocumentException;
import com.ibm.rnd.alexa.exception.UnsupportedDocumentTypeException;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Entry point of ingestion. Validates the upload, detects duplicates by checksum, records a PENDING
 * document and hands the heavy work to a bounded executor so the HTTP request returns immediately.
 */
public class DocumentIngestionService {

    static final Pattern ACCESS_GROUP = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    static final int MAX_NAME_LENGTH = 255;

    /** @param duplicate true when identical content was already uploaded; no new work was queued */
    public record Submission(StoredDocument document, boolean duplicate) {
    }

    private final DocumentRepository repository;
    private final DocumentIndexer indexer;
    private final DocumentExtractor extractor;
    private final ExecutorService executor;
    private final KnowledgeProperties properties;
    private final MeterRegistry registry;

    DocumentIngestionService(DocumentRepository repository, DocumentIndexer indexer, DocumentExtractor extractor,
            ExecutorService executor, KnowledgeProperties properties, MeterRegistry registry) {
        this.repository = repository;
        this.indexer = indexer;
        this.extractor = extractor;
        this.executor = executor;
        this.properties = properties;
        this.registry = registry;
    }

    public Submission submit(String originalFileName, byte[] content, String accessGroup) {
        String group = validateAccessGroup(accessGroup);
        String name = sanitizeFileName(originalFileName);
        validateContent(name, content);

        String checksum = sha256(content);
        var existing = repository.findByChecksum(group, checksum);
        if (existing.isPresent()) {
            count("duplicate");
            return new Submission(existing.get(), true);
        }

        StoredDocument pending = repository.createPending(name, checksum, group);
        try {
            executor.execute(() -> indexer.index(pending.id(), content));
        } catch (RejectedExecutionException e) {
            repository.delete(pending.id());
            count("rejected");
            throw new IngestionBusyException();
        }
        count("accepted");
        return new Submission(pending, false);
    }

    public StoredDocument status(UUID id, String accessGroup) {
        return repository.findById(id, validateAccessGroup(accessGroup)).orElseThrow(DocumentNotFoundException::new);
    }

    static String validateAccessGroup(String accessGroup) {
        String group = accessGroup == null ? "" : accessGroup.strip().toLowerCase(java.util.Locale.ROOT);
        if (!ACCESS_GROUP.matcher(group).matches()) {
            throw new InvalidDocumentException("access_group must be 1-64 characters: a-z, 0-9, '-' or '_'");
        }
        return group;
    }

    /** Keeps only the last path segment and drops control characters, so the name is safe to store and show. */
    static String sanitizeFileName(String original) {
        if (!StringUtils.hasText(original)) {
            throw new InvalidDocumentException("A file name is required");
        }
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        name = name.replaceAll("\\p{Cc}", "").strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new InvalidDocumentException("File name must be 1-" + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    private void validateContent(String name, byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidDocumentException("The file is empty");
        }
        if (content.length > properties.maxUploadSize().toBytes()) {
            throw new InvalidDocumentException("The file exceeds the " + properties.maxUploadSize().toMegabytes() + " MB limit");
        }
        DocumentType claimed = DocumentType.fromFileName(name).orElseThrow(() ->
                new UnsupportedDocumentTypeException("Supported file types: pdf, docx, txt, html, md"));
        if (!claimed.accepts(extractor.detectMediaType(content))) {
            registry.counter("assistant.knowledge.ingestion", "outcome", "type_mismatch").increment();
            throw new UnsupportedDocumentTypeException("The file content does not match its extension");
        }
    }

    private void count(String outcome) {
        registry.counter("assistant.knowledge.ingestion", "outcome", outcome).increment();
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
