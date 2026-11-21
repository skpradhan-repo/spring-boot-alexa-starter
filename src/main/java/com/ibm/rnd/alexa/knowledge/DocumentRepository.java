package com.ibm.rnd.alexa.knowledge;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port of the knowledge service. Every lookup that serves a caller takes the caller's
 * access group, so authorization is applied in SQL and never left to the calling code.
 */
interface DocumentRepository {

    /** A previously uploaded, non-failed document with identical bytes in this access group. */
    Optional<StoredDocument> findByChecksum(String accessGroup, String checksum);

    /** Visible only when the document belongs to {@code accessGroup}. */
    Optional<StoredDocument> findById(UUID id, String accessGroup);

    /** Inserts a PENDING row, numbering it as the next version of {@code name} within the group. */
    StoredDocument createPending(String name, String checksum, String accessGroup);

    /** Atomically replaces the document's chunks and marks it INDEXED. */
    void completeIndexing(UUID id, String language, List<EmbeddedChunk> chunks);

    void markFailed(UUID id, String failureReason);

    /** Removes a document that never started processing (for example the queue rejected it). */
    void delete(UUID id);
}
