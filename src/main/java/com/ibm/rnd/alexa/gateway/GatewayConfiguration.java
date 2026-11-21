package com.ibm.rnd.alexa.gateway;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.MeterRegistry;

/** Assembles the model gateway: Spring AI adapter wrapped by the resilience decorator, plus metrics. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfiguration {

    @Bean
    CircuitBreaker modelCircuitBreaker(GatewayProperties properties) {
        GatewayProperties.CircuitBreaker cb = properties.circuitBreaker();
        return CircuitBreaker.of("model", CircuitBreakerConfig.custom()
                .failureRateThreshold(cb.failureRateThreshold())
                .minimumNumberOfCalls(cb.minimumCalls())
                .slidingWindowSize(Math.max(cb.minimumCalls() * 2, 10))
                .waitDurationInOpenState(cb.openDuration())
                .permittedNumberOfCallsInHalfOpenState(1)
                .build());
    }

    @Bean
    ResilientChatGateway chatGateway(ChatModel chatModel, CircuitBreaker modelCircuitBreaker,
            GatewayProperties properties, MeterRegistry registry) {
        ResilientChatGateway gateway = new ResilientChatGateway(new SpringAiChatGateway(chatModel),
                modelCircuitBreaker, properties);
        registry.gauge("assistant.gateway.slots.available", gateway, ResilientChatGateway::availableSlots);
        registry.gauge("assistant.gateway.circuit.state", modelCircuitBreaker, cb -> cb.getState().getOrder());
        return gateway;
    }
}
