package com.ibm.rnd.alexa.handler;

import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/** Handles utterances that match no other intent, and keeps the session open so the user can retry. */
@Component
class FallbackIntentHandler extends IntentHandler {

    static final String INTENT_NAME = "AMAZON.FallbackIntent";
    static final String APOLOGY = "Sorry, I didn't understand that request. You can say help to hear what I can do.";
    static final String REPROMPT = "Say help to hear what I can do.";

    FallbackIntentHandler() {
        super(INTENT_NAME);
    }

    @Override
    protected SkillReply reply(HandlerInput input) {
        return SkillReply.ask(APOLOGY, REPROMPT);
    }
}
