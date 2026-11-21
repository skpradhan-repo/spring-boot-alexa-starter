package com.ibm.rnd.alexa.config;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.amazon.ask.Skill;
import com.amazon.ask.builder.CustomSkillBuilder;
import com.amazon.ask.dispatcher.exception.ExceptionHandler;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.services.Serializer;
import com.amazon.ask.servlet.verifiers.SkillRequestSignatureVerifier;
import com.amazon.ask.servlet.verifiers.SkillRequestTimestampVerifier;
import com.amazon.ask.servlet.verifiers.SkillServletVerifier;
import com.amazon.ask.util.JacksonSerializer;

/**
 * Wires the Alexa Skills Kit SDK into the Spring container.
 * <p>
 * Request handlers and verifiers are discovered as beans, so adding a new capability or a new
 * verification step means adding a new component - this class does not change
 * (Open/Closed Principle).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AlexaProperties.class)
public class AlexaSkillConfiguration {

    private static final String VERIFICATION_ENABLED = "alexa.verification.enabled";

    @Bean
    Serializer alexaSerializer() {
        return new JacksonSerializer();
    }

    @Bean
    Skill alexaSkill(List<RequestHandler> handlers, List<ExceptionHandler> exceptionHandlers) {
        // The skill id is enforced by SkillIdVerifier: the SDK's own check throws an NPE when
        // the request has no "context" object, which is the case for the browser simulator.
        CustomSkillBuilder builder = new CustomSkillBuilder();
        handlers.forEach(builder::addRequestHandler);
        exceptionHandlers.forEach(builder::addExceptionHandler);
        return builder.build();
    }

    @Bean
    @Order(1)
    @ConditionalOnProperty(name = VERIFICATION_ENABLED, havingValue = "true", matchIfMissing = true)
    SkillServletVerifier signatureVerifier() {
        return new SkillRequestSignatureVerifier();
    }

    @Bean
    @Order(2)
    @ConditionalOnProperty(name = VERIFICATION_ENABLED, havingValue = "true", matchIfMissing = true)
    SkillServletVerifier timestampVerifier(AlexaProperties properties) {
        return new SkillRequestTimestampVerifier(properties.verification().timestampTolerance().toMillis());
    }
}
