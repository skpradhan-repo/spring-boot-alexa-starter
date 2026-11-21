package com.ibm.rnd.alexa.handler;

import java.util.Collection;
import java.util.List;

/** Drinks that can be ordered ({@code drink} slot of {@code OrderDrinkIntent}). */
enum Drink implements Choice {

    COFFEE("coffee", List.of("java", "espresso")),
    TEA("tea", List.of("chai")),
    JUICE("juice", List.of("orange juice", "fruit juice"));

    private final String label;
    private final List<String> synonyms;

    Drink(String label, List<String> synonyms) {
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
