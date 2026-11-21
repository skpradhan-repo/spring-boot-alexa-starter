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
    public List<RetrievedChunk> searchSimilar(String accessGroup, float[] queryVector, int limit) {
        List<RetrievedChunk> hits = new ArrayList<>();
        for (StoredDocument d : documents.values()) {
            boolean latest = documents.values().stream().noneMatch(o -> o.accessGroup().equals(d.accessGroup())
                    && o.name().equals(d.name()) && o.status() == DocumentStatus.INDEXED && o.version() > d.version());
            if (!d.accessGroup().equals(accessGroup) || d.status() != DocumentStatus.INDEXED || !latest) {
                continue;
            }
            for (EmbeddedChunk c : chunks.getOrDefault(d.id(), List.of())) {
                hits.add(new RetrievedChunk(d.name(), d.version(), c.chunk().index(), c.chunk().text(),
                        cosine(queryVector, c.vector())));
            }
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.stream().limit(limit).toList();
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : dot / Math.sqrt(na * nb);
    }

    @Override
    public void delete(UUID id) {
        documents.remove(id);
        checksums.remove(id);
        chunks.remove(id);
    }
}
