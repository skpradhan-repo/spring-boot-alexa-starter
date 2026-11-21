package com.ibm.rnd.alexa.gateway;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Inference limits (prefix {@code assistant.gateway}). Defaults are sized for a CPU-only laptop:
 * more parallel requests make every response slower, so concurrency is deliberately low.
 *
 * @param maxConcurrent  simultaneous model calls
 * @param queueTimeout   how long a request may wait for a free slot before it is rejected as overloaded
 * @param requestTimeout hard limit for one model call
 * @param circuitBreaker failure-isolation settings
 */
@Validated
@ConfigurationProperties(prefix = "assistant.gateway")
public record GatewayProperties(
        @Min(1) @Max(16) @DefaultValue("2") int maxConcurrent,
        @NotNull @DefaultValue("2s") Duration queueTimeout,
        @NotNull @DefaultValue("30s") Duration requestTimeout,
        @Valid @DefaultValue CircuitBreaker circuitBreaker) {

    /**
     * @param failureRateThreshold percentage of failed calls that opens the breaker
     * @param minimumCalls         calls observed before the failure rate is evaluated
     * @param openDuration         how long the breaker stays open before probing the provider again
     */
    public record CircuitBreaker(
            @Min(1) @Max(100) @DefaultValue("50") int failureRateThreshold,
            @Min(1) @DefaultValue("4") int minimumCalls,
            @NotNull @DefaultValue("20s") Duration openDuration) {
    }
}
