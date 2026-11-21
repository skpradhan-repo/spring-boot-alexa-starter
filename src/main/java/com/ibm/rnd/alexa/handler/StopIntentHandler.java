package com.ibm.rnd.alexa.handler;

import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/** Ends the conversation politely. */
@Component
class StopIntentHandler extends IntentHandler {

    static final String INTENT_NAME = "AMAZON.StopIntent";
    static final String GOODBYE = "Goodbye!";

    StopIntentHandler() {
        super(INTENT_NAME);
    }

    @Override
    protected SkillReply reply(HandlerInput input) {
        return SkillReply.tell(GOODBYE);
    }
}
