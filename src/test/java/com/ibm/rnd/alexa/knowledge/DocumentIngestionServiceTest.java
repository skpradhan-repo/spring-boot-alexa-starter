package com.ibm.rnd.alexa.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.util.unit.DataSize;

import com.ibm.rnd.alexa.exception.DocumentNotFoundException;
import com.ibm.rnd.alexa.exception.IngestionBusyException;
import com.ibm.rnd.alexa.exception.InvalidDocumentException;
import com.ibm.rnd.alexa.exception.UnsupportedDocumentTypeException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Upload validation, duplicate detection, versioning, authorization and queue limits (no database, no Ollama). */
class DocumentIngestionServiceTest {

    private static final int DIMENSIONS = 8;

    private final InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private ExecutorService executor;
    private DocumentIngestionService service;

    private static KnowledgeProperties properties(DataSize max) {
        return new KnowledgeProperties(true, max, 500, 60, 16, DIMENSIONS, 1_000_000, 1, 10, 3, 0.4, 3000);
    }

    private static EmbeddingModel fakeEmbeddings() {
        return new FakeEmbeddingModel(DIMENSIONS);
    }

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        service = build(executor, properties(DataSize.ofMegabytes(1)));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private DocumentIngestionService build(ExecutorService exec, KnowledgeProperties props) {
        DocumentExtractor extractor = new DocumentExtractor(props.maxExtractedChars());
        DocumentIndexer indexer = new DocumentIndexer(extractor, new TextNormalizer(), new LanguageDetector(),
                new TextChunker(props.chunkTokens(), props.chunkOverlapTokens()), fakeEmbeddings(), repository, props, registry);
        return new DocumentIngestionService(repository, indexer, extractor, exec, props, registry);
    }

