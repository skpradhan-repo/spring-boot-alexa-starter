package com.ibm.rnd.alexa.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory {@link DocumentRepository} for unit tests: same visibility and versioning rules, no database. */
class InMemoryDocumentRepository implements DocumentRepository {

    final Map<UUID, StoredDocument> documents = new LinkedHashMap<>();
    final Map<UUID, String> checksums = new LinkedHashMap<>();
    final Map<UUID, List<EmbeddedChunk>> chunks = new LinkedHashMap<>();

    @Override
    public Optional<StoredDocument> findByChecksum(String accessGroup, String checksum) {
        return documents.values().stream()
                .filter(d -> d.accessGroup().equals(accessGroup) && checksums.get(d.id()).equals(checksum)
                        && d.status() != DocumentStatus.FAILED)
                .findFirst();
    }

    @Override
    public Optional<StoredDocument> findById(UUID id, String accessGroup) {
        return Optional.ofNullable(documents.get(id)).filter(d -> d.accessGroup().equals(accessGroup));
    }

    @Override
    public StoredDocument createPending(String name, String checksum, String accessGroup) {
        int version = documents.values().stream()
                .filter(d -> d.accessGroup().equals(accessGroup) && d.name().equals(name))
                .mapToInt(StoredDocument::version).max().orElse(0) + 1;
        UUID id = UUID.randomUUID();
        StoredDocument doc = new StoredDocument(id, name, version, accessGroup, null, DocumentStatus.PENDING, null, 0, Instant.now());
        documents.put(id, doc);
        checksums.put(id, checksum);
        return doc;
    }

    @Override
    public void completeIndexing(UUID id, String language, List<EmbeddedChunk> embedded) {
        StoredDocument d = documents.get(id);
        chunks.put(id, new ArrayList<>(embedded));
        documents.put(id, new StoredDocument(id, d.name(), d.version(), d.accessGroup(), language,
                DocumentStatus.INDEXED, null, embedded.size(), d.createdAt()));
    }

    @Override
    public void markFailed(UUID id, String failureReason) {
        StoredDocument d = documents.get(id);
        documents.put(id, new StoredDocument(id, d.name(), d.version(), d.accessGroup(), d.language(),
                DocumentStatus.FAILED, failureReason, 0, d.createdAt()));
    }

    @Override
    public void delete(UUID id) {
        documents.remove(id);
        checksums.remove(id);
        chunks.remove(id);
    }
}
