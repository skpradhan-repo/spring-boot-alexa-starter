package com.ibm.rnd.alexa.handler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Slot;
import com.amazon.ask.model.slu.entityresolution.Resolutions;
import com.amazon.ask.model.slu.entityresolution.StatusCode;
import com.amazon.ask.model.slu.entityresolution.Value;
import com.amazon.ask.model.slu.entityresolution.ValueWrapper;
import com.amazon.ask.request.RequestHelper;

/** Null-safe access to slot values and entity resolutions of the current {@code IntentRequest}. */
final class Slots {

    private Slots() {
    }

    /** The slot value as the user said it; empty when the slot is missing, unfilled or blank. */
    static Optional<String> spoken(HandlerInput input, String slotName) {
        return RequestHelper.forHandlerInput(input).getSlotValue(slotName).filter(value -> !value.isBlank());
    }

    /**
     * The canonical id of the first successful entity resolution (the synonym-to-id mapping defined in the
     * interaction model); empty when the value did not match any entity.
     */
    static Optional<String> resolvedId(HandlerInput input, String slotName) {
        return RequestHelper.forHandlerInput(input).getSlot(slotName)
                .map(Slot::getResolutions)
                .map(Resolutions::getResolutionsPerAuthority)
                .stream()
                .flatMap(List::stream)
                .filter(resolution -> resolution.getStatus() != null
                        && resolution.getStatus().getCode() == StatusCode.ER_SUCCESS_MATCH)
                .flatMap(resolution -> resolution.getValues() == null
                        ? Stream.<ValueWrapper>empty()
                        : resolution.getValues().stream())
                .map(ValueWrapper::getValue)
                .filter(Objects::nonNull)
                .map(Value::getId)
                .filter(Objects::nonNull)
                .findFirst();
    }
}
