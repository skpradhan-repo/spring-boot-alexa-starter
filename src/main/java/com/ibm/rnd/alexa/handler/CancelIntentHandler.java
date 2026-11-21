package com.ibm.rnd.alexa.handler;

import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/** Abandons the current interaction (for example a half-finished order). */
@Component
class CancelIntentHandler extends IntentHandler {

    static final String INTENT_NAME = "AMAZON.CancelIntent";
    static final String CANCELLED = "Okay, I've cancelled that.";

    CancelIntentHandler() {
        super(INTENT_NAME);
    }

    @Override
    protected SkillReply reply(HandlerInput input) {
        return SkillReply.tell(CANCELLED);
    }
}
