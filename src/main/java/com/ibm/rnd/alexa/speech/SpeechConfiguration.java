package com.ibm.rnd.alexa.speech;

import java.net.http.HttpClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;

/** Wires the local speech recognition endpoint; nothing here is created unless it is switched on. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpeechProperties.class)
@ConditionalOnProperty(prefix = "assistant.speech", name = "enabled", havingValue = "true")
class SpeechConfiguration {

    @Bean
    SpeechRecognizer speechRecognizer(SpeechProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(2))
                .build();
        return new HttpSpeechRecognizer(client, properties.serviceUrl(), properties.timeout());
    }

    @Bean
    SpeechService speechService(SpeechRecognizer recognizer, SpeechProperties properties, MeterRegistry registry) {
        return new SpeechService(recognizer, properties, registry);
    }
}
