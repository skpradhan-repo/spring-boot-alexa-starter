package com.ibm.rnd.alexa.speech;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.rnd.alexa.exception.SpeechUnavailableException;

/**
 * Talks to the Python recognition service over loopback HTTP. The base URL is validated to be local in
 * {@link SpeechProperties}, so recordings cannot be sent to another host by configuration mistake.
 */
class HttpSpeechRecognizer implements SpeechRecognizer {

    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_TEXT_CHARS = 5000;

    private final HttpClient client;
    private final URI base;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();

    HttpSpeechRecognizer(HttpClient client, String serviceUrl, Duration timeout) {
        this.client = client;
        this.base = URI.create(serviceUrl.endsWith("/") ? serviceUrl : serviceUrl + "/");
        this.timeout = timeout;
    }

    @Override
    public Status status() {
        try {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(base.resolve("health")).timeout(STATUS_TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return new Status("error", List.of());
            }
            JsonNode body = mapper.readTree(response.body());
            List<String> languages = new java.util.ArrayList<>();
            body.path("languages").forEach(node -> languages.add(node.asText()));
            return new Status(body.path("status").asText("error"), List.copyOf(languages));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Status("unreachable", List.of());
        } catch (IOException | RuntimeException e) {
            return new Status("unreachable", List.of());
        }
    }

    @Override
    public String transcribe(byte[] wav, String lang) {
        URI uri = base.resolve("transcribe?lang=" + URLEncoder.encode(lang, StandardCharsets.UTF_8));
        try {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(timeout)
                            .header("Content-Type", "audio/wav")
                            .POST(HttpRequest.BodyPublishers.ofByteArray(wav)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new SpeechUnavailableException("Speech service answered HTTP " + response.statusCode());
            }
            String text = mapper.readTree(response.body()).path("text").asText("").strip();
            return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechUnavailableException("Interrupted while waiting for the speech service", e);
        } catch (IOException e) {
            throw new SpeechUnavailableException("Speech service is not reachable", e);
        }
    }
}
