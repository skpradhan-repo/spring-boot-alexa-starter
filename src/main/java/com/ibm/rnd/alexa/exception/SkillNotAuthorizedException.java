package com.ibm.rnd.alexa.exception;

/**
 * The request is authentic but addressed to a different skill than the one this service is configured for.
 * Distinct from a plain {@link SecurityException} (failed signature or timestamp) so it maps to 403, not 401.
 */
public class SkillNotAuthorizedException extends SecurityException {

    public SkillNotAuthorizedException(String message) {
        super(message);
    }
}
