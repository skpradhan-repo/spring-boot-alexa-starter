package com.ibm.rnd.alexa.handler;

import java.util.Collection;
import java.util.List;

/** Drink sizes ({@code size} slot of {@code OrderDrinkIntent}). */
enum Size implements Choice {

    SMALL("small", List.of("tiny", "short")),
    MEDIUM("medium", List.of("regular", "normal")),
    LARGE("large", List.of("big", "tall"));

    private final String label;
    private final List<String> synonyms;

    Size(String label, List<String> synonyms) {
        this.label = label;
        this.synonyms = synonyms;
    }

    @Override
    public String id() {
        return name();
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public Collection<String> synonyms() {
        return synonyms;
    }
}
