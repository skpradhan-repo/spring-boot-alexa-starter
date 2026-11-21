package com.ibm.rnd.alexa.handler;

import static com.amazon.ask.request.Predicates.requestType;

import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.LaunchRequest;

/** Welcomes the user when the skill is opened without an intent and keeps the session open. */
@Component
class LaunchRequestHandler extends ReplyingRequestHandler {

    static final String GREETING = "Hello World! Your Spring Boot Alexa skill is running. What would you like to do?";
    static final String REPROMPT = "You can say help to hear what I can do.";

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.matches(requestType(LaunchRequest.class));
    }

    @Override
    protected SkillReply reply(HandlerInput input) {
        return SkillReply.ask(GREETING, REPROMPT);
    }
}
