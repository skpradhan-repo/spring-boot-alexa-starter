package com.ibm.rnd.alexa.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/** Explains what the skill can do. The list is built from every {@link HelpTopic} bean. */
@Component
class HelpIntentHandler extends IntentHandler {

    static final String INTENT_NAME = "AMAZON.HelpIntent";
    static final String REPROMPT = "What would you like to do?";

    private final List<HelpTopic> topics;

    HelpIntentHandler(List<HelpTopic> topics) {
        super(INTENT_NAME);
        this.topics = List.copyOf(topics);
    }

    @Override
    protected SkillReply reply(HandlerInput input) {
        List<String> phrases = topics.stream().map(HelpTopic::helpPhrase).toList();
        String options = phrases.isEmpty() ? "nothing yet" : Ssml.joinWithOr(phrases);
        return SkillReply.ask("You can " + options + ". " + REPROMPT, REPROMPT);
    }
}
