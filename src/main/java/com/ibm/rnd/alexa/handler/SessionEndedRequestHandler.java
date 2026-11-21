package com.ibm.rnd.alexa.handler;

import static com.amazon.ask.request.Predicates.requestType;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.SessionEndedRequest;

/**
 * Handles {@code SessionEndedRequest}. Alexa forbids speech in the response to this request, so the
 * handler only records why the session ended and returns an empty response.
 */
@Component
class SessionEndedRequestHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionEndedRequestHandler.class);

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.matches(requestType(SessionEndedRequest.class));
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        SessionEndedRequest request = (SessionEndedRequest) input.getRequestEnvelope().getRequest();
        log.info("Session ended: reason={}", request.getReason());
        if (request.getError() != null) {
            log.warn("Session ended with error: type={}", request.getError().getType());
        }
        return input.getResponseBuilder().build();
    }
}
