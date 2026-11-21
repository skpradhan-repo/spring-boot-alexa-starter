package com.ibm.rnd.alexa.speech;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ibm.rnd.alexa.exception.InvalidAudioException;
import com.ibm.rnd.alexa.exception.SpeechUnavailableException;

/** HTTP contract of the local speech API. The service is mocked, so no Python process or model is needed. */
@SpringBootTest(properties = "assistant.speech.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SpeechControllerTest {

    private static final String ODIA = "\u0B15\u0B30\u0B4D\u0B2E\u0B1A\u0B3E\u0B30\u0B40\u0B19\u0B4D\u0B15";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SpeechService speech;

    @Test
    void statusReportsReadinessAndLanguages() throws Exception {
        Mockito.when(speech.status()).thenReturn(new SpeechRecognizer.Status("ready", List.of("or", "hi")));

        mvc.perform(get("/api/speech/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.state").value("ready"))
                .andExpect(jsonPath("$.languages[0]").value("or"));
    }

    @Test
    void statusSaysNotReadyWhenTheModelIsMissing() throws Exception {
        Mockito.when(speech.status()).thenReturn(new SpeechRecognizer.Status("model_missing", List.of()));

        mvc.perform(get("/api/speech/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.state").value("model_missing"));
    }

    @Test
    void transcribeReturnsUnicodeTextInTheRequestedLanguage() throws Exception {
        Mockito.when(speech.transcribe(any(), eq("or"))).thenReturn(ODIA);

        mvc.perform(post("/api/speech/transcribe?lang=or").contentType("audio/wav").content(new byte[] {1, 2, 3}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value(ODIA))
                .andExpect(jsonPath("$.language").value("or"));
    }

    @Test
    void badAudioIs400WithOurMessage() throws Exception {
        Mockito.when(speech.transcribe(any(), eq("or"))).thenThrow(new InvalidAudioException("The recording is too short"));

        mvc.perform(post("/api/speech/transcribe?lang=or").contentType("audio/wav").content(new byte[] {1}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The recording is too short"));
    }

    @Test
    void aDownServiceIs503WithRetryAfterAndNoInternals() throws Exception {
        Mockito.when(speech.transcribe(any(), eq("or"))).thenThrow(new SpeechUnavailableException("connect refused to 127.0.0.1:8090"));

        mvc.perform(post("/api/speech/transcribe?lang=or").contentType("audio/wav").content(new byte[] {1}))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.detail").value("Local speech recognition is not running or not ready"));
    }

    @Test
    void aMissingLanguageIs400() throws Exception {
        mvc.perform(post("/api/speech/transcribe").contentType("audio/wav").content(new byte[] {1}))
                .andExpect(status().isBadRequest());
    }

    @Test
    void otherContentTypesAre415() throws Exception {
        mvc.perform(post("/api/speech/transcribe?lang=or").contentType("application/json").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
