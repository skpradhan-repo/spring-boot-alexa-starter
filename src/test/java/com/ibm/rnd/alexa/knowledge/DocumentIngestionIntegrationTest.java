package com.ibm.rnd.alexa.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Whole ingestion path against real PostgreSQL + pgvector and real Tika: upload bytes, background
 * indexing, chunks and vectors stored, access control in SQL. Only the embedding model is replaced
 * (a deterministic fake), so the test needs neither Ollama nor network access.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles({"local", "knowledge"})
@Import(DocumentIngestionIntegrationTest.FakeEmbeddings.class)
class DocumentIngestionIntegrationTest {

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
        registry.add("spring.ai.model.embedding", () -> "none");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeEmbeddings {
        @Bean
        @Primary
        org.springframework.ai.embedding.EmbeddingModel embeddingModel() {
            return new FakeEmbeddingModel(1024);
        }
    }

    @Autowired
    private DocumentIngestionService ingestion;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private DocumentRepository repository;

    private StoredDocument await(UUID id, String group) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            StoredDocument d = ingestion.status(id, group);
            if (d.status() != DocumentStatus.PENDING) {
                return d;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("document still PENDING: " + id);
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void textDocumentIsChunkedEmbeddedAndStored() throws Exception {
        String paragraph = "Employees receive twenty days of annual leave. Leave must be approved by the manager. ";
        var submission = ingestion.submit("handbook.txt", utf8(paragraph.repeat(60)), "it-docs");

        StoredDocument indexed = await(submission.document().id(), "it-docs");

        assertThat(indexed.status()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(indexed.chunkCount()).isGreaterThan(1);
        Map<String, Object> row = jdbc.sql("SELECT count(*) AS n, count(embedding) AS embedded, min(chunk_index) AS first, "
                        + "max(chunk_index) AS last, max(vector_dims(embedding)) AS dims FROM document_chunks WHERE document_id = :id")
                .param("id", indexed.id()).query().singleRow();
        assertThat(((Number) row.get("n")).intValue()).isEqualTo(indexed.chunkCount());
        assertThat(row.get("embedded")).isEqualTo(row.get("n"));
        assertThat(row.get("first")).isEqualTo(0);
        assertThat(row.get("dims")).isEqualTo(1024);
    }

    @Test
    void realPdfIsExtractedByTika() throws Exception {
        var submission = ingestion.submit("policy.pdf", MinimalPdf.withText("Expense claims need a receipt HR-POL-2026-07"), "pdf-docs");

        StoredDocument indexed = await(submission.document().id(), "pdf-docs");

        assertThat(indexed.status()).as("failure: %s", indexed.failureReason()).isEqualTo(DocumentStatus.INDEXED);
        Long hits = jdbc.sql("SELECT count(*) FROM document_chunks WHERE document_id = :id "
                        + "AND tsv @@ plainto_tsquery('simple', 'HR-POL-2026-07')")
                .param("id", indexed.id()).query(Long.class).single();
        assertThat(hits).isEqualTo(1L);
    }

    @Test
    void hindiAndOdiaTextSurviveTheRoundTrip() throws Exception {
        String hindi = "\u0915\u0930\u094D\u092E\u091A\u093E\u0930\u093F\u092F\u094B\u0902 \u0915\u094B \u092C\u0940\u0938 \u0926\u093F\u0928 \u0915\u093E \u0935\u093E\u0930\u094D\u0937\u093F\u0915 \u0905\u0935\u0915\u093E\u0936 \u092E\u093F\u0932\u0924\u093E \u0939\u0948\u0964";
        String odia = "\u0B15\u0B30\u0B4D\u0B2E\u0B1A\u0B3E\u0B30\u0B40\u0B59\u0B4D\u0B15\u0B41 \u0B2C\u0B3E\u0B30\u0B4D\u0B37\u0B3F\u0B15 \u0B1B\u0B41\u0B1F\u0B3F \u0B2E\u0B3F\u0B33\u0B47\u0964";

        var h = ingestion.submit("hindi.txt", utf8(hindi), "lang");
        var o = ingestion.submit("odia.txt", utf8(odia), "lang");

        assertThat(await(h.document().id(), "lang").language()).isEqualTo("hi");
        assertThat(await(o.document().id(), "lang").language()).isEqualTo("or");
        String storedHindi = jdbc.sql("SELECT content FROM document_chunks WHERE document_id = :id")
                .param("id", h.document().id()).query(String.class).single();
        assertThat(storedHindi).isEqualTo(hindi);
    }

    @Test
    void corruptPdfFailsWithAGenericReasonAndStoresNothing() throws Exception {
        // Real PDF header, garbage body: passes the content-type check, then Tika cannot parse it.
        byte[] corrupt = utf8("%PDF-1.7\n" + "\u0001\u0002 garbage stream without objects or xref ".repeat(20));
        var submission = ingestion.submit("broken.pdf", corrupt, "bad-docs");

        StoredDocument done = await(submission.document().id(), "bad-docs");

        assertThat(done.status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(done.failureReason()).isIn("EXTRACTION_FAILED", "EMPTY_DOCUMENT");
        assertThat(done.chunkCount()).isZero();
    }

    @Test
    void accessGroupIsEnforcedInSql() throws Exception {
        var submission = ingestion.submit("salary.txt", utf8("Salary bands are confidential."), "finance");
        await(submission.document().id(), "finance");

        List<Map<String, Object>> visibleToOthers = jdbc.sql(
                        "SELECT d.id FROM documents d WHERE d.id = :id AND d.access_group = :group")
                .param("id", submission.document().id()).param("group", "interns").query().listOfRows();

        assertThat(visibleToOthers).isEmpty();
    }

    @Test
    void duplicateUploadDoesNotCreateASecondRowOrReEmbed() throws Exception {
        var first = ingestion.submit("dup.txt", utf8("Exactly the same bytes."), "dups");
        await(first.document().id(), "dups");

        var second = ingestion.submit("dup-copy.txt", utf8("Exactly the same bytes."), "dups");

        assertThat(second.duplicate()).isTrue();
        assertThat(second.document().id()).isEqualTo(first.document().id());
        assertThat(jdbc.sql("SELECT count(*) FROM documents WHERE access_group = 'dups'").query(Long.class).single()).isEqualTo(1L);
    }

    @Test
    void versionsIncrementPerGroupAndNameUnderConcurrentUploads() throws Exception {
        List<Integer> versions = java.util.stream.IntStream.range(0, 6).parallel()
                .mapToObj(i -> ingestion.submit("same-name.txt", utf8("content number " + i), "race").document().version())
                .toList();

        assertThat(versions).doesNotHaveDuplicates().containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
    }

    @Test
    void similaritySearchRanksByCosineAndRespectsGroupStatusAndLatestVersion() throws Exception {
        // FakeEmbeddingModel puts the text length on axis 0, so use chunk vectors directly for a clear geometry.
        String group = "search-" + UUID.randomUUID().toString().substring(0, 8);
        UUID leave = ingestion.submit("leave.txt", utf8("leave"), group).document().id();
        await(leave, group);
        UUID other = ingestion.submit("other.txt", utf8("other"), group).document().id();
        await(other, group);
        UUID foreign = ingestion.submit("leave.txt", utf8("leave"), group + "-x").document().id();
        await(foreign, group + "-x");

        float[] towardsAxis0 = new float[1024];
        towardsAxis0[0] = 1;
        float[] towardsAxis1 = new float[1024];
        towardsAxis1[1] = 1;
        jdbc.sql("UPDATE document_chunks SET embedding = CAST(:v AS vector) WHERE document_id = :id")
                .param("v", JdbcDocumentRepository.toVectorLiteral(towardsAxis0)).param("id", leave).update();
        jdbc.sql("UPDATE document_chunks SET embedding = CAST(:v AS vector) WHERE document_id = :id")
                .param("v", JdbcDocumentRepository.toVectorLiteral(towardsAxis1)).param("id", other).update();

        List<RetrievedChunk> hits = repository.searchSimilar(group, towardsAxis0, 5);

        assertThat(hits).extracting(RetrievedChunk::documentName).containsExactly("leave.txt", "other.txt");
        assertThat(hits.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(hits.get(1).score()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));

        // A newer version replaces the older one in search results.
        UUID v2 = ingestion.submit("leave.txt", utf8("leave, revised"), group).document().id();
        await(v2, group);
        jdbc.sql("UPDATE document_chunks SET embedding = CAST(:v AS vector) WHERE document_id = :id")
                .param("v", JdbcDocumentRepository.toVectorLiteral(towardsAxis0)).param("id", v2).update();
        List<RetrievedChunk> afterRevision = repository.searchSimilar(group, towardsAxis0, 5);
        assertThat(afterRevision).filteredOn(c -> c.documentName().equals("leave.txt"))
                .extracting(RetrievedChunk::version).containsExactly(2);

        assertThat(repository.searchSimilar("no-such-group", towardsAxis0, 5)).isEmpty();
    }

    @Test
    void sameNameInDifferentGroupsStartsAtVersionOneEach() {
        int a = ingestion.submit("shared.txt", utf8("group a"), "grp-a").document().version();
        int b = ingestion.submit("shared.txt", utf8("group b"), "grp-b").document().version();

        assertThat(a).isEqualTo(1);
        assertThat(b).isEqualTo(1);
    }
}
