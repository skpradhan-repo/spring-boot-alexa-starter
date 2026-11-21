package com.ibm.rnd.alexa.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.ibm.rnd.alexa.exception.ModelUnavailableException;
import com.ibm.rnd.alexa.exception.ModelUnavailableException.Reason;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import reactor.core.publisher.Flux;

class ResilientChatGatewayTest {

    private static final ChatPrompt PROMPT = new ChatPrompt("sys", "hello", 50);

    private static GatewayProperties props(int concurrent, Duration queue, Duration timeout) {
        return new GatewayProperties(concurrent, queue, timeout,
                new GatewayProperties.CircuitBreaker(50, 2, Duration.ofSeconds(30)));
    }

    private static CircuitBreaker breaker() {
        return CircuitBreaker.of("t", CircuitBreakerConfig.custom()
                .failureRateThreshold(50).minimumNumberOfCalls(2).slidingWindowSize(4)
                .waitDurationInOpenState(Duration.ofSeconds(30)).build());
    }

    private static ChatGateway answering(String text) {
        return new ChatGateway() {
            @Override public String complete(ChatPrompt p) { return text; }
            @Override public Flux<String> stream(ChatPrompt p) { return Flux.just(text.split("(?<= )")); }
        };
    }

    private static ChatGateway failing() {
        return new ChatGateway() {
            @Override public String complete(ChatPrompt p) { throw new IllegalStateException("boom: secret-host:11434"); }
            @Override public Flux<String> stream(ChatPrompt p) { return Flux.error(new IllegalStateException("boom: secret-host:11434")); }
        };
    }

    @Test
    void passesThroughSuccessfulAnswers() {
        var gateway = new ResilientChatGateway(answering("Hello there friend"), breaker(),
                props(2, Duration.ofSeconds(1), Duration.ofSeconds(5)));

        assertThat(gateway.complete(PROMPT)).isEqualTo("Hello there friend");
        assertThat(gateway.stream(PROMPT).collectList().block()).containsExactly("Hello ", "there ", "friend");
        assertThat(gateway.availableSlots()).isEqualTo(2);
    }

    @Test
    void providerFailureBecomesUnavailableWithoutLeakingDetail() {
        var gateway = new ResilientChatGateway(failing(), breaker(),
                props(2, Duration.ofSeconds(1), Duration.ofSeconds(5)));

        assertThatThrownBy(() -> gateway.complete(PROMPT))
                .isInstanceOfSatisfying(ModelUnavailableException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE);
                    assertThat(e.getMessage()).doesNotContain("secret-host");
                });
        assertThat(gateway.availableSlots()).isEqualTo(2);
    }

    @Test
    void slowProviderTimesOut() {
        ChatGateway slow = new ChatGateway() {
            @Override public String complete(ChatPrompt p) { return ""; }
            @Override public Flux<String> stream(ChatPrompt p) { return Flux.never(); }
        };
        var gateway = new ResilientChatGateway(slow, breaker(),
                props(1, Duration.ofSeconds(1), Duration.ofMillis(200)));

        assertThatThrownBy(() -> gateway.complete(PROMPT))
                .isInstanceOfSatisfying(ModelUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.TIMEOUT));
        assertThat(gateway.availableSlots()).isEqualTo(1);
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndStopsCallingTheProvider() {
        AtomicInteger calls = new AtomicInteger();
        ChatGateway counting = new ChatGateway() {
            @Override public String complete(ChatPrompt p) { return ""; }
            @Override public Flux<String> stream(ChatPrompt p) {
                calls.incrementAndGet();
                return Flux.error(new IllegalStateException("down"));
            }
        };
        CircuitBreaker breaker = breaker();
        var gateway = new ResilientChatGateway(counting, breaker,
                props(2, Duration.ofSeconds(1), Duration.ofSeconds(5)));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> gateway.complete(PROMPT)).isInstanceOf(ModelUnavailableException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int callsBeforeOpenRejection = calls.get();
        assertThatThrownBy(() -> gateway.complete(PROMPT))
                .isInstanceOfSatisfying(ModelUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
        assertThat(calls.get()).as("an open circuit must not reach the provider").isEqualTo(callsBeforeOpenRejection);
    }

    @Test
    void rejectsAsOverloadedWhenAllSlotsStayBusy() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ChatGateway blocking = new ChatGateway() {
            @Override public String complete(ChatPrompt p) { return ""; }
            @Override public Flux<String> stream(ChatPrompt p) {
                return Flux.defer(() -> {
                    try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    return Flux.just("done");
                }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
            }
        };
        var gateway = new ResilientChatGateway(blocking, breaker(),
                props(1, Duration.ofMillis(150), Duration.ofSeconds(10)));

        Thread first = Thread.ofVirtual().start(() -> gateway.complete(PROMPT));
        while (gateway.availableSlots() != 0) {
            Thread.sleep(10);
        }

        assertThatThrownBy(() -> gateway.complete(PROMPT))
                .isInstanceOfSatisfying(ModelUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.OVERLOADED));

        release.countDown();
        first.join();
        assertThat(gateway.availableSlots()).isEqualTo(1);
    }

    @Test
    void chatPromptRejectsBlankUserMessageAndBadLimit() {
        assertThatThrownBy(() -> new ChatPrompt("s", "  ", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatPrompt("s", "hi", 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
