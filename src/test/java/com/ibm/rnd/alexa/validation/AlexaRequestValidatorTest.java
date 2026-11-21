package com.ibm.rnd.alexa.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.RequestEnvelope;
import com.ibm.rnd.alexa.exception.InvalidAlexaRequestException;

class AlexaRequestValidatorTest {

    private final AlexaRequestValidator validator = new AlexaRequestValidator();

    private static RequestEnvelope envelope(String version, com.amazon.ask.model.Request request) {
        return RequestEnvelope.builder().withVersion(version).withRequest(request).build();
    }

    @Test
    void acceptsValidLaunchRequest() {
        var request = LaunchRequest.builder().withRequestId("r").build();

        assertThatCode(() -> validator.validate(envelope("1.0", request))).doesNotThrowAnyException();
    }

    @Test
    void acceptsValidIntentRequest() {
        var request = IntentRequest.builder().withRequestId("r")
                .withIntent(Intent.builder().withName("HelloWorldIntent").build()).build();

        assertThatCode(() -> validator.validate(envelope("1.0", request))).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullEnvelope() {
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidAlexaRequestException.class);
    }

    @Test
    void rejectsMissingVersion() {
        var request = LaunchRequest.builder().withRequestId("r").build();

        assertThatThrownBy(() -> validator.validate(envelope(" ", request)))
                .isInstanceOf(InvalidAlexaRequestException.class).hasMessage("Missing version");
    }

    @Test
    void rejectsMissingRequest() {
        assertThatThrownBy(() -> validator.validate(envelope("1.0", null)))
                .isInstanceOf(InvalidAlexaRequestException.class).hasMessage("Missing request");
    }

    @Test
    void rejectsMissingRequestId() {
        assertThatThrownBy(() -> validator.validate(envelope("1.0", LaunchRequest.builder().build())))
                .isInstanceOf(InvalidAlexaRequestException.class).hasMessage("Missing requestId");
    }

    @Test
    void rejectsIntentRequestWithoutIntentName() {
        var noIntent = IntentRequest.builder().withRequestId("r").build();
        var noName = IntentRequest.builder().withRequestId("r").withIntent(Intent.builder().build()).build();

        assertThatThrownBy(() -> validator.validate(envelope("1.0", noIntent)))
                .isInstanceOf(InvalidAlexaRequestException.class);
        assertThatThrownBy(() -> validator.validate(envelope("1.0", noName)))
                .isInstanceOf(InvalidAlexaRequestException.class);
    }
}
