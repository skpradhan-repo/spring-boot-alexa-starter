package com.ibm.rnd.alexa.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ibm.rnd.alexa.exception.ModelUnavailableException;
import com.ibm.rnd.alexa.exception.ModelUnavailableException.Reason;

/** HTTP contract of the browser chat API, with the model replaced by a stub (no Ollama needed). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChatService chat;

    private static final String BODY = "{\"message\":\"What is a leave policy?\"}";

    @Test
    void answersWithJson() throws Exception {
        Mockito.when(chat.answer("What is a leave policy?")).thenReturn("Time off.");

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Time off."));
    }

    @Test
    void blankMessageIsABadRequest() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void oversizedMessageIsABadRequest() throws Exception {
        String big = "{\"message\":\"" + "a".repeat(ChatService.MAX_MESSAGE_CHARS + 1) + "\"}";
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(big))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unavailableModelIs503WithRetryAfterAndNoInternals() throws Exception {
        Mockito.when(chat.answer(any())).thenThrow(new ModelUnavailableException(Reason.UNAVAILABLE,
                new IllegalStateException("connect refused 127.0.0.1:11434")));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.detail").value("The assistant is busy or unavailable, try again shortly"));
    }

    @Test
    void overloadedModelIs429() throws Exception {
        Mockito.when(chat.answer(any())).thenThrow(new ModelUnavailableException(Reason.OVERLOADED));

        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void alexaWebhookWorksWithoutAnyModel() throws Exception {
        String launch = """
                {"version":"1.0","session":{"new":true,"sessionId":"s","application":{"applicationId":"a"},"user":{"userId":"u"}},
                 "request":{"type":"LaunchRequest","requestId":"r","timestamp":"2026-01-01T00:00:00Z","locale":"en-US"}}
                """;
        mvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content(launch))
                .andExpect(status().isOk());
        Mockito.verifyNoInteractions(chat);
    }
}
