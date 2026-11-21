package com.ibm.rnd.alexa.handler;

import static com.amazon.ask.request.Predicates.intentName;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/**
 * Base for handlers bound to exactly one intent. Matching is implemented once here, so concrete
 * handlers only provide the intent name and the reply.
 */
abstract class IntentHandler extends ReplyingRequestHandler {

    private final String intentName;

    protected IntentHandler(String intentName) {
        this.intentName = intentName;
    }

    @Override
    public final boolean canHandle(HandlerInput input) {
        return input.matches(intentName(intentName));
    }
}
