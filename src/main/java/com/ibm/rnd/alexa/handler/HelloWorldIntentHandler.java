package com.ibm.rnd.alexa.handler;

import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/** Echoes back the {@code message} slot of {@code HelloWorldIntent}, with a card for the Alexa app. */
@Component
class HelloWorldIntentHandler extends IntentHandler implements HelpTopic {

    static final String INTENT_NAME = "HelloWorldIntent";
    static final String MESSAGE_SLOT = "message";
    static final String DEFAULT_MESSAGE = "Hello World";

    HelloWorldIntentHandler() {
        super(INTENT_NAME);
    }

    @Override
    protected SkillReply reply(HandlerInput input) {
        String message = Slots.spoken(input, MESSAGE_SLOT).orElse(DEFAULT_MESSAGE);
        // The message is user-controlled: escape it so it can never inject SSML markup.
        return SkillReply.tell("I received your message: " + Ssml.escape(message))
                .withCard("Hello World", message);
    }

    @Override
    public String helpPhrase() {
        return "say hello followed by a message";
    }
}
