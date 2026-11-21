package com.ibm.rnd.alexa.handler;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.exception.ExceptionHandler;
import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;

/**
 * Last line of defence inside the skill: an unexpected failure in any handler becomes a spoken apology
 * instead of an HTTP 500. Details are logged server-side only and never spoken.
 */
@Component
class GenericErrorHandler implements ExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GenericErrorHandler.class);

    static final String APOLOGY = "Sorry, something went wrong. Please try again.";

    @Override
    public boolean canHandle(HandlerInput input, Throwable throwable) {
        return true;
    }

    @Override
    public Optional<Response> handle(HandlerInput input, Throwable throwable) {
        log.error("Unhandled error in skill handler", throwable);
        return ReplyRenderer.render(input, SkillReply.ask(APOLOGY, FallbackIntentHandler.REPROMPT));
    }
}
