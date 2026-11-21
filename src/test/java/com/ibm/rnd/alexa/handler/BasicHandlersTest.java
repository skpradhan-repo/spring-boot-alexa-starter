package com.ibm.rnd.alexa.handler;

import static com.ibm.rnd.alexa.handler.HandlerInputs.ssml;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.ui.SimpleCard;

class BasicHandlersTest {

    private final LaunchRequestHandler launch = new LaunchRequestHandler();
    private final HelloWorldIntentHandler hello = new HelloWorldIntentHandler();
    private final StopIntentHandler stop = new StopIntentHandler();
    private final CancelIntentHandler cancel = new CancelIntentHandler();
    private final FallbackIntentHandler fallback = new FallbackIntentHandler();
    private final SessionEndedRequestHandler sessionEnded = new SessionEndedRequestHandler();

    // ---- Launch

    @Test
    void launchOnlyMatchesLaunchRequests() {
        assertThat(launch.canHandle(HandlerInputs.launch())).isTrue();
        assertThat(launch.canHandle(HandlerInputs.intent("HelloWorldIntent", Map.of()))).isFalse();
    }

    @Test
    void launchWelcomesAndKeepsSessionOpenWithReprompt() {
        Response response = launch.handle(HandlerInputs.launch()).orElseThrow();

        assertThat(ssml(response)).isEqualTo(LaunchRequestHandler.GREETING);
        assertThat(response.getShouldEndSession()).isFalse();
        assertThat(HandlerInputs.repromptSsml(response)).isEqualTo(LaunchRequestHandler.REPROMPT);
    }

    // ---- HelloWorld

    @Test
    void helloOnlyMatchesItsIntent() {
        assertThat(hello.canHandle(HandlerInputs.intent("HelloWorldIntent", Map.of()))).isTrue();
        assertThat(hello.canHandle(HandlerInputs.intent("AMAZON.FallbackIntent", Map.of()))).isFalse();
        assertThat(hello.canHandle(HandlerInputs.launch())).isFalse();
    }

    @Test
    void helloEchoesMessageAndAddsCard() {
        var input = HandlerInputs.intent("HelloWorldIntent",
                HandlerInputs.slots(HandlerInputs.slot("message", "good morning")));

        Response response = hello.handle(input).orElseThrow();

        assertThat(ssml(response)).isEqualTo("I received your message: good morning");
        assertThat(response.getShouldEndSession()).isTrue();
        assertThat(((SimpleCard) response.getCard()).getContent()).isEqualTo("good morning");
    }

    @Test
    void helloEscapesSsmlInUserMessage() {
        var input = HandlerInputs.intent("HelloWorldIntent",
                HandlerInputs.slots(HandlerInputs.slot("message", "<break time=\"10s\"/> & more")));

        String speech = ssml(hello.handle(input).orElseThrow());

        assertThat(speech).isEqualTo("I received your message: &lt;break time=&quot;10s&quot;/&gt; &amp; more");
    }

    @Test
    void helloFallsBackToDefaultWhenSlotMissingEmptyOrBlank() {
        String expected = "I received your message: " + HelloWorldIntentHandler.DEFAULT_MESSAGE;

        var inputs = List.of(
                HandlerInputs.intent("HelloWorldIntent", null),
                HandlerInputs.intent("HelloWorldIntent", HandlerInputs.slots(HandlerInputs.slot("x", "y"))),
                HandlerInputs.intent("HelloWorldIntent", HandlerInputs.slots(HandlerInputs.slot("message", "  "))),
                HandlerInputs.intent("HelloWorldIntent", HandlerInputs.slots(HandlerInputs.slot("message", null))));

        for (HandlerInput input : inputs) {
            assertThat(ssml(hello.handle(input).orElseThrow())).isEqualTo(expected);
        }
    }

    // ---- Stop / Cancel

    @Test
    void stopSaysGoodbyeAndEndsSession() {
        var input = HandlerInputs.intent("AMAZON.StopIntent", Map.of());

        assertThat(stop.canHandle(input)).isTrue();
        Response response = stop.handle(input).orElseThrow();
        assertThat(ssml(response)).isEqualTo(StopIntentHandler.GOODBYE);
        assertThat(response.getShouldEndSession()).isTrue();
    }

    @Test
    void cancelConfirmsAndEndsSession() {
        var input = HandlerInputs.intent("AMAZON.CancelIntent", Map.of());

        assertThat(cancel.canHandle(input)).isTrue();
        assertThat(cancel.canHandle(HandlerInputs.intent("AMAZON.StopIntent", Map.of()))).isFalse();
        Response response = cancel.handle(input).orElseThrow();
        assertThat(ssml(response)).isEqualTo(CancelIntentHandler.CANCELLED);
        assertThat(response.getShouldEndSession()).isTrue();
    }

    // ---- Fallback

    @Test
    void fallbackApologisesAndKeepsSessionOpen() {
        var input = HandlerInputs.intent("AMAZON.FallbackIntent", Map.of());

        assertThat(fallback.canHandle(input)).isTrue();
        Response response = fallback.handle(input).orElseThrow();
        assertThat(ssml(response)).isEqualTo(FallbackIntentHandler.APOLOGY);
        assertThat(response.getShouldEndSession()).isFalse();
        assertThat(HandlerInputs.repromptSsml(response)).isEqualTo(FallbackIntentHandler.REPROMPT);
    }

    // ---- SessionEnded

    @Test
    void sessionEndedReturnsNoSpeech() {
        assertThat(sessionEnded.canHandle(HandlerInputs.sessionEnded())).isTrue();
        assertThat(sessionEnded.canHandle(HandlerInputs.launch())).isFalse();

        Response response = sessionEnded.handle(HandlerInputs.sessionEnded()).orElseThrow();

        assertThat(response.getOutputSpeech()).isNull();
        assertThat(response.getReprompt()).isNull();
    }
}
