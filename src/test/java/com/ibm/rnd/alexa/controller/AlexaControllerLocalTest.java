package com.ibm.rnd.alexa.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** End-to-end test of the webhook with the unsigned "local" profile used by the browser simulator. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AlexaControllerLocalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mvc;

    private static String envelope(boolean isNew, String attributesJson, String requestJson) {
        return """
                {"version":"1.0",
                 "session":{"new":%s,"sessionId":"s-1","application":{"applicationId":"app"},
                            "user":{"userId":"u"},"attributes":%s},
                 "request":%s}
                """.formatted(isNew, attributesJson, requestJson);
    }

    private static String intent(String name, String slotsJson) {
        return """
                {"type":"IntentRequest","requestId":"r","timestamp":"2026-01-01T00:00:00Z","locale":"en-US",
                 "intent":{"name":"%s","confirmationStatus":"NONE","slots":%s}}
                """.formatted(name, slotsJson);
    }

    private static String slot(String name, String value) {
        return "\"%s\":{\"name\":\"%s\",\"value\":\"%s\",\"confirmationStatus\":\"NONE\"}".formatted(name, name, value);
    }

    private JsonNode send(String body) throws Exception {
        MvcResult result = mvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void launchRequestWelcomesAndKeepsSessionOpen() throws Exception {
        JsonNode response = send(envelope(true, "{}",
                "{\"type\":\"LaunchRequest\",\"requestId\":\"r\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"locale\":\"en-US\"}"));

        assertThat(response.at("/response/shouldEndSession").asBoolean()).isFalse();
        assertThat(response.at("/response/reprompt/outputSpeech/ssml").asText()).startsWith("<speak>");
    }

    @Test
    void helloWorldIntentIsRoutedWithCard() throws Exception {
        JsonNode response = send(envelope(false, "{}", intent("HelloWorldIntent", "{" + slot("message", "hi there") + "}")));

        assertThat(response.at("/response/outputSpeech/ssml").asText())
                .isEqualTo("<speak>I received your message: hi there</speak>");
        assertThat(response.at("/response/card/type").asText()).isEqualTo("Simple");
        assertThat(response.at("/response/shouldEndSession").asBoolean()).isTrue();
    }

    @Test
    void multiTurnOrderCarriesSessionAttributesBetweenRequests() throws Exception {
        // Turn 1: only the drink is given -> skill asks for the size and returns what it remembered
        JsonNode turn1 = send(envelope(false, "{}", intent("OrderDrinkIntent", "{" + slot("drink", "coffee") + "}")));

        assertThat(turn1.at("/response/outputSpeech/ssml").asText()).contains("What size coffee");
        assertThat(turn1.at("/response/directives/0/type").asText()).isEqualTo("Dialog.ElicitSlot");
        assertThat(turn1.at("/response/directives/0/slotToElicit").asText()).isEqualTo("size");
        assertThat(turn1.at("/sessionAttributes/drink").asText()).isEqualTo("COFFEE");

        // Turn 2: the client echoes the attributes back, exactly like a real Alexa device
        String attributes = JSON.writeValueAsString(turn1.get("sessionAttributes"));
        JsonNode turn2 = send(envelope(false, attributes, intent("OrderDrinkIntent", "{" + slot("size", "large") + "}")));

        assertThat(turn2.at("/response/outputSpeech/ssml").asText()).contains("large coffee");
        assertThat(turn2.at("/response/card/content").asText()).contains("large coffee");
        assertThat(turn2.at("/sessionAttributes/ordersPlaced").asInt()).isEqualTo(1);
        assertThat(turn2.at("/sessionAttributes").has("drink")).isFalse();
    }

    @Test
    void stopIntentEndsSession() throws Exception {
        JsonNode response = send(envelope(false, "{}", intent("AMAZON.StopIntent", "{}")));

        assertThat(response.at("/response/shouldEndSession").asBoolean()).isTrue();
    }

    @Test
    void sessionEndedRequestIsAcceptedWithoutSpeech() throws Exception {
        JsonNode response = send(envelope(false, "{}",
                "{\"type\":\"SessionEndedRequest\",\"requestId\":\"r\",\"timestamp\":\"2026-01-01T00:00:00Z\","
                        + "\"locale\":\"en-US\",\"reason\":\"USER_INITIATED\"}"));

        assertThat(response.at("/response").has("outputSpeech")).isFalse();
    }

    @Test
    void malformedJsonIsRejectedWithoutLeakingDetails() throws Exception {
        mvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid Alexa request"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Exception"))));
    }

    @Test
    void structurallyInvalidRequestIsRejected() throws Exception {
        mvc.perform(post("/alexa").contentType(MediaType.APPLICATION_JSON).content("{\"version\":\"1.0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid Alexa request"));
    }

    @Test
    void nonJsonContentTypeKeepsMvcStatus() throws Exception {
        mvc.perform(post("/alexa").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
