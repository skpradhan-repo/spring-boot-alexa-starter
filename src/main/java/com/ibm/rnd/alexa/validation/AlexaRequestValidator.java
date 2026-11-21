package com.ibm.rnd.alexa.validation;

import org.springframework.stereotype.Component;

import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Request;
import com.amazon.ask.model.RequestEnvelope;
import com.ibm.rnd.alexa.exception.InvalidAlexaRequestException;

/**
 * Structural validation of a parsed {@link RequestEnvelope}: the fields every handler relies on must be
 * present. Authenticity (signature, timestamp, skill id) is a separate concern handled by the
 * {@code SkillServletVerifier} beans.
 */
@Component
public class AlexaRequestValidator {

    /** @throws InvalidAlexaRequestException describing the first problem found */
    public void validate(RequestEnvelope envelope) {
        if (envelope == null) {
            throw new InvalidAlexaRequestException("Empty request");
        }
        if (isBlank(envelope.getVersion())) {
            throw new InvalidAlexaRequestException("Missing version");
        }
        Request request = envelope.getRequest();
        if (request == null) {
            throw new InvalidAlexaRequestException("Missing request");
        }
        if (isBlank(request.getRequestId())) {
            throw new InvalidAlexaRequestException("Missing requestId");
        }
        if (request instanceof IntentRequest intentRequest
                && (intentRequest.getIntent() == null || isBlank(intentRequest.getIntent().getName()))) {
            throw new InvalidAlexaRequestException("IntentRequest without an intent name");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
