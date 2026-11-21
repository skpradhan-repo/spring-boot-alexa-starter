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

/** Skill-id enforcement on its own (signature checks off, as for the simulator): wrong skill is 403. */
@SpringBootTest(properties = "alexa.skill-id=expected-skill")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AlexaControllerSkillIdTest {

    private static String launch(String applicationId) {
        return """
                {"version":"1.0",
                 "session":{"new":true,"sessionId":"s","application":{"applicationId":"%s"},"user":{"userId":"u"}},
                 "request":{"type":"LaunchRequest","requestId":"r","timestamp":"2026-01-01T00:00:00Z","locale":"en-US"}}
                """.formatted(applicationId);
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void requestForAnotherSkillIsForbidden() throws Exception {
        mvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content(launch("someone-elses-skill")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Request is not permitted for this skill"));
    }

    @Test
    void requestForTheConfiguredSkillIsAccepted() throws Exception {
        mvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content(launch("expected-skill")))
                .andExpect(status().isOk());
    }

    @Test
    void requestWithoutAnyApplicationIdIsForbidden() throws Exception {
        String noApp = """
                {"version":"1.0","session":{"new":true,"sessionId":"s","user":{"userId":"u"}},
                 "request":{"type":"LaunchRequest","requestId":"r","timestamp":"2026-01-01T00:00:00Z","locale":"en-US"}}
                """;
        mvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content(noApp))
                .andExpect(status().isForbidden());
    }
}
