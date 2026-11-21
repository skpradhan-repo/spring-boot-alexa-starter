package com.ibm.rnd.alexa.knowledge;

import java.lang.Character.UnicodeScript;
import java.util.EnumMap;
import java.util.Map;

/**
 * Picks a language code from the dominant Unicode script. Cheap, deterministic and offline. It cannot
 * separate languages that share a script (Hindi vs Marathi both use Devanagari, Spanish vs English both use
 * Latin), which is acceptable here: the value is only a filter/label, and the embedding model (bge-m3) is
 * multilingual either way.
 */
final class LanguageDetector {

    private static final String DEFAULT = "en";
    private static final int SAMPLE_LETTERS = 5000;

    /** Script to the language code used when that script dominates. Latin is the tie-break default. */
    private static final Map<UnicodeScript, String> LANGUAGE_BY_SCRIPT = new EnumMap<>(UnicodeScript.class);

    static {
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.LATIN, DEFAULT);
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.DEVANAGARI, "hi");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.ORIYA, "or");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.BENGALI, "bn");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.TAMIL, "ta");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.TELUGU, "te");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.KANNADA, "kn");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.MALAYALAM, "ml");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.GUJARATI, "gu");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.GURMUKHI, "pa");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.ARABIC, "ar");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.CYRILLIC, "ru");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.HAN, "zh");
        LANGUAGE_BY_SCRIPT.put(UnicodeScript.HANGUL, "ko");
    }

    String detect(String text) {
        Map<UnicodeScript, Integer> counts = new EnumMap<>(UnicodeScript.class);
        int sampled = 0;
        for (int i = 0; i < text.length() && sampled < SAMPLE_LETTERS; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            sampled++;
            UnicodeScript script = UnicodeScript.of(cp);
            if (LANGUAGE_BY_SCRIPT.containsKey(script)) {
                counts.merge(script, 1, Integer::sum);
            }
        }

        // Latin wins ties, so mixed or unknown text keeps the previous default of English.
        UnicodeScript best = UnicodeScript.LATIN;
        int bestCount = counts.getOrDefault(best, 0);
        for (Map.Entry<UnicodeScript, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return LANGUAGE_BY_SCRIPT.get(best);
    }
}
