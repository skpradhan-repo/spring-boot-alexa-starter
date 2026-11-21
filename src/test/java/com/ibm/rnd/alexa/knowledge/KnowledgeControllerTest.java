package com.ibm.rnd.alexa.knowledge;

import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ibm.rnd.alexa.exception.InvalidDocumentException;
import com.ibm.rnd.alexa.exception.ModelUnavailableException;

/** HTTP contract of the question API. The answer service is mocked, so no database or Ollama is needed. */
@SpringBootTest(properties = {
        "assistant.knowledge.enabled=true",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class KnowledgeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private KnowledgeAnswerService answers;

    // The real wiring needs a database and an embedding model; these tests replace what it would build.
    @MockitoBean
    private DocumentRepository repository;
    @MockitoBean
    private DocumentIndexer indexer;
    @MockitoBean
    private DocumentIngestionService ingestion;
    @MockitoBean
    private org.springframework.ai.embedding.EmbeddingModel embeddingModel;

    private static String json(String question, String group) {
        return group == null ? "{\"question\":\"" + question + "\"}"
                : "{\"question\":\"" + question + "\",\"access_group\":\"" + group + "\"}";
    }

    @Test
    void returnsTheAnswerWithItsSources() throws Exception {
        Mockito.when(answers.ask("How many days?", "hr")).thenReturn(new KnowledgeAnswerService.Answer(
                "24 days.", true, List.of(new KnowledgeAnswerService.Source("leave.txt", 1, 0, 0.83, "Annual leave is 24 days"))));

        mvc.perform(post("/api/knowledge/ask").contentType(MediaType.APPLICATION_JSON).content(json("How many days?", "hr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("24 days."))
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.sources[0].document").value("leave.txt"))
                .andExpect(jsonPath("$.sources[0].excerpt").value("Annual leave is 24 days"));
    }

    @Test
    void accessGroupDefaultsToPublic() throws Exception {
        Mockito.when(answers.ask(eq("hello?"), eq("public"))).thenReturn(
                new KnowledgeAnswerService.Answer(KnowledgeAnswerService.NOT_FOUND, false, List.of()));

        mvc.perform(post("/api/knowledge/ask").contentType(MediaType.APPLICATION_JSON).content(json("hello?", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(false));
    }

    @Test
    void blankQuestionIs400() throws Exception {
        mvc.perform(post("/api/knowledge/ask").contentType(MediaType.APPLICATION_JSON).content(json("  ", "hr")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooLongQuestionIs400() throws Exception {
        mvc.perform(post("/api/knowledge/ask").contentType(MediaType.APPLICATION_JSON)
                        .content(json("x".repeat(KnowledgeAnswerService.MAX_QUESTION_CHARS + 1), "hr")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidAccessGroupIs400WithOurMessage() throws Exception {
        Mockito.when(answers.ask(eq("q?"), eq("BAD GROUP")))
                .thenThrow(new InvalidDocumentException("The access group is invalid"));

        mvc.perform(post("/api/knowledge/ask").contentType(MediaType.APPLICATION_JSON).content(json("q?", "BAD GROUP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The access group is invalid"));
    }

    @Test
    void modelDownIs503WithRetryAfterAndNoInternals() throws Exception {
        Mockito.when(answers.ask(eq("q?"), eq("hr")))
                .thenThrow(new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE));

        mvc.perform(post("/api/knowledge/ask").contentType(MediaType.APPLICATION_JSON).content(json("q?", "hr")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.detail").value("The assistant is busy or unavailable, try again shortly"));
    }
}
