package com.ibm.rnd.alexa.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.response.ResponseBuilder;

/** Adapter from the SDK-independent {@link SkillReply} to an ASK SDK {@link Response}. */
final class ReplyRenderer {

    private ReplyRenderer() {
    }

    static Optional<Response> render(HandlerInput input, SkillReply reply) {
        ResponseBuilder builder = input.getResponseBuilder()
                .withSpeech(reply.speech())
                .withShouldEndSession(reply.endSession());

        if (reply.reprompt() != null) {
            builder.withReprompt(reply.reprompt());
        }
        if (reply.card() != null) {
            builder.withSimpleCard(reply.card().title(), reply.card().text());
        }
        if (reply.elicitSlot() != null) {
            builder.addElicitSlotDirective(reply.elicitSlot(), currentIntent(input));
        }
        updateSessionAttributes(input, reply);

        return builder.build();
    }

    private static Intent currentIntent(HandlerInput input) {
        if (input.getRequestEnvelope().getRequest() instanceof IntentRequest intentRequest) {
            return intentRequest.getIntent();
        }
        throw new IllegalStateException("A slot can only be elicited while handling an IntentRequest");
    }

    /** Out-of-session requests cannot carry attributes, so they are skipped rather than failing the reply. */
    private static void updateSessionAttributes(HandlerInput input, SkillReply reply) {
        if (!Sessions.isActive(input)
                || (reply.sessionAttributes().isEmpty() && reply.removedAttributes().isEmpty())) {
            return;
        }
        Map<String, Object> updated = new HashMap<>(Sessions.attributes(input));
        updated.putAll(reply.sessionAttributes());
        updated.keySet().removeAll(reply.removedAttributes());
        input.getAttributesManager().setSessionAttributes(updated);
    }
}
