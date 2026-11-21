package com.ibm.rnd.alexa.handler;

import static com.ibm.rnd.alexa.handler.HandlerInputs.ssml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.amazon.ask.model.Response;

class SkillReplyAndSupportTest {

    // ---- SkillReply invariants

    @Test
    void openSessionRequiresReprompt() {
        assertThatThrownBy(() -> SkillReply.ask("hi", " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void speechMustNotBeBlank() {
        assertThatThrownBy(() -> SkillReply.tell("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cannotElicitSlotWhenSessionEnds() {
        assertThatThrownBy(() -> SkillReply.tell("bye").elicitingSlot("x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replyIsImmutableAndCopiesAttributes() {
        SkillReply base = SkillReply.tell("bye");
        SkillReply changed = base.withSessionAttribute("k", "v");

        assertThat(base.sessionAttributes()).isEmpty();
        assertThat(changed.sessionAttributes()).containsEntry("k", "v");
    }

    // ---- Help is assembled from HelpTopic beans

    @Test
    void helpListsEveryRegisteredTopic() {
        var help = new HelpIntentHandler(List.of(() -> "order a drink", () -> "say hello"));

        Response response = help.handle(HandlerInputs.intent("AMAZON.HelpIntent", Map.of())).orElseThrow();

        assertThat(ssml(response)).startsWith("You can order a drink or say hello.");
        assertThat(response.getShouldEndSession()).isFalse();
        assertThat(HandlerInputs.repromptSsml(response)).isEqualTo(HelpIntentHandler.REPROMPT);
    }

    @Test
    void helpCopesWithNoTopics() {
        var help = new HelpIntentHandler(List.of());

        assertThat(ssml(help.handle(HandlerInputs.intent("AMAZON.HelpIntent", Map.of())).orElseThrow()))
                .startsWith("You can nothing yet.");
    }

    // ---- Ssml

    @Test
    void ssmlEscapesAllSpecialCharacters() {
        assertThat(Ssml.escape("a & b < c > \"d\" 'e'")).isEqualTo("a &amp; b &lt; c &gt; &quot;d&quot; &apos;e&apos;");
        assertThat(Ssml.escape(null)).isEmpty();
    }

    @Test
    void joinWithOrFormatsLists() {
        assertThat(Ssml.joinWithOr(List.of())).isEmpty();
        assertThat(Ssml.joinWithOr(List.of("a"))).isEqualTo("a");
        assertThat(Ssml.joinWithOr(List.of("a", "b"))).isEqualTo("a or b");
        assertThat(Ssml.joinWithOr(List.of("a", "b", "c"))).isEqualTo("a, b, or c");
    }

    // ---- Sessions

    @Test
    void sessionHelpersAreSafeOutOfSession() {
        var input = HandlerInputs.intentWithoutSession("X", Map.of());

        assertThat(Sessions.isActive(input)).isFalse();
        assertThat(Sessions.attributes(input)).isEmpty();
        assertThat(Sessions.count(input, "n")).isZero();
    }

    @Test
    void sessionCountIgnoresNonNumbers() {
        var input = HandlerInputs.intent("X", Map.of(), Map.of("n", "oops", "m", 4L));

        assertThat(Sessions.count(input, "n")).isZero();
        assertThat(Sessions.count(input, "m")).isEqualTo(4);
    }

    // ---- Error handler

    @Test
    void errorHandlerApologisesWithoutLeakingDetails() {
        var handler = new GenericErrorHandler();
        var input = HandlerInputs.launch();
        var failure = new IllegalStateException("secret internal detail");

        assertThat(handler.canHandle(input, failure)).isTrue();
        Response response = handler.handle(input, failure).orElseThrow();

        assertThat(ssml(response)).isEqualTo(GenericErrorHandler.APOLOGY).doesNotContain("secret");
        assertThat(response.getShouldEndSession()).isFalse();
    }
}
