package com.ibm.rnd.alexa.gateway;

/**
 * Provider-neutral request to the language model.
 *
 * @param system instruction text, trusted (written by us)
 * @param user   the end user's message, untrusted
 * @param maxTokens upper bound on generated tokens; keeps spoken answers short and latency bounded
 */
public record ChatPrompt(String system, String user, int maxTokens) {

    public ChatPrompt {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user message must not be blank");
        }
        system = system == null ? "" : system;
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
    }
}
