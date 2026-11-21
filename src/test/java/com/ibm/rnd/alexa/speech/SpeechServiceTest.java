package com.ibm.rnd.alexa.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.ibm.rnd.alexa.exception.InvalidAudioException;
import com.ibm.rnd.alexa.exception.SpeechUnavailableException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class SpeechServiceTest {

    private static final SpeechProperties PROPS = new SpeechProperties(true, "http://127.0.0.1:8090", 1_000_000, Duration.ofSeconds(5));
    private static final byte[] WAV = WavValidatorTest.wav(16000, 1, 16, 1.0);

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private SpeechService service(SpeechRecognizer recognizer) {
        return new SpeechService(recognizer, PROPS, registry);
    }

    private static SpeechRecognizer returning(String text) {
        return new SpeechRecognizer() {
            @Override
            public Status status() {
                return new Status("ready", List.of("or"));
            }

            @Override
            public String transcribe(byte[] wav, String lang) {
                return text;
            }
        };
    }

    @Test
    void returnsTheRecognisedTextAndCountsIt() {
        assertThat(service(returning("hello")).transcribe(WAV, "or")).isEqualTo("hello");
        assertThat(registry.counter("assistant.speech.transcriptions", "outcome", "ok").count()).isEqualTo(1.0);
    }

    @Test
    void emptyTextIsCountedAsEmptyNotAnError() {
        assertThat(service(returning("")).transcribe(WAV, "or")).isEmpty();
        assertThat(registry.counter("assistant.speech.transcriptions", "outcome", "empty").count()).isEqualTo(1.0);
    }

    @Test
    void anUnsupportedLanguageIsRejectedBeforeTheRecogniserIsCalled() {
        SpeechRecognizer neverCalled = new SpeechRecognizer() {
            @Override
            public Status status() {
                throw new AssertionError();
            }

            @Override
            public String transcribe(byte[] wav, String lang) {
                throw new AssertionError("must not be called");
            }
        };
        assertThatThrownBy(() -> service(neverCalled).transcribe(WAV, "en"))
                .isInstanceOf(InvalidAudioException.class).hasMessageContaining("language");
    }

    @Test
    void badAudioIsRejectedBeforeTheRecogniserIsCalled() {
        assertThatThrownBy(() -> service(returning("x")).transcribe(new byte[10], "or")).isInstanceOf(InvalidAudioException.class);
    }

    @Test
    void aSecondRecordingWhileOneIsRunningFailsFastInsteadOfQueueing() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SpeechRecognizer slow = new SpeechRecognizer() {
            @Override
            public Status status() {
                return new Status("ready", List.of());
            }

            @Override
            public String transcribe(byte[] wav, String lang) {
                inside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "first";
            }
        };
        SpeechService service = service(slow);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = pool.submit(() -> service.transcribe(WAV, "or"));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.transcribe(WAV, "or"))
                    .isInstanceOf(SpeechUnavailableException.class).hasMessageContaining("being processed");
            assertThat(registry.counter("assistant.speech.transcriptions", "outcome", "busy").count()).isEqualTo(1.0);

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(service.transcribe(WAV, "or")).isEqualTo("first");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aFailingRecogniserReleasesTheSlot() {
        SpeechRecognizer failing = new SpeechRecognizer() {
            @Override
            public Status status() {
                return new Status("ready", List.of());
            }

            @Override
            public String transcribe(byte[] wav, String lang) {
                throw new SpeechUnavailableException("down");
            }
        };
        SpeechService service = service(failing);
        assertThatThrownBy(() -> service.transcribe(WAV, "or")).isInstanceOf(SpeechUnavailableException.class).hasMessage("down");
        assertThatThrownBy(() -> service.transcribe(WAV, "or")).hasMessage("down");
        assertThat(registry.counter("assistant.speech.transcriptions", "outcome", "unavailable").count()).isEqualTo(2.0);
    }
}
