package com.ibm.rnd.alexa.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LanguageDetectorTest {

    private final LanguageDetector detector = new LanguageDetector();

    @Test
    void englishIsDetected() {
        assertThat(detector.detect("Employees receive twenty days of annual leave.")).isEqualTo("en");
    }

    @Test
    void hindiIsDetected() {
        assertThat(detector.detect("\u0915\u0930\u094D\u092E\u091A\u093E\u0930\u093F\u092F\u094B\u0902 \u0915\u094B \u0935\u093E\u0930\u094D\u0937\u093F\u0915 \u0905\u0935\u0915\u093E\u0936 \u092E\u093F\u0932\u0924\u093E \u0939\u0948\u0964"))
                .isEqualTo("hi");
    }

    @Test
    void odiaIsDetected() {
        assertThat(detector.detect("\u0B15\u0B30\u0B4D\u0B2E\u0B1A\u0B3E\u0B30\u0B40\u0B59\u0B4D\u0B15\u0B41 \u0B2C\u0B3E\u0B30\u0B4D\u0B37\u0B3F\u0B15 \u0B1B\u0B41\u0B1F\u0B3F \u0B2E\u0B3F\u0B33\u0B47\u0964"))
                .isEqualTo("or");
    }

    @Test
    void dominantScriptWinsInMixedText() {
        assertThat(detector.detect("HR policy \u0915\u0930\u094D\u092E\u091A\u093E\u0930\u093F\u092F\u094B\u0902 \u0915\u094B \u0935\u093E\u0930\u094D\u0937\u093F\u0915 \u0905\u0935\u0915\u093E\u0936 \u092E\u093F\u0932\u0924\u093E \u0939\u0948"))
                .isEqualTo("hi");
    }

    @Test
    void textWithNoLettersFallsBackToEnglish() {
        assertThat(detector.detect("12345 !!! ---")).isEqualTo("en");
    }
}
