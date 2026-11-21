package com.ibm.rnd.alexa.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the real Flyway migration against a real PostgreSQL + pgvector (no mocks) and exercises the
 * indexes the hybrid retrieval will use. Skipped automatically when no container runtime is reachable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles({"local", "knowledge"})
class KnowledgeSchemaTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("docker.io/pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcClient jdbc;

    private static String vector(float first) {
        float[] v = new float[1024];
        v[0] = first;
        v[1] = 1f;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.length; i++) {
            sb.append(i == 0 ? "" : ",").append(v[i]);
        }
        return sb.append(']').toString();
    }

    @Test
    void migrationCreatesSchemaAndHybridQueriesWork() {
        assertThat(jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success").query(Long.class).single())
                .isGreaterThanOrEqualTo(1L);

        var docId = jdbc.sql("INSERT INTO documents (name, checksum, access_group, language) "
                        + "VALUES ('Policy.pdf', repeat('a', 64), 'hr', 'en') RETURNING id")
                .query(java.util.UUID.class).single();
        jdbc.sql("INSERT INTO document_chunks (document_id, chunk_index, content, embedding) "
                        + "VALUES (:d, 0, 'Policy HR-POL-2026-01 grants 20 days of annual leave', CAST(:e AS vector))")
                .param("d", docId).param("e", vector(0.9f)).update();
        jdbc.sql("INSERT INTO document_chunks (document_id, chunk_index, content, embedding) "
                        + "VALUES (:d, 1, 'Office parking rules', CAST(:e AS vector))")
                .param("d", docId).param("e", vector(-0.9f)).update();

        // Vector search (cosine distance): the chunk closest to the query vector comes first.
        List<Map<String, Object>> byVector = jdbc.sql("SELECT chunk_index FROM document_chunks "
                        + "ORDER BY embedding <=> CAST(:q AS vector) LIMIT 2")
                .param("q", vector(0.8f)).query().listOfRows();
        assertThat(byVector.get(0).get("chunk_index")).isEqualTo(0);

        // Keyword search finds an exact policy id that embeddings often blur.
        Long keywordHits = jdbc.sql("SELECT count(*) FROM document_chunks "
                        + "WHERE tsv @@ plainto_tsquery('simple', 'HR-POL-2026-01')")
                .query(Long.class).single();
        assertThat(keywordHits).isEqualTo(1L);
    }

    @Test
    void constraintsProtectDataIntegrity() {
        jdbc.sql("INSERT INTO documents (name, checksum) VALUES ('Dup.pdf', repeat('b', 64))").update();

        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.sql("INSERT INTO documents (name, checksum) VALUES ('Dup.pdf', repeat('c', 64))").update(),
                "same name and version must be unique");
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.sql("INSERT INTO documents (name, checksum, status) VALUES ('X', repeat('d', 64), 'BOGUS')").update(),
                "status is constrained");
    }
}
