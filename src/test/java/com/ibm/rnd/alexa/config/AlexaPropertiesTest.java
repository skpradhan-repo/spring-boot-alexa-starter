package com.ibm.rnd.alexa.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** Startup safety: verification without a skill id would silently accept any skill's requests. */
class AlexaPropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static AlexaProperties props(String skillId, boolean verify) {
        return new AlexaProperties(skillId, new AlexaProperties.Verification(verify, java.time.Duration.ofSeconds(150)));
    }

    @Test
    void verificationWithoutSkillIdIsRejected() {
        Set<ConstraintViolation<AlexaProperties>> violations = validator.validate(props("", true));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("ALEXA_SKILL_ID");
    }

    @Test
    void verificationWithSkillIdIsAccepted() {
        assertThat(validator.validate(props("amzn1.ask.skill.x", true))).isEmpty();
    }

    @Test
    void missingSkillIdIsAcceptableWhileVerificationIsOff() {
        assertThat(validator.validate(props("", false))).isEmpty();
    }

    @Test
    void nonPositiveTimestampToleranceIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new AlexaProperties.Verification(true, java.time.Duration.ZERO));
    }
}
