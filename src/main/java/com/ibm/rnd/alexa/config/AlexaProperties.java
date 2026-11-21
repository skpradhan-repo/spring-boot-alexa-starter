package com.ibm.rnd.alexa.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Type-safe settings for the Alexa integration (prefix {@code alexa}).
 *
 * @param skillId      expected skill (application) id; blank disables the check
 * @param verification Alexa request verification settings
 */
@ConfigurationProperties(prefix = "alexa")
public record AlexaProperties(
        @DefaultValue("") String skillId,
        @DefaultValue Verification verification) {

    /**
     * @param enabled            whether signature and timestamp verification is enforced
     * @param timestampTolerance maximum accepted age of a request
     */
    public record Verification(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("150s") Duration timestampTolerance) {

        public Verification {
            if (timestampTolerance == null || timestampTolerance.isZero() || timestampTolerance.isNegative()) {
                throw new IllegalArgumentException("alexa.verification.timestamp-tolerance must be positive");
            }
        }
    }

    public boolean hasSkillId() {
        return skillId != null && !skillId.isBlank();
    }
}
