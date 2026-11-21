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
    void otherIndianAndMajorScriptsAreDetected() {
        assertThat(detector.detect("\u09B8\u09AC \u0995\u09B0\u09CD\u09AE\u0995\u09B0\u09CD\u09A4\u09BE\u09B0 \u099B\u09C1\u099F\u09BF")).isEqualTo("bn");
        assertThat(detector.detect("\u0B85\u0BA9\u0BC8\u0BA4\u0BCD\u0BA4\u0BC1 \u0BAA\u0BA3\u0BBF\u0BAF\u0BBE\u0BB3\u0BB0\u0BCD\u0B95\u0BB3\u0BC1\u0B95\u0BCD\u0B95\u0BC1\u0BAE\u0BCD")).isEqualTo("ta");
        assertThat(detector.detect("\u0C05\u0C28\u0C4D\u0C28\u0C3F \u0C09\u0C26\u0C4D\u0C2F\u0C4B\u0C17\u0C41\u0C32\u0C15\u0C41")).isEqualTo("te");
        assertThat(detector.detect("\u0645\u0648\u0638\u0641\u0648\u0646 \u0625\u062C\u0627\u0632\u0629 \u0633\u0646\u0648\u064A\u0629")).isEqualTo("ar");
        assertThat(detector.detect("\u0421\u043E\u0442\u0440\u0443\u0434\u043D\u0438\u043A\u0438 \u043F\u043E\u043B\u0443\u0447\u0430\u044E\u0442 \u043E\u0442\u043F\u0443\u0441\u043A")).isEqualTo("ru");
    }

    @Test
    void textWithNoLettersFallsBackToEnglish() {
        assertThat(detector.detect("12345 !!! ---")).isEqualTo("en");
    }
}
