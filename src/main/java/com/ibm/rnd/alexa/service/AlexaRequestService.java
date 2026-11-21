package com.ibm.rnd.alexa.service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.amazon.ask.Skill;
import com.amazon.ask.exception.AskSdkException;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.model.ResponseEnvelope;
import com.amazon.ask.model.services.Serializer;
import com.amazon.ask.servlet.verifiers.AlexaHttpRequest;
import com.amazon.ask.servlet.verifiers.SkillServletVerifier;
import com.ibm.rnd.alexa.exception.InvalidAlexaRequestException;
import com.ibm.rnd.alexa.validation.AlexaRequestValidator;
import com.ibm.rnd.alexa.validation.SimpleAlexaHttpRequest;

/**
 * Application service for one Alexa round trip: parse, validate, verify, dispatch to the skill, serialize.
 * <p>
 * Depends only on abstractions ({@link Skill}, {@link Serializer}, {@link SkillServletVerifier}),
 * all injected, so it is trivially testable and unaware of HTTP (Dependency Inversion).
 */
@Service
public class AlexaRequestService {

    private static final Logger log = LoggerFactory.getLogger(AlexaRequestService.class);

    private final Skill skill;
    private final Serializer serializer;
    private final AlexaRequestValidator validator;
    private final List<SkillServletVerifier> verifiers;

    public AlexaRequestService(Skill skill, Serializer serializer, AlexaRequestValidator validator,
            List<SkillServletVerifier> verifiers) {
        this.skill = skill;
        this.serializer = serializer;
        this.validator = validator;
        this.verifiers = List.copyOf(verifiers);
    }

    /**
     * @param rawBody      the exact bytes received (needed unchanged for signature verification)
     * @param signature    value of the {@code Signature} header, may be null
     * @param certChainUrl value of the {@code SignatureCertChainUrl} header, may be null
     * @return the serialized Alexa response envelope
     * @throws InvalidAlexaRequestException if the body is not a valid request envelope
     * @throws SecurityException            if any verifier rejects the request
     */
    public String handle(byte[] rawBody, String signature, String certChainUrl) {
        RequestEnvelope envelope = parse(rawBody);
        validator.validate(envelope);

        AlexaHttpRequest request = new SimpleAlexaHttpRequest(rawBody, envelope, signature, certChainUrl);
        verifiers.forEach(verifier -> verifier.verify(request));

        log.debug("Dispatching Alexa request {}", envelope.getRequest().getRequestId());
        ResponseEnvelope response = skill.invoke(envelope);
        return serializer.serialize(response);
    }

    private RequestEnvelope parse(byte[] rawBody) {
        try {
            return serializer.deserialize(new String(rawBody, StandardCharsets.UTF_8), RequestEnvelope.class);
        } catch (AskSdkException e) {
            throw new InvalidAlexaRequestException("Malformed Alexa request", e);
        }
    }
}