    private static byte[] text(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private StoredDocument awaitStatus(UUID id, String group, DocumentStatus expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            StoredDocument d = service.status(id, group);
            if (d.status() == expected) {
                return d;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("document " + id + " did not reach " + expected);
    }

    @Test
    void acceptsTextAndIndexesItInTheBackground() throws Exception {
        var submission = service.submit("leave.txt", text("Annual leave is 20 days for every employee."), "hr");

        assertThat(submission.duplicate()).isFalse();
        assertThat(submission.document().status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(submission.document().version()).isEqualTo(1);

        StoredDocument indexed = awaitStatus(submission.document().id(), "hr", DocumentStatus.INDEXED);
        assertThat(indexed.chunkCount()).isEqualTo(1);
        assertThat(indexed.language()).isEqualTo("en");
    }

    @Test
    void indexesHindiAndOdiaContentWithTheRightLanguage() throws Exception {
        var hindi = service.submit("hindi.txt", text("\u0915\u0930\u094D\u092E\u091A\u093E\u0930\u093F\u092F\u094B\u0902 \u0915\u094B \u0935\u093E\u0930\u094D\u0937\u093F\u0915 \u0905\u0935\u0915\u093E\u0936 \u092E\u093F\u0932\u0924\u093E \u0939\u0948\u0964"), "hr");
        var odia = service.submit("odia.txt", text("\u0B15\u0B30\u0B4D\u0B2E\u0B1A\u0B3E\u0B30\u0B40\u0B59\u0B4D\u0B15\u0B41 \u0B2C\u0B3E\u0B30\u0B4D\u0B37\u0B3F\u0B15 \u0B1B\u0B41\u0B1F\u0B3F \u0B2E\u0B3F\u0B33\u0B47\u0964"), "hr");

        assertThat(awaitStatus(hindi.document().id(), "hr", DocumentStatus.INDEXED).language()).isEqualTo("hi");
        assertThat(awaitStatus(odia.document().id(), "hr", DocumentStatus.INDEXED).language()).isEqualTo("or");
    }

    @Test
    void identicalContentIsADuplicateAndQueuesNothing() throws Exception {
        var first = service.submit("a.txt", text("Same bytes"), "hr");
        awaitStatus(first.document().id(), "hr", DocumentStatus.INDEXED);

        var second = service.submit("renamed.txt", text("Same bytes"), "hr");

        assertThat(second.duplicate()).isTrue();
        assertThat(second.document().id()).isEqualTo(first.document().id());
        assertThat(repository.documents).hasSize(1);
    }

    @Test
    void sameContentInAnotherGroupIsNotADuplicate() throws Exception {
        var hr = service.submit("a.txt", text("Same bytes"), "hr");
        var it = service.submit("a.txt", text("Same bytes"), "it");

        assertThat(it.duplicate()).isFalse();
        assertThat(it.document().id()).isNotEqualTo(hr.document().id());
        assertThat(it.document().version()).isEqualTo(1);
    }

    @Test
    void changedContentWithTheSameNameBecomesANewVersion() throws Exception {
        var v1 = service.submit("policy.txt", text("Version one"), "hr");
        awaitStatus(v1.document().id(), "hr", DocumentStatus.INDEXED);
        var v2 = service.submit("policy.txt", text("Version two"), "hr");

        assertThat(v2.duplicate()).isFalse();
        assertThat(v2.document().version()).isEqualTo(2);
    }

    @Test
    void documentsAreInvisibleToOtherGroups() {
        var submission = service.submit("secret.txt", text("Confidential"), "hr");

        assertThatThrownBy(() -> service.status(submission.document().id(), "it"))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThatThrownBy(() -> service.status(UUID.randomUUID(), "hr"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void rejectsEmptyOversizedAndUnsupportedFiles() {
        assertThatThrownBy(() -> service.submit("a.txt", new byte[0], "hr"))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> service.submit("big.txt", new byte[1024 * 1024 + 1], "hr"))
                .isInstanceOf(InvalidDocumentException.class).hasMessageContaining("limit");
        assertThatThrownBy(() -> service.submit("tool.exe", text("MZ"), "hr"))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
        assertThatThrownBy(() -> service.submit("noextension", text("hello"), "hr"))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
        assertThat(repository.documents).isEmpty();
    }

    @Test
    void rejectsContentThatDoesNotMatchItsExtension() {
        // A plain-text file renamed to .pdf must not be accepted as a PDF.
        assertThatThrownBy(() -> service.submit("fake.pdf", text("just some text, not a pdf"), "hr"))
                .isInstanceOf(UnsupportedDocumentTypeException.class).hasMessageContaining("does not match");
        // A PDF renamed to .txt is rejected too.
        byte[] pdf = text("%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF");
        assertThatThrownBy(() -> service.submit("fake.txt", pdf, "hr"))
                .isInstanceOf(UnsupportedDocumentTypeException.class);
    }

    @Test
    void validatesTheAccessGroup() {
        for (String bad : new String[] {"", "  ", "HR Team", "../etc", "a".repeat(65), "hr;drop table documents"}) {
            assertThatThrownBy(() -> service.submit("a.txt", text("x"), bad))
                    .as("access group '%s'", bad).isInstanceOf(InvalidDocumentException.class);
        }
        // Case and surrounding spaces are normalized rather than rejected.
        assertThat(service.submit("a.txt", text("x"), "  HR ").document().accessGroup()).isEqualTo("hr");
    }

    @Test
    void sanitizesFileNames() {
        assertThat(DocumentIngestionService.sanitizeFileName("C:\\Users\\bob\\..\\policy.pdf")).isEqualTo("policy.pdf");
        assertThat(DocumentIngestionService.sanitizeFileName("../../etc/passwd.txt")).isEqualTo("passwd.txt");
        assertThat(DocumentIngestionService.sanitizeFileName("na\u0000me\n.txt")).isEqualTo("name.txt");
        assertThatThrownBy(() -> DocumentIngestionService.sanitizeFileName("  "))
                .isInstanceOf(InvalidDocumentException.class);
        assertThatThrownBy(() -> DocumentIngestionService.sanitizeFileName("a".repeat(300) + ".txt"))
                .isInstanceOf(InvalidDocumentException.class);
    }

    @Test
    void aFullQueueRejectsTheUploadAndLeavesNoOrphanRow() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor tiny = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
        try {
            // Occupy the only worker and the only queue slot with tasks that block until released.
            tiny.execute(() -> await(release));
            tiny.execute(() -> await(release));
            DocumentIngestionService busy = build(tiny, properties(DataSize.ofMegabytes(1)));

            assertThatThrownBy(() -> busy.submit("a.txt", text("hello"), "hr")).isInstanceOf(IngestionBusyException.class);
            assertThat(repository.documents).isEmpty();
        } finally {
            release.countDown();
            tiny.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void embeddingOutageMarksTheDocumentFailedWithACoarseReason() throws Exception {
        FakeEmbeddingModel down = new FakeEmbeddingModel(DIMENSIONS);
        down.failWith(new IllegalStateException("connection refused to 127.0.0.1:11434 with secret detail"));
        DocumentExtractor extractor = new DocumentExtractor(1_000_000);
        KnowledgeProperties props = properties(DataSize.ofMegabytes(1));
        DocumentIndexer indexer = new DocumentIndexer(extractor, new TextNormalizer(), new LanguageDetector(),
                new TextChunker(500, 60), down, repository, props, registry);
        DocumentIngestionService failing = new DocumentIngestionService(repository, indexer, extractor, executor, props, registry);

        var submission = failing.submit("a.txt", text("some content"), "hr");
        StoredDocument failed = awaitStatusOn(failing, submission.document().id(), DocumentStatus.FAILED);

        assertThat(failed.failureReason()).isEqualTo("EMBEDDING_UNAVAILABLE");
        assertThat(failed.chunkCount()).isZero();
    }

    @Test
    void wrongEmbeddingSizeIsRejectedBeforeAnythingIsStored() throws Exception {
        FakeEmbeddingModel wrong = new FakeEmbeddingModel(DIMENSIONS + 1);
        DocumentExtractor extractor = new DocumentExtractor(1_000_000);
        KnowledgeProperties props = properties(DataSize.ofMegabytes(1));
        DocumentIndexer indexer = new DocumentIndexer(extractor, new TextNormalizer(), new LanguageDetector(),
                new TextChunker(500, 60), wrong, repository, props, registry);
        DocumentIngestionService svc = new DocumentIngestionService(repository, indexer, extractor, executor, props, registry);

        var submission = svc.submit("a.txt", text("some content"), "hr");
        StoredDocument failed = awaitStatusOn(svc, submission.document().id(), DocumentStatus.FAILED);

        assertThat(failed.failureReason()).isEqualTo("EMBEDDING_MISMATCH");
        assertThat(repository.chunks).isEmpty();
    }

    @Test
    void aDocumentWithNoTextFailsAsEmpty() throws Exception {
        // Only characters the normalizer strips, so extraction succeeds but nothing remains to index.
        var submission = service.submit("blank.txt", text("\u00AD\uFEFF\n\n   \n"), "hr");

        StoredDocument failed = awaitStatus(submission.document().id(), "hr", DocumentStatus.FAILED);
        assertThat(failed.failureReason()).isEqualTo("EMPTY_DOCUMENT");
    }

    @Test
    void failedDocumentsCanBeReuploaded() throws Exception {
        var blank = service.submit("blank.txt", text("\u00AD\uFEFF\n\n   \n"), "hr");
        awaitStatus(blank.document().id(), "hr", DocumentStatus.FAILED);

        var again = service.submit("blank.txt", text("\u00AD\uFEFF\n\n   \n"), "hr");

        assertThat(again.duplicate()).as("a FAILED document must not block a retry").isFalse();
        assertThat(again.document().version()).isEqualTo(2);
    }

    private StoredDocument awaitStatusOn(DocumentIngestionService svc, UUID id, DocumentStatus expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            StoredDocument d = svc.status(id, "hr");
            if (d.status() == expected) {
                return d;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("document " + id + " did not reach " + expected);
    }

}
