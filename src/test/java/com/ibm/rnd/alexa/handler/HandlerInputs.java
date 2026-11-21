package com.ibm.rnd.alexa.handler;

import java.util.HashMap;
import java.util.Map;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Application;
import com.amazon.ask.model.Intent;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Request;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.SessionEndedReason;
import com.amazon.ask.model.SessionEndedRequest;
import com.amazon.ask.model.Session;
import com.amazon.ask.model.Slot;
import com.amazon.ask.model.slu.entityresolution.Resolution;
import com.amazon.ask.model.slu.entityresolution.Resolutions;
import com.amazon.ask.model.slu.entityresolution.Status;
import com.amazon.ask.model.slu.entityresolution.StatusCode;
import com.amazon.ask.model.slu.entityresolution.Value;
import com.amazon.ask.model.slu.entityresolution.ValueWrapper;
import com.amazon.ask.model.ui.SsmlOutputSpeech;

/** Builds Alexa request fixtures for handler tests. */
final class HandlerInputs {

    private HandlerInputs() {
    }

    static HandlerInput launch() {
        return input(LaunchRequest.builder().withRequestId("req-1").build(), session(Map.of()));
    }

    static HandlerInput sessionEnded() {
        return input(SessionEndedRequest.builder().withRequestId("req-1")
                .withReason(SessionEndedReason.USER_INITIATED).build(), session(Map.of()));
    }

    /** Intent request inside a session carrying {@code attributes}. */
    static HandlerInput intent(String name, Map<String, Slot> slots, Map<String, Object> attributes) {
        return input(intentRequest(name, slots), session(attributes));
    }

    static HandlerInput intent(String name, Map<String, Slot> slots) {
        return intent(name, slots, Map.of());
    }

    /** Intent request that carries no session at all (out-of-session). */
    static HandlerInput intentWithoutSession(String name, Map<String, Slot> slots) {
        return input(intentRequest(name, slots), null);
    }

    static Slot slot(String name, String value) {
        return Slot.builder().withName(name).withValue(value).build();
    }

    /** A slot as Alexa sends it after entity resolution: the spoken value plus a canonical id. */
    static Slot resolvedSlot(String name, String spoken, String canonicalId) {
        Resolution resolution = Resolution.builder()
                .withAuthority("amzn1.er-authority.test")
                .withStatus(Status.builder().withCode(StatusCode.ER_SUCCESS_MATCH).build())
                .addValuesItem(ValueWrapper.builder()
                        .withValue(Value.builder().withName(canonicalId).withId(canonicalId).build()).build())
                .build();
        return Slot.builder().withName(name).withValue(spoken)
                .withResolutions(Resolutions.builder().addResolutionsPerAuthorityItem(resolution).build())
                .build();
    }

    static Slot unmatchedSlot(String name, String spoken) {
        Resolution resolution = Resolution.builder()
                .withAuthority("amzn1.er-authority.test")
                .withStatus(Status.builder().withCode(StatusCode.ER_SUCCESS_NO_MATCH).build())
                .build();
        return Slot.builder().withName(name).withValue(spoken)
                .withResolutions(Resolutions.builder().addResolutionsPerAuthorityItem(resolution).build())
                .build();
    }

    static Map<String, Slot> slots(Slot... slots) {
        Map<String, Slot> map = new HashMap<>();
        for (Slot slot : slots) {
            map.put(slot.getName(), slot);
        }
        return map;
    }

    /** The spoken text of a response with the {@code <speak>} wrapper removed. */
    static String ssml(Response response) {
        String ssml = ((SsmlOutputSpeech) response.getOutputSpeech()).getSsml();
        return ssml.replaceAll("^<speak>|</speak>$", "");
    }

    static String repromptSsml(Response response) {
        String ssml = ((SsmlOutputSpeech) response.getReprompt().getOutputSpeech()).getSsml();
        return ssml.replaceAll("^<speak>|</speak>$", "");
    }

    private static IntentRequest intentRequest(String name, Map<String, Slot> slots) {
        Intent intent = Intent.builder().withName(name).withSlots(slots).build();
        return IntentRequest.builder().withRequestId("req-1").withIntent(intent).build();
    }

    private static Session session(Map<String, Object> attributes) {
        return Session.builder()
                .withSessionId("session-1")
                .withNew(false)
                .withApplication(Application.builder().withApplicationId("app").build())
                .withAttributes(new HashMap<>(attributes))
                .build();
    }

    private static HandlerInput input(Request request, Session session) {
        return HandlerInput.builder()
                .withRequestEnvelope(RequestEnvelope.builder().withRequest(request).withSession(session).build())
                .build();
    }
}
