package com.ibm.rnd.alexa.handler;

import static com.ibm.rnd.alexa.handler.HandlerInputs.intent;
import static com.ibm.rnd.alexa.handler.HandlerInputs.repromptSsml;
import static com.ibm.rnd.alexa.handler.HandlerInputs.resolvedSlot;
import static com.ibm.rnd.alexa.handler.HandlerInputs.slot;
import static com.ibm.rnd.alexa.handler.HandlerInputs.slots;
import static com.ibm.rnd.alexa.handler.HandlerInputs.ssml;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.dialog.ElicitSlotDirective;
import com.amazon.ask.model.ui.SimpleCard;

class OrderDrinkIntentHandlerTest {

    private static final String INTENT = OrderDrinkIntentHandler.INTENT_NAME;

    private final OrderDrinkIntentHandler handler = new OrderDrinkIntentHandler();

    /** Runs the handler and returns the response; session attributes end up on the input's AttributesManager. */
    private Response run(HandlerInput input) {
        return handler.handle(input).orElseThrow();
    }

    private static Map<String, Object> sessionOf(HandlerInput input) {
        return input.getAttributesManager().getSessionAttributes();
    }

    private static String elicitedSlot(Response response) {
        return ((ElicitSlotDirective) response.getDirectives().get(0)).getSlotToElicit();
    }

    @Test
    void matchesOnlyItsIntent() {
        assertThat(handler.canHandle(intent(INTENT, Map.of()))).isTrue();
        assertThat(handler.canHandle(intent("HelloWorldIntent", Map.of()))).isFalse();
    }

    @Test
    void asksForDrinkWhenNothingSaid() {
        var input = intent(INTENT, Map.of());

        Response response = run(input);

        assertThat(ssml(response)).contains("What would you like to drink?", "coffee, tea, or juice");
        assertThat(response.getShouldEndSession()).isFalse();
        assertThat(repromptSsml(response)).isNotBlank();
        assertThat(elicitedSlot(response)).isEqualTo("drink");
    }

    @Test
    void asksForSizeAfterDrinkAndRemembersDrink() {
        var input = intent(INTENT, slots(slot("drink", "coffee")));

        Response response = run(input);

        assertThat(ssml(response)).contains("What size coffee");
        assertThat(elicitedSlot(response)).isEqualTo("size");
        assertThat(sessionOf(input)).containsEntry("drink", "COFFEE");
    }

    @Test
    void usesEntityResolutionIdInsteadOfSpokenText() {
        // "java" is a synonym the interaction model resolves to COFFEE
        var input = intent(INTENT, slots(resolvedSlot("drink", "java", "COFFEE")));

        run(input);

        assertThat(sessionOf(input)).containsEntry("drink", "COFFEE");
    }

    @Test
    void matchesSynonymsWhenRequestHasNoResolutions() {
        var input = intent(INTENT, slots(slot("drink", "Chai")));

        run(input);

        assertThat(sessionOf(input)).containsEntry("drink", "TEA");
    }

    @Test
    void completesOrderWhenBothSlotsSuppliedInOneTurn() {
        var input = intent(INTENT, slots(slot("drink", "tea"), slot("size", "large")));

        Response response = run(input);

        assertThat(ssml(response)).contains("<emphasis level=\"moderate\">large tea</emphasis>", "coming right up");
        assertThat(response.getShouldEndSession()).isFalse();
        assertThat(response.getDirectives()).isNullOrEmpty();
        assertThat(((SimpleCard) response.getCard()).getContent()).contains("large tea", "order 1");
        assertThat(sessionOf(input)).containsEntry("ordersPlaced", 1);
    }

    @Test
    void completesOrderFromSessionAcrossTurnsAndClearsCollectedSlots() {
        Map<String, Object> attributes = Map.of("drink", "COFFEE");
        var input = intent(INTENT, slots(slot("size", "big")), attributes);

        Response response = run(input);

        assertThat(ssml(response)).contains("large coffee");
        assertThat(sessionOf(input)).doesNotContainKeys("drink", "size").containsEntry("ordersPlaced", 1);
    }

    @Test
    void countsOrdersAcrossTheSession() {
        var input = intent(INTENT, slots(slot("drink", "juice"), slot("size", "small")),
                Map.of("ordersPlaced", 2));

        Response response = run(input);

        assertThat(((SimpleCard) response.getCard()).getContent()).contains("order 3");
        assertThat(sessionOf(input)).containsEntry("ordersPlaced", 3);
    }

    @Test
    void rejectsUnknownDrinkAndKeepsAlreadyChosenSize() {
        var input = intent(INTENT, slots(slot("drink", "soup")), Map.of("size", "SMALL"));

        Response response = run(input);

        assertThat(ssml(response)).contains("Sorry, I don't have soup").contains("coffee, tea, or juice");
        assertThat(elicitedSlot(response)).isEqualTo("drink");
        assertThat(sessionOf(input)).containsEntry("size", "SMALL").doesNotContainKey("drink");
    }

    @Test
    void rejectsUnknownSizeAndKeepsChosenDrink() {
        var input = intent(INTENT, slots(slot("drink", "tea"), slot("size", "gigantic")));

        Response response = run(input);

        assertThat(ssml(response)).contains("Sorry, I don't have size gigantic");
        assertThat(elicitedSlot(response)).isEqualTo("size");
        assertThat(sessionOf(input)).containsEntry("drink", "TEA");
    }

    @Test
    void unmatchedResolutionIsTreatedAsUnknown() {
        var input = intent(INTENT, slots(HandlerInputs.unmatchedSlot("drink", "lava")));

        assertThat(ssml(run(input))).contains("Sorry, I don't have lava");
    }

    @Test
    void escapesUnknownUserTextInSsml() {
        var input = intent(INTENT, slots(slot("drink", "<audio src=\"x\"/>")));

        assertThat(ssml(run(input))).doesNotContain("<audio").contains("&lt;audio");
    }

    @Test
    void worksWithoutSessionByAnsweringWithoutStoringState() {
        var input = HandlerInputs.intentWithoutSession(INTENT, slots(slot("drink", "tea")));

        Response response = run(input);

        assertThat(ssml(response)).contains("What size tea");
    }

    @Test
    void helpPhraseDescribesTheIntent() {
        assertThat(handler.helpPhrase()).contains("order a drink");
        assertThat(List.of(handler)).allMatch(HelpTopic.class::isInstance);
    }
}
