package com.ibm.rnd.alexa.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** With verification enabled (default / prod), unsigned or foreign requests must be rejected. */
@SpringBootTest(properties = "alexa.skill-id=expected-skill")
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class AlexaControllerSecureTest {

    private static final String UNSIGNED_REQUEST = """
            {"version":"1.0",
             "session":{"new":true,"sessionId":"s","application":{"applicationId":"expected-skill"},"user":{"userId":"u"}},
             "request":{"type":"LaunchRequest","requestId":"r","timestamp":"2026-01-01T00:00:00Z","locale":"en-US"}}
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void unsignedRequestIsRejected() throws Exception {
        mvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content(UNSIGNED_REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid Alexa request"));
    }
}
