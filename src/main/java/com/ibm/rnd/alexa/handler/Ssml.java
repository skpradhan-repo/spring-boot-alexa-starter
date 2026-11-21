package com.ibm.rnd.alexa.handler;

import java.util.List;

/** Helpers for building safe SSML fragments. */
final class Ssml {

    private Ssml() {
    }

    /** Escapes XML special characters so user-supplied text cannot break or inject SSML markup. */
    static String escape(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&apos;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String pause(int millis) {
        return "<break time=\"" + millis + "ms\"/>";
    }

    /** "a", "a or b", "a, b, or c". Items are escaped. */
    static String joinWithOr(List<String> items) {
        List<String> safe = items.stream().map(Ssml::escape).toList();
        return switch (safe.size()) {
            case 0 -> "";
            case 1 -> safe.get(0);
            case 2 -> safe.get(0) + " or " + safe.get(1);
            default -> String.join(", ", safe.subList(0, safe.size() - 1)) + ", or " + safe.get(safe.size() - 1);
        };
    }
}
