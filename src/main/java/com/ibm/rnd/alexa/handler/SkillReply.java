package com.ibm.rnd.alexa.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable description of what the skill answers, independent of the ASK SDK response types.
 * <p>
 * Handlers describe <i>what</i> to say; {@link ReplyRenderer} decides <i>how</i> that maps to an Alexa
 * response. Invariants are enforced on construction so that no handler can emit an invalid combination
 * (for example an open session without a reprompt).
 *
 * @param speech            SSML fragment to speak (without the {@code <speak>} wrapper); user-supplied text must be
 *                          escaped with {@link Ssml#escape(String)}
 * @param reprompt          SSML fragment spoken if the user stays silent; required while the session stays open
 * @param card              optional card shown in the Alexa app
 * @param elicitSlot        optional name of the slot to ask for next (emits a {@code Dialog.ElicitSlot} directive)
 * @param endSession        whether the session closes after this reply
 * @param sessionAttributes attributes merged into the session state
 * @param removedAttributes names of session attributes to drop
 */
public record SkillReply(
        String speech,
        String reprompt,
        Card card,
        String elicitSlot,
        boolean endSession,
        Map<String, Object> sessionAttributes,
        Set<String> removedAttributes) {

    /** Simple (text-only) card. */
    public record Card(String title, String text) {
    }

    public SkillReply {
        if (speech == null || speech.isBlank()) {
            throw new IllegalArgumentException("speech must not be blank");
        }
        if (!endSession && (reprompt == null || reprompt.isBlank())) {
            throw new IllegalArgumentException("a reprompt is required when the session stays open");
        }
        if (elicitSlot != null && endSession) {
            throw new IllegalArgumentException("a slot cannot be elicited when the session ends");
        }
        sessionAttributes = sessionAttributes == null ? Map.of() : Map.copyOf(sessionAttributes);
        removedAttributes = removedAttributes == null ? Set.of() : Set.copyOf(removedAttributes);
    }

    /** Speaks and closes the session. */
    public static SkillReply tell(String speech) {
        return new SkillReply(speech, null, null, null, true, Map.of(), Set.of());
    }

    /** Speaks and keeps the session open, re-prompting with {@code reprompt} on silence. */
    public static SkillReply ask(String speech, String reprompt) {
        return new SkillReply(speech, reprompt, null, null, false, Map.of(), Set.of());
    }

    public SkillReply withCard(String title, String text) {
        return new SkillReply(speech, reprompt, new Card(title, text), elicitSlot, endSession,
                sessionAttributes, removedAttributes);
    }

    public SkillReply withSessionAttribute(String name, Object value) {
        Map<String, Object> merged = new HashMap<>(sessionAttributes);
        merged.put(name, value);
        return new SkillReply(speech, reprompt, card, elicitSlot, endSession, merged, removedAttributes);
    }

    public SkillReply withoutSessionAttribute(String name) {
        Set<String> removed = new java.util.HashSet<>(removedAttributes);
        removed.add(name);
        return new SkillReply(speech, reprompt, card, elicitSlot, endSession, sessionAttributes, removed);
    }

    /** Asks Alexa to collect {@code slotName} on the next turn. Only valid while the session stays open. */
    public SkillReply elicitingSlot(String slotName) {
        return new SkillReply(speech, reprompt, card, slotName, endSession, sessionAttributes, removedAttributes);
    }
}
