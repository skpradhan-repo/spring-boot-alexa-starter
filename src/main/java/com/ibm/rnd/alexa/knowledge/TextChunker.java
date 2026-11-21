package com.ibm.rnd.alexa.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive splitter: prefers paragraph breaks, then lines, then sentence ends, then words, and only
 * cuts mid-word as a last resort. Consecutive pieces are packed up to the size limit, and each chunk
 * begins with the tail of the previous one so a fact that straddles a boundary stays retrievable.
 * <p>
 * Sizes are estimated, not tokenized: {@value #CHARS_PER_TOKEN} characters per token is a conservative
 * figure that also holds for Devanagari and Odia, which tokenize into more pieces than English.
 */
final class TextChunker {

    static final int CHARS_PER_TOKEN = 3;

    /** Sentence ends include the Devanagari/Odia danda (U+0964) and double danda (U+0965). */
    private static final List<String> SEPARATORS = List.of("\n\n", "\n", "\u0964 ", "\u0965 ", ". ", "? ", "! ", "; ", ", ", " ");

    private final int maxChars;
    private final int overlapChars;

    TextChunker(int chunkTokens, int overlapTokens) {
        this.maxChars = chunkTokens * CHARS_PER_TOKEN;
        this.overlapChars = overlapTokens * CHARS_PER_TOKEN;
    }

    List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // Reserve room for the overlap so "overlap + new text" never exceeds the limit.
        List<String> segments = new ArrayList<>();
        split(text, 0, maxChars - overlapChars, segments);

        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String segment : segments) {
            if (current.length() > 0 && current.length() + segment.length() > maxChars) {
                String emitted = current.toString();
                add(chunks, emitted);
                current = new StringBuilder(overlapTail(emitted));
            }
            current.append(segment);
        }
        add(chunks, current.toString());
        return chunks;
    }

    private void add(List<TextChunk> chunks, String text) {
        String trimmed = text.strip();
        if (!trimmed.isEmpty()) {
            chunks.add(new TextChunk(chunks.size(), trimmed));
        }
    }

    private void split(String text, int level, int limit, List<String> out) {
        if (text.length() <= limit) {
            out.add(text);
            return;
        }
        if (level >= SEPARATORS.size()) {
            hardCut(text, limit, out);
            return;
        }
        List<String> parts = splitKeepingSeparator(text, SEPARATORS.get(level));
        if (parts.size() == 1) {
            split(text, level + 1, limit, out);
            return;
        }
        for (String part : parts) {
            split(part, level + 1, limit, out);
        }
    }

    private static List<String> splitKeepingSeparator(String text, String separator) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int at;
        while ((at = text.indexOf(separator, start)) >= 0) {
            int end = at + separator.length();
            parts.add(text.substring(start, end));
            start = end;
        }
        if (start < text.length()) {
            parts.add(text.substring(start));
        }
        return parts;
    }

    private static void hardCut(String text, int limit, List<String> out) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
                end--;
            }
            out.add(text.substring(start, end));
            start = end;
        }
    }

    /** Last {@code overlapChars} of a chunk, moved forward to a word start so words are not cut in half. */
    private String overlapTail(String chunk) {
        if (overlapChars == 0 || chunk.length() <= overlapChars) {
            return overlapChars == 0 ? "" : chunk;
        }
        int from = chunk.length() - overlapChars;
        if (Character.isLowSurrogate(chunk.charAt(from))) {
            from++;
        }
        for (int i = from; i < chunk.length(); i++) {
            if (Character.isWhitespace(chunk.charAt(i - 1))) {
                return chunk.substring(i);
            }
        }
        return chunk.substring(from);
    }
}
