package com.ibm.rnd.alexa.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ibm.rnd.alexa.exception.SpeechUnavailableException;
import com.sun.net.httpserver.HttpServer;

/** The client against a real loopback HTTP server that stands in for the Python service. */
class HttpSpeechRecognizerTest {

    private static final String ODIA = "\u0B15\u0B30\u0B4D\u0B2E\u0B1A\u0B3E\u0B30\u0B40\u0B19\u0B4D\u0B15 \u0B1B\u0B41\u0B1F\u0B3F";

    private HttpServer server;
    private final AtomicReference<String> seenQuery = new AtomicReference<>();
    private final AtomicReference<String> seenContentType = new AtomicReference<>();
    private volatile String healthBody = "{\"status\":\"ready\",\"languages\":[\"or\",\"hi\"]}";
    private volatile int transcribeStatus = 200;
    private volatile String transcribeBody = "{\"text\":\"" + ODIA + "\"}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> reply(exchange, 200, healthBody));
        server.createContext("/transcribe", exchange -> {
            seenQuery.set(exchange.getRequestURI().getRawQuery());
            seenContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exchange.getRequestBody().readAllBytes();
            reply(exchange, transcribeStatus, transcribeBody);
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private HttpSpeechRecognizer client() {
        return new HttpSpeechRecognizer(HttpClient.newHttpClient(), "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(5));
    }

    @Test
    void readsReadyStatusAndLanguages() {
        SpeechRecognizer.Status status = client().status();
        assertThat(status.ready()).isTrue();
        assertThat(status.languages()).containsExactly("or", "hi");
    }

    @Test
    void reportsLoadingAndMissingModelAsNotReady() {
        healthBody = "{\"status\":\"model_missing\",\"languages\":[]}";
        SpeechRecognizer.Status status = client().status();
        assertThat(status.ready()).isFalse();
        assertThat(status.state()).isEqualTo("model_missing");
    }

    @Test
    void anUnreachableServiceIsAStatusNotAnException() {
        server.stop(0);
        SpeechRecognizer.Status status = client().status();
        assertThat(status.ready()).isFalse();
        assertThat(status.state()).isEqualTo("unreachable");
    }

    @Test
    void sendsAudioAndReturnsUnicodeTextUnchanged() {
        String text = client().transcribe(new byte[] {1, 2, 3}, "or");
        assertThat(text).isEqualTo(ODIA);
        assertThat(seenQuery.get()).isEqualTo("lang=or");
        assertThat(seenContentType.get()).isEqualTo("audio/wav");
    }

    @Test
    void aFailingServiceBecomesSpeechUnavailable() {
        transcribeStatus = 503;
        assertThatThrownBy(() -> client().transcribe(new byte[] {1}, "or"))
                .isInstanceOf(SpeechUnavailableException.class).hasMessageContaining("503");
    }

    @Test
    void anUnreachableServiceOnTranscribeBecomesSpeechUnavailable() {
        server.stop(0);
        assertThatThrownBy(() -> client().transcribe(new byte[] {1}, "or")).isInstanceOf(SpeechUnavailableException.class);
    }

    @Test
    void propertiesRefuseARemoteServiceUrl() {
        assertThatThrownBy(() -> new SpeechProperties(true, "https://speech.example.com", 1_000_000, Duration.ofSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("this machine");
        new SpeechProperties(true, "http://localhost:8090", 1_000_000, Duration.ofSeconds(60));
        new SpeechProperties(true, "http://127.0.0.1:8090", 1_000_000, Duration.ofSeconds(60));
    }
}
