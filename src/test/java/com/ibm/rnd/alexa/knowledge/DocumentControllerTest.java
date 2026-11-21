package com.ibm.rnd.alexa.knowledge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ibm.rnd.alexa.exception.DocumentNotFoundException;
import com.ibm.rnd.alexa.exception.IngestionBusyException;
import com.ibm.rnd.alexa.exception.InvalidDocumentException;
import com.ibm.rnd.alexa.exception.UnsupportedDocumentTypeException;

/** HTTP contract of the upload API. The service is mocked, so no database or Ollama is needed. */
@SpringBootTest(properties = {
        "assistant.knowledge.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DocumentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DocumentIngestionService ingestion;

    // The real wiring needs a database and an embedding model; these tests replace what it would build.
    @MockitoBean
    private DocumentRepository repository;
    @MockitoBean
    private DocumentIndexer indexer;
    @MockitoBean
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    private static StoredDocument stored(DocumentStatus status) {
        return new StoredDocument(UUID.fromString("11111111-1111-1111-1111-111111111111"), "leave.txt", 1, "hr",
                null, status, null, 0, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "leave.txt", "text/plain", "Annual leave is 20 days.".getBytes());
    }

    @Test
    void newUploadIs202WithPendingStatus() throws Exception {
        Mockito.when(ingestion.submit(eq("leave.txt"), any(), eq("hr")))
                .thenReturn(new DocumentIngestionService.Submission(stored(DocumentStatus.PENDING), false));

        mvc.perform(multipart("/api/documents").file(file()).param("access_group", "hr"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void duplicateUploadIs200() throws Exception {
        Mockito.when(ingestion.submit(any(), any(), any()))
                .thenReturn(new DocumentIngestionService.Submission(stored(DocumentStatus.INDEXED), true));

        mvc.perform(multipart("/api/documents").file(file()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void accessGroupDefaultsToPublic() throws Exception {
        Mockito.when(ingestion.submit(any(), any(), eq("public")))
                .thenReturn(new DocumentIngestionService.Submission(stored(DocumentStatus.PENDING), false));

        mvc.perform(multipart("/api/documents").file(file())).andExpect(status().isAccepted());
    }

    @Test
    void missingFileIsABadRequest() throws Exception {
        mvc.perform(multipart("/api/documents").param("access_group", "hr")).andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedTypeIs415() throws Exception {
        Mockito.when(ingestion.submit(any(), any(), any()))
                .thenThrow(new UnsupportedDocumentTypeException("Supported file types: pdf, docx, txt, html, md"));

        mvc.perform(multipart("/api/documents").file(file()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.detail").value("Supported file types: pdf, docx, txt, html, md"));
    }

    @Test
    void invalidDocumentIs400() throws Exception {
        Mockito.when(ingestion.submit(any(), any(), any())).thenThrow(new InvalidDocumentException("The file is empty"));

        mvc.perform(multipart("/api/documents").file(file()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The file is empty"));
    }

    @Test
    void fullQueueIs429WithRetryAfter() throws Exception {
        Mockito.when(ingestion.submit(any(), any(), any())).thenThrow(new IngestionBusyException());

        mvc.perform(multipart("/api/documents").file(file()))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));
    }

    @Test
    void statusReturnsTheDocument() throws Exception {
        UUID id = stored(DocumentStatus.INDEXED).id();
        Mockito.when(ingestion.status(id, "hr")).thenReturn(stored(DocumentStatus.INDEXED));

        mvc.perform(get("/api/documents/{id}", id).param("access_group", "hr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.name").value("leave.txt"));
    }

    @Test
    void unknownOrForeignDocumentIs404WithNoDetail() throws Exception {
        Mockito.when(ingestion.status(any(), any())).thenThrow(new DocumentNotFoundException());

        mvc.perform(get("/api/documents/{id}", UUID.randomUUID()).param("access_group", "it"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document not found"));
    }
}
