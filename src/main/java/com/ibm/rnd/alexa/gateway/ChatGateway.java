package com.ibm.rnd.alexa.gateway;

import reactor.core.publisher.Flux;

/**
 * Port to a language model. The rest of the application depends on this interface only, so the
 * provider (Ollama today) can be swapped or extracted into its own service without touching callers
 * (Dependency Inversion).
 */
public interface ChatGateway {

    /** Blocking completion. @throws com.ibm.rnd.alexa.exception.ModelUnavailableException if the model cannot serve it */
    String complete(ChatPrompt prompt);

    /** Token stream. Errors are signalled as {@link com.ibm.rnd.alexa.exception.ModelUnavailableException}. */
    Flux<String> stream(ChatPrompt prompt);
}
