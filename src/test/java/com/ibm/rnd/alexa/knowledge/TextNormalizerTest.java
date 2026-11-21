package com.ibm.rnd.alexa.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void collapsesWhitespaceAndBlankLines() {
        assertThat(normalizer.normalize("  Hello   world \r\n\r\n\r\n\r\nNext\tline  "))
                .isEqualTo("Hello world\n\nNext line");
    }

    @Test
    void removesNulAndControlCharactersButKeepsNewlines() {
        assertThat(normalizer.normalize("a\u0000b\u0007c\nd")).isEqualTo("abc\nd");
    }

    @Test
    void unifiesUnicodeForms() {
        // "e" + combining acute vs precomposed "e-acute": must end up identical.
        assertThat(normalizer.normalize("cafe\u0301")).isEqualTo(normalizer.normalize("caf\u00e9"));
    }

    @Test
    void keepsZeroWidthJoinersThatShapeIndicText() {
        String withZwj = "\u0915\u094D\u200D\u0937";
        assertThat(normalizer.normalize(withZwj)).contains("\u200D");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(normalizer.normalize(null)).isEmpty();
        assertThat(normalizer.normalize("")).isEmpty();
        assertThat(normalizer.normalize("   \n\n ")).isEmpty();
    }
}
