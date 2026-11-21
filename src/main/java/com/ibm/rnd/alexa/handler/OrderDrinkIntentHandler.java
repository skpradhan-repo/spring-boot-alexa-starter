package com.ibm.rnd.alexa.handler;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;

/**
 * Multi-turn demo intent that exercises the full dialog toolkit: several slots, entity resolutions
 * (synonym to canonical id), reprompts, SSML, cards, slot elicitation and session attributes.
 * <p>
 * Slots filled in an earlier turn are kept in the session, so the user may give the drink and the size in
 * either order, or both at once.
 */
@Component
class OrderDrinkIntentHandler extends IntentHandler implements HelpTopic {

    static final String INTENT_NAME = "OrderDrinkIntent";
    static final String DRINK_SLOT = "drink";
    static final String SIZE_SLOT = "size";
    static final String ORDERS_PLACED = "ordersPlaced";

    OrderDrinkIntentHandler() {
        super(INTENT_NAME);
    }

    @Override
    protected SkillReply reply(HandlerInput input) {
        Optional<Drink> spokenDrink = Choices.fromSlot(input, DRINK_SLOT, Drink.class);
        Optional<Size> spokenSize = Choices.fromSlot(input, SIZE_SLOT, Size.class);
        Optional<Drink> drink = spokenDrink.or(() -> Choices.fromSession(input, DRINK_SLOT, Drink.class));
        Optional<Size> size = spokenSize.or(() -> Choices.fromSession(input, SIZE_SLOT, Size.class));

        // A value was said this turn but is not on the menu: say so instead of silently ignoring it.
        Optional<String> unknownDrink = Slots.spoken(input, DRINK_SLOT).filter(v -> spokenDrink.isEmpty());
        if (unknownDrink.isPresent()) {
            return remember(askFor(DRINK_SLOT, "Sorry, I don't have " + Ssml.escape(unknownDrink.get())
                    + ". Please choose " + Choices.labels(Drink.class) + "."), null, size.orElse(null));
        }
        Optional<String> unknownSize = Slots.spoken(input, SIZE_SLOT).filter(v -> spokenSize.isEmpty());
        if (unknownSize.isPresent()) {
            return remember(askFor(SIZE_SLOT, "Sorry, I don't have size " + Ssml.escape(unknownSize.get())
                    + ". Please choose " + Choices.labels(Size.class) + "."), drink.orElse(null), null);
        }

        if (drink.isEmpty()) {
            return remember(askFor(DRINK_SLOT, "What would you like to drink? We have "
                    + Choices.labels(Drink.class) + "."), null, size.orElse(null));
        }
        if (size.isEmpty()) {
            return remember(askFor(SIZE_SLOT, "What size " + drink.get().label() + " would you like? "
                    + Choices.labels(Size.class) + "?"), drink.get(), null);
        }
        return complete(input, drink.get(), size.get());
    }

    private static SkillReply askFor(String slot, String question) {
        return SkillReply.ask(question, question).elicitingSlot(slot);
    }

    /** Stores the slots collected so far so a later turn can continue the order. */
    private static SkillReply remember(SkillReply reply, Drink drink, Size size) {
        SkillReply result = reply;
        if (drink != null) {
            result = result.withSessionAttribute(DRINK_SLOT, drink.id());
        }
        if (size != null) {
            result = result.withSessionAttribute(SIZE_SLOT, size.id());
        }
        return result;
    }

    private static SkillReply complete(HandlerInput input, Drink drink, Size size) {
        int orders = Sessions.count(input, ORDERS_PLACED) + 1;
        String summary = size.label() + " " + drink.label();
        return SkillReply.ask(
                        "Got it. One <emphasis level=\"moderate\">" + summary + "</emphasis>" + Ssml.pause(300)
                                + " coming right up. Would you like anything else?",
                        "Would you like anything else? You can order another drink or say stop.")
                .withCard("Your order", "1 x " + summary + " (order " + orders + " of this session)")
                .withSessionAttribute(ORDERS_PLACED, orders)
                .withoutSessionAttribute(DRINK_SLOT)
                .withoutSessionAttribute(SIZE_SLOT);
    }

    @Override
    public String helpPhrase() {
        return "order a drink, such as a large coffee";
    }
}
