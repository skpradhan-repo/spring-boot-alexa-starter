package com.ibm.rnd.alexa.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

    // 20 tokens = 60 chars max per chunk, 5 tokens = 15 chars overlap: small numbers make boundaries easy to see.
    private final TextChunker chunker = new TextChunker(20, 5);

    @Test
    void blankInputGivesNoChunks() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("  \n ")).isEmpty();
    }

    @Test
    void shortTextIsOneChunk() {
        List<TextChunk> chunks = chunker.chunk("Annual leave is 20 days.");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).text()).isEqualTo("Annual leave is 20 days.");
    }

    @Test
    void neverExceedsTheLimitAndIndexesAreSequential() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(40);
        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks.size()).isGreaterThan(5);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
            assertThat(chunks.get(i).text().length()).isLessThanOrEqualTo(60);
        }
    }

    @Test
    void consecutiveChunksOverlap() {
        String text = "alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november oscar papa quebec romeo";
        List<TextChunk> chunks = chunker.chunk(text);

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            String previousLastWord = lastWord(chunks.get(i - 1).text());
            assertThat(chunks.get(i).text()).contains(previousLastWord);
        }
    }

    @Test
    void prefersParagraphBreaksOverCuttingSentences() {
        String text = "First paragraph is here.\n\nSecond paragraph is here too.\n\nThird paragraph ends it.";
        // 15 tokens = 45 chars: one paragraph fits, two do not, so every cut must land on a paragraph break.
        List<TextChunk> chunks = new TextChunker(15, 0).chunk(text);

        assertThat(chunks).extracting(TextChunk::text)
                .containsExactly("First paragraph is here.", "Second paragraph is here too.", "Third paragraph ends it.");
    }

    @Test
    void splitsAtTheDevanagariDanda() {
        String sentence = "\u092F\u0939 \u090F\u0915 \u092A\u0930\u0940\u0915\u094D\u0937\u0923 \u0939\u0948\u0964 ";
        List<TextChunk> chunks = new TextChunker(20, 0).chunk(sentence.repeat(10));

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text()).endsWith("\u0964"));
    }

    @Test
    void textWithoutAnySeparatorIsStillCut() {
        List<TextChunk> chunks = chunker.chunk("x".repeat(500));

        assertThat(chunks.size()).isGreaterThan(5);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(60));
    }

    @Test
    void doesNotSplitASurrogatePair() {
        String emoji = "\uD83D\uDE00";
        List<TextChunk> chunks = new TextChunker(20, 0).chunk(emoji.repeat(100));

        for (TextChunk chunk : chunks) {
            assertThat(Character.isLowSurrogate(chunk.text().charAt(0))).isFalse();
            assertThat(Character.isHighSurrogate(chunk.text().charAt(chunk.text().length() - 1))).isFalse();
        }
    }

    @Test
    void allWordsSurviveChunking() {
        String text = "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen";
        String joined = String.join(" ", chunker.chunk(text).stream().map(TextChunk::text).toList());

        for (String word : text.split(" ")) {
            assertThat(joined).contains(word);
        }
    }

    private static String lastWord(String text) {
        return text.substring(text.lastIndexOf(' ') + 1);
    }
}
