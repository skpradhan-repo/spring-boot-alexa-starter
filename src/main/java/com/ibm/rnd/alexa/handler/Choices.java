package com.ibm.rnd.alexa.handler;

import java.util.Arrays;
import java.util.Optional;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/** Resolves slot values and session state to members of a {@link Choice} enum. */
final class Choices {

    private Choices() {
    }

    /**
     * Prefers Alexa's entity resolution (canonical id); falls back to matching the spoken text against
     * labels and synonyms, which is what happens for simulator requests that carry no resolutions.
     */
    static <T extends Enum<T> & Choice> Optional<T> fromSlot(HandlerInput input, String slotName, Class<T> type) {
        return Slots.resolvedId(input, slotName)
                .flatMap(id -> findById(type, id))
                .or(() -> Slots.spoken(input, slotName).flatMap(spoken -> findBySpoken(type, spoken)));
    }

    /** Reads a choice previously stored (by id) in the session. */
    static <T extends Enum<T> & Choice> Optional<T> fromSession(HandlerInput input, String key, Class<T> type) {
        return Optional.ofNullable(Sessions.attributes(input).get(key))
                .map(Object::toString)
                .flatMap(id -> findById(type, id));
    }

    static <T extends Enum<T> & Choice> String labels(Class<T> type) {
        return Ssml.joinWithOr(Arrays.stream(type.getEnumConstants()).map(Choice::label).toList());
    }

    private static <T extends Enum<T> & Choice> Optional<T> findById(Class<T> type, String id) {
        return Arrays.stream(type.getEnumConstants()).filter(choice -> choice.id().equals(id)).findFirst();
    }

    private static <T extends Enum<T> & Choice> Optional<T> findBySpoken(Class<T> type, String spoken) {
        return Arrays.stream(type.getEnumConstants()).filter(choice -> choice.matchesSpoken(spoken)).findFirst();
    }
}
