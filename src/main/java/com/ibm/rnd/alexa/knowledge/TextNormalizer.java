package com.ibm.rnd.alexa.knowledge;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Cleans extracted text before chunking. Unicode NFC matters for Hindi and Odia: the same visible
 * word can be encoded several ways, and without a canonical form identical words would neither match
 * in keyword search nor embed identically.
 */
final class TextNormalizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}&&[^\\n\\t]]");
    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[ \\t\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]+");
    private static final Pattern SPACE_AROUND_NEWLINE = Pattern.compile(" *\\n *");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        // ZWJ/ZWNJ (U+200D/U+200C) are deliberately kept: they change how Indic conjuncts render.
        String text = Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace("\r\n", "\n").replace('\r', '\n').replace('\u2028', '\n').replace('\u2029', '\n')
                .replace("\u00AD", "").replace("\uFEFF", "");
        // NUL and other control characters break PostgreSQL text columns and carry no meaning.
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = HORIZONTAL_SPACE.matcher(text).replaceAll(" ");
        text = SPACE_AROUND_NEWLINE.matcher(text).replaceAll("\n");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }
}
