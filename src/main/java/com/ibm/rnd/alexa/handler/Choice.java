package com.ibm.rnd.alexa.handler;

import java.util.Collection;
import java.util.Locale;

/**
 * One value of a custom slot type (for example a drink). Mirrors an entry of the interaction model:
 * a canonical {@link #id()} plus the spoken synonyms that map to it.
 */
interface Choice {

    /** Canonical id, as returned in Alexa entity resolutions. */
    String id();

    /** Human-readable name used in replies. */
    String label();

    Collection<String> synonyms();

    default boolean matchesSpoken(String spoken) {
        String normalized = spoken.strip().toLowerCase(Locale.ROOT);
        return label().equalsIgnoreCase(normalized)
                || synonyms().stream().anyMatch(synonym -> synonym.equalsIgnoreCase(normalized));
    }
}
