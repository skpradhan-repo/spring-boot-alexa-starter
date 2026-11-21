package com.ibm.rnd.alexa.handler;

import java.util.Map;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/** Safe access to session state; the SDK throws when asked for attributes of an out-of-session request. */
final class Sessions {

    private Sessions() {
    }

    static boolean isActive(HandlerInput input) {
        return input.getRequestEnvelope().getSession() != null;
    }

    static Map<String, Object> attributes(HandlerInput input) {
        return isActive(input) ? input.getAttributesManager().getSessionAttributes() : Map.of();
    }

    /** Numeric attribute, or 0 when absent or not a number (JSON numbers arrive as Integer or Long). */
    static int count(HandlerInput input, String name) {
        return attributes(input).get(name) instanceof Number number ? number.intValue() : 0;
    }
}
