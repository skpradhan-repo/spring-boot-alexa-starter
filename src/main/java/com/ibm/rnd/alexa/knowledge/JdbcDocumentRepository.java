package com.ibm.rnd.alexa.knowledge;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL/pgvector implementation. All SQL is parameterized; vectors are bound as text and cast. */
class JdbcDocumentRepository implements DocumentRepository {

    private static final String SELECT_DOCUMENT = """
            SELECT d.id, d.name, d.version, d.access_group, d.language, d.status, d.failure_reason, d.created_at,
                   (SELECT count(*) FROM document_chunks c WHERE c.document_id = d.id) AS chunk_count
            FROM documents d
            """;

    private static final int VERSION_RACE_RETRIES = 3;

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    JdbcDocumentRepository(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    @Override
    public Optional<StoredDocument> findByChecksum(String accessGroup, String checksum) {
        return jdbc.sql(SELECT_DOCUMENT + " WHERE d.access_group = :group AND d.checksum = :checksum "
                        + "AND d.status <> 'FAILED' ORDER BY d.created_at DESC LIMIT 1")
                .param("group", accessGroup).param("checksum", checksum)
                .query((rs, row) -> map(rs)).optional();
    }

    @Override
    public Optional<StoredDocument> findById(UUID id, String accessGroup) {
        return jdbc.sql(SELECT_DOCUMENT + " WHERE d.id = :id AND d.access_group = :group")
                .param("id", id).param("group", accessGroup)
                .query((rs, row) -> map(rs)).optional();
    }

    @Override
    public StoredDocument createPending(String name, String checksum, String accessGroup) {
        for (int attempt = 1; ; attempt++) {
            try {
                UUID id = jdbc.sql("""
                                INSERT INTO documents (name, checksum, access_group, version)
                                SELECT :name, :checksum, :group, coalesce(max(version), 0) + 1
                                FROM documents WHERE access_group = :group AND name = :name
                                RETURNING id
                                """)
                        .param("name", name).param("checksum", checksum).param("group", accessGroup)
                        .query(UUID.class).single();
                return findById(id, accessGroup).orElseThrow();
            } catch (DuplicateKeyException e) {
                // Two uploads of the same name raced for the same version number; the loser retries.
                if (attempt >= VERSION_RACE_RETRIES) {
                    throw e;
                }
            }
        }
    }

    @Override
    public void completeIndexing(UUID id, String language, List<EmbeddedChunk> chunks) {
        transaction.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM document_chunks WHERE document_id = :id").param("id", id).update();
            for (EmbeddedChunk chunk : chunks) {
                jdbc.sql("INSERT INTO document_chunks (document_id, chunk_index, content, embedding) "
                                + "VALUES (:id, :idx, :content, CAST(:embedding AS vector))")
                        .param("id", id).param("idx", chunk.chunk().index())
                        .param("content", chunk.chunk().text()).param("embedding", toVectorLiteral(chunk.vector()))
                        .update();
            }
            jdbc.sql("UPDATE documents SET status = 'INDEXED', language = :language, failure_reason = NULL WHERE id = :id")
                    .param("language", language).param("id", id).update();
        });
    }

    @Override
    public void markFailed(UUID id, String failureReason) {
        jdbc.sql("UPDATE documents SET status = 'FAILED', failure_reason = :reason WHERE id = :id")
                .param("reason", failureReason).param("id", id).update();
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql("DELETE FROM documents WHERE id = :id").param("id", id).update();
    }

    private static StoredDocument map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoredDocument(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getInt("version"),
                rs.getString("access_group"),
                rs.getString("language"),
                DocumentStatus.valueOf(rs.getString("status")),
                rs.getString("failure_reason"),
                rs.getInt("chunk_count"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    /** pgvector text format, e.g. {@code [0.1,0.2]}. {@link Float#toString} is locale independent. */
    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
