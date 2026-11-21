package com.ibm.rnd.alexa.exception;

/**
 * The language model could not serve a request. Carries only a coarse {@link Reason}: provider error
 * details stay in the server log and never reach clients.
 */
public class ModelUnavailableException extends RuntimeException {

    public enum Reason {
        /** Provider down, unreachable, or the circuit breaker is open. */
        UNAVAILABLE,
        /** All inference slots are busy and the wait budget was exhausted. */
        OVERLOADED,
        /** The provider did not answer within the configured time limit. */
        TIMEOUT
    }

    private final Reason reason;

    public ModelUnavailableException(Reason reason, Throwable cause) {
        super("Language model unavailable: " + reason, cause);
        this.reason = reason;
    }

    public ModelUnavailableException(Reason reason) {
        this(reason, null);
    }

    public Reason reason() {
        return reason;
    }
}
