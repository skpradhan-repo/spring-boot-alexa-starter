package com.ibm.rnd.alexa.gateway;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reports whether Ollama is reachable. Informational only: it never gates the Alexa webhook, so the
 * deterministic intents keep working when the model is down. A missing model is reported as
 * {@code DEGRADED} (still HTTP 200), not {@code DOWN}, so monitors do not treat the whole app as dead.
 */
@Component("model")
class ModelHealthIndicator implements HealthIndicator {

    static final Status DEGRADED = new Status("DEGRADED", "Language model unreachable; deterministic features still work");

    private final RestClient client;

    ModelHealthIndicator(@Value("${spring.ai.ollama.base-url:http://127.0.0.1:11434}") String baseUrl) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public Health health() {
        try {
            client.get().uri("/api/version").retrieve().toBodilessEntity();
            return Health.up().build();
        } catch (RuntimeException e) {
            // Detail stays out of the response; actuator exposes health publicly.
            return Health.status(DEGRADED).build();
        }
    }
}
