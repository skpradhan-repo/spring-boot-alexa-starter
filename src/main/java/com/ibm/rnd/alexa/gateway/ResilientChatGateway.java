package com.ibm.rnd.alexa.gateway;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.ibm.rnd.alexa.exception.ModelUnavailableException;
import com.ibm.rnd.alexa.exception.ModelUnavailableException.Reason;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Decorator that protects the model (and the callers) around any {@link ChatGateway}:
 * <ul>
 *   <li><b>Bulkhead</b> - at most {@code maxConcurrent} calls run at once; extra callers wait up to
 *       {@code queueTimeout} and are then rejected as {@link Reason#OVERLOADED} (graceful backpressure).</li>
 *   <li><b>Timeout</b> - a call exceeding {@code requestTimeout} fails with {@link Reason#TIMEOUT}.</li>
 *   <li><b>Circuit breaker</b> - repeated failures stop traffic to a sick provider so the application
 *       fails fast instead of piling up slow requests.</li>
 * </ul>
 * Everything is expressed on the streaming path; {@link #complete} reuses it so both share one policy.
 */
public class ResilientChatGateway implements ChatGateway {

    private final ChatGateway delegate;
    private final CircuitBreaker breaker;
    private final Semaphore slots;
    private final Duration queueTimeout;
    private final Duration requestTimeout;

    public ResilientChatGateway(ChatGateway delegate, CircuitBreaker breaker, GatewayProperties properties) {
        this.delegate = delegate;
        this.breaker = breaker;
        this.slots = new Semaphore(properties.maxConcurrent(), true);
        this.queueTimeout = properties.queueTimeout();
        this.requestTimeout = properties.requestTimeout();
    }

    @Override
    public String complete(ChatPrompt prompt) {
        String text = stream(prompt).collect(StringBuilder::new, StringBuilder::append).map(StringBuilder::toString)
                .block();
        return text == null ? "" : text.strip();
    }

    @Override
    public Flux<String> stream(ChatPrompt prompt) {
        return Mono.fromCallable(this::acquireSlot)
                .subscribeOn(Schedulers.boundedElastic()) // waiting for a slot must not block a caller thread
                .flatMapMany(ignored -> guarded(prompt).doFinally(signal -> slots.release()));
    }

    /** Applies breaker accounting and the hard timeout to one provider call. */
    private Flux<String> guarded(ChatPrompt prompt) {
        return Flux.defer(() -> {
            if (!breaker.tryAcquirePermission()) {
                return Flux.error(new ModelUnavailableException(Reason.UNAVAILABLE,
                        CallNotPermittedException.createCallNotPermittedException(breaker)));
            }
            long start = System.nanoTime();
            return delegate.stream(prompt)
                    .timeout(requestTimeout)
                    .doOnComplete(() -> breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS))
                    .doOnError(e -> breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e))
                    .doOnCancel(breaker::releasePermission)
                    .onErrorMap(TimeoutException.class, e -> new ModelUnavailableException(Reason.TIMEOUT, e))
                    .onErrorMap(e -> !(e instanceof ModelUnavailableException),
                            e -> new ModelUnavailableException(Reason.UNAVAILABLE, e));
        });
    }

    private Boolean acquireSlot() throws InterruptedException {
        if (!slots.tryAcquire(queueTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new ModelUnavailableException(Reason.OVERLOADED);
        }
        return Boolean.TRUE;
    }

    /** Number of free inference slots; exposed for metrics. */
    public int availableSlots() {
        return slots.availablePermits();
    }
}
