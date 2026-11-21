package com.ibm.rnd.alexa.validation;

import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.amazon.ask.model.Application;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.servlet.verifiers.AlexaHttpRequest;
import com.amazon.ask.servlet.verifiers.SkillServletVerifier;
import com.ibm.rnd.alexa.config.AlexaProperties;
import com.ibm.rnd.alexa.exception.SkillNotAuthorizedException;

/**
 * Rejects requests addressed to a different skill than the one configured in
 * {@code alexa.skill-id}. Does nothing when no skill id is configured.
 */
@Component
@Order(3)
public class SkillIdVerifier implements SkillServletVerifier {

    private final AlexaProperties properties;

    public SkillIdVerifier(AlexaProperties properties) {
        this.properties = properties;
    }

    @Override
    public void verify(AlexaHttpRequest request) {
        if (!properties.hasSkillId()) {
            return;
        }
        String actual = applicationId(request.getDeserializedRequestEnvelope()).orElse(null);
        if (!properties.skillId().equals(actual)) {
            throw new SkillNotAuthorizedException("Request is not addressed to the configured skill");
        }
    }

    /** Real Alexa requests carry {@code context.System.application}; the simulator only sends {@code session}. */
    private static Optional<String> applicationId(RequestEnvelope envelope) {
        Optional<Application> fromContext = Optional.ofNullable(envelope.getContext())
                .flatMap(context -> Optional.ofNullable(context.getSystem()))
                .flatMap(system -> Optional.ofNullable(system.getApplication()));
        return fromContext
                .or(() -> Optional.ofNullable(envelope.getSession())
                        .flatMap(session -> Optional.ofNullable(session.getApplication())))
                .map(Application::getApplicationId);
    }
}
