package com.ibm.rnd.alexa.knowledge;

import java.time.Instant;
import java.util.UUID;

/** A document row plus its chunk count, as read back from the database. */
public record StoredDocument(
        UUID id,
        String name,
        int version,
        String accessGroup,
        String language,
        DocumentStatus status,
        String failureReason,
        int chunkCount,
        Instant createdAt) {
}
