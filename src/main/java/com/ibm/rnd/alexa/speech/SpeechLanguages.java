package com.ibm.rnd.alexa.speech;

import java.util.Set;

/**
 * Languages the local model has an output head for (AI4Bharat IndicConformer, 22 Indian languages). Anything
 * else is rejected before audio is forwarded, so an unsupported language fails clearly instead of being
 * transcribed as something else.
 */
final class SpeechLanguages {

    /** ISO 639 codes as the model names them; Odia is {@code or}. */
    static final Set<String> SUPPORTED = Set.of(
            "as", "bn", "brx", "doi", "gu", "hi", "kn", "kok", "ks", "mai", "ml",
            "mni", "mr", "ne", "or", "pa", "sa", "sat", "sd", "ta", "te", "ur");

    private SpeechLanguages() {
    }

    static boolean isSupported(String code) {
        return code != null && SUPPORTED.contains(code);
    }
}
