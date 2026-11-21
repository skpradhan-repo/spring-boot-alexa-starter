package com.ibm.rnd.alexa.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Runs the committed files in {@code samples/documents} through the real extraction, normalization,
 * language detection and chunking code (no database or Ollama needed). Keeps the manual-test samples
 * honest: if a sample stops being valid for the pipeline, this fails.
 */
class SampleDocumentsTest {

    private static final Path SAMPLES = Path.of("samples", "documents");

    private final DocumentExtractor extractor = new DocumentExtractor(2_000_000);
    private final TextNormalizer normalizer = new TextNormalizer();
    private final LanguageDetector detector = new LanguageDetector();
    private final TextChunker chunker = new TextChunker(500, 60);

    private String text(String fileName) throws Exception {
        return normalizer.normalize(extractor.extractText(Files.readAllBytes(SAMPLES.resolve(fileName))));
    }

    @Test
    void englishSampleIsDetectedAsEnglish() throws Exception {
        String text = text("leave-policy-en.txt");
        assertThat(detector.detect(text)).isEqualTo("en");
        assertThat(text).contains("24 days");
        assertThat(chunker.chunk(text)).isNotEmpty();
    }

    @Test
    void hindiSampleIsDetectedAsHindiAndKeepsItsText() throws Exception {
        String text = text("leave-policy-hi.txt");
        assertThat(detector.detect(text)).isEqualTo("hi");
        assertThat(text).contains("छुट्टी नीति");
        assertThat(chunker.chunk(text)).isNotEmpty();
    }

    @Test
    void odiaSampleIsDetectedAsOdiaAndKeepsItsText() throws Exception {
        String text = text("leave-policy-or.txt");
        assertThat(detector.detect(text)).isEqualTo("or");
        assertThat(text).contains("ଛୁଟି ନୀତି");
        assertThat(chunker.chunk(text)).isNotEmpty();
    }

    @Test
    void markdownAndHtmlSamplesExtractReadableText() throws Exception {
        assertThat(text("expense-policy-en.md")).contains("Economy class");
        assertThat(text("it-security-en.html")).contains("full-disk encryption").doesNotContain("<p>");
    }

    @Test
    void everySampleDetectsAsATextType() throws Exception {
        List<Path> files;
        try (var stream = Files.list(SAMPLES)) {
            files = stream.toList();
        }
        assertThat(files).hasSizeGreaterThanOrEqualTo(5);
        for (Path file : files) {
            assertThat(extractor.detectMediaType(Files.readAllBytes(file)))
                    .as(file.getFileName().toString())
                    .startsWith("text/");
        }
    }
}
