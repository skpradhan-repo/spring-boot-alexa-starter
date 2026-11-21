package com.ibm.rnd.alexa.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

/**
 * Type-safe settings for the Alexa integration (prefix {@code alexa}). Validated at startup: the
 * application refuses to start with verification enabled but no skill id, instead of silently
 * accepting requests addressed to any skill.
 *
 * @param skillId      expected skill (application) id; may be blank only while verification is disabled
 * @param verification Alexa request verification settings
 */
@Validated
@ConfigurationProperties(prefix = "alexa")
public record AlexaProperties(
        @DefaultValue("") String skillId,
        @Valid @DefaultValue Verification verification) {

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

    @AssertTrue(message = "alexa.skill-id (ALEXA_SKILL_ID) must be set while alexa.verification.enabled=true")
    public boolean isSkillIdConfiguredWhenVerifying() {
        return !verification.enabled() || hasSkillId();
    }
}
