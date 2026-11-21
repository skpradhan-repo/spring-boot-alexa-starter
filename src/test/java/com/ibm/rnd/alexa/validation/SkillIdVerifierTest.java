package com.ibm.rnd.alexa.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.amazon.ask.model.Application;
import com.amazon.ask.model.Context;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.model.Session;
import com.amazon.ask.model.interfaces.system.SystemState;
import com.ibm.rnd.alexa.config.AlexaProperties;

class SkillIdVerifierTest {

    private static SkillIdVerifier verifierFor(String skillId) {
        return new SkillIdVerifier(new AlexaProperties(skillId,
                new AlexaProperties.Verification(true, Duration.ofSeconds(150))));
    }

    private static SimpleAlexaHttpRequest request(RequestEnvelope envelope) {
        return new SimpleAlexaHttpRequest(new byte[0], envelope, null, null);
    }

    private static RequestEnvelope.Builder base() {
        return RequestEnvelope.builder().withRequest(LaunchRequest.builder().build());
    }

    private static Application app(String id) {
        return Application.builder().withApplicationId(id).build();
    }

    @Test
    void doesNothingWhenNoSkillIdConfigured() {
        assertThatCode(() -> verifierFor("").verify(request(base().build()))).doesNotThrowAnyException();
    }

    @Test
    void acceptsMatchingIdFromContext() {
        var envelope = base().withContext(Context.builder()
                .withSystem(SystemState.builder().withApplication(app("skill-1")).build()).build()).build();

        assertThatCode(() -> verifierFor("skill-1").verify(request(envelope))).doesNotThrowAnyException();
    }

    @Test
    void acceptsMatchingIdFromSessionWhenContextAbsent() {
        var envelope = base().withSession(Session.builder().withApplication(app("skill-1")).build()).build();

        assertThatCode(() -> verifierFor("skill-1").verify(request(envelope))).doesNotThrowAnyException();
    }

    @Test
    void rejectsDifferentId() {
        var envelope = base().withSession(Session.builder().withApplication(app("other")).build()).build();

        assertThatThrownBy(() -> verifierFor("skill-1").verify(request(envelope)))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsRequestWithNoApplicationWhenIdConfigured() {
        assertThatThrownBy(() -> verifierFor("skill-1").verify(request(base().build())))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void propertiesRejectNonPositiveTolerance() {
        assertThat(verifierFor("x")).isNotNull();
        assertThatThrownBy(() -> new AlexaProperties.Verification(true, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
