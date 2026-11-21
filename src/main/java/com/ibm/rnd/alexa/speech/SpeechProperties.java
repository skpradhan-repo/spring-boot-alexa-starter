package com.ibm.rnd.alexa.speech;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Local speech recognition settings (prefix {@code assistant.speech}).
 *
 * @param enabled       exposes {@code /api/speech}; off by default because it needs the separate Python service
 * @param serviceUrl    where that service listens; it must be a loopback address, audio never leaves the machine
 * @param maxAudioBytes largest accepted recording (16 kHz mono 16-bit is about 32 KB per second)
 * @param timeout       how long one transcription may take; a CPU needs several seconds for a short clip
 */
@Validated
@ConfigurationProperties(prefix = "assistant.speech")
public record SpeechProperties(
        @DefaultValue("false") boolean enabled,
        @NotBlank @DefaultValue("http://127.0.0.1:8090") String serviceUrl,
        @Min(32_000) @Max(10_000_000) @DefaultValue("1000000") int maxAudioBytes,
        @NotNull @DefaultValue("60s") Duration timeout) {

    public SpeechProperties {
        java.net.URI uri = java.net.URI.create(serviceUrl);
        String host = uri.getHost();
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host) && !"[::1]".equals(host)) {
            throw new IllegalArgumentException(
                    "assistant.speech.service-url must point to this machine (127.0.0.1 or localhost): recordings must not leave it");
        }
    }
}
