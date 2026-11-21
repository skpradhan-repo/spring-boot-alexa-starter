package com.ibm.rnd.alexa.knowledge;

/**
 * Picks a language code from the dominant Unicode script. Cheap, deterministic and offline. It cannot
 * separate languages that share a script (Hindi vs Marathi both use Devanagari), which is acceptable
 * here: the value is only a filter/label, and the embedding model is multilingual either way.
 */
final class LanguageDetector {

    private static final String DEFAULT = "en";

    String detect(String text) {
        int devanagari = 0;
        int odia = 0;
        int latin = 0;
        int sampled = 0;
        for (int i = 0; i < text.length() && sampled < 5000; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            sampled++;
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            switch (script) {
                case DEVANAGARI -> devanagari++;
                case ORIYA -> odia++;
                case LATIN -> latin++;
                default -> { }
            }
        }
        if (odia > devanagari && odia > latin) {
            return "or";
        }
        if (devanagari > latin) {
            return "hi";
        }
        return DEFAULT;
    }
}
