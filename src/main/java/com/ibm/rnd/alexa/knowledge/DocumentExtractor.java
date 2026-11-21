package com.ibm.rnd.alexa.knowledge;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;

/**
 * Wraps Apache Tika. {@link #detectMediaType} looks at the bytes (not the file name), so a renamed
 * executable cannot pass as a PDF; {@link #extractText} caps output so a small file that expands into
 * gigabytes of text cannot exhaust memory.
 */
class DocumentExtractor {

    private final Tika tika = new Tika();
    private final int maxChars;

    DocumentExtractor(int maxChars) {
        this.maxChars = maxChars;
    }

    String detectMediaType(byte[] content) {
        try {
            return tika.detect(content);
        } catch (RuntimeException e) {
            return "application/octet-stream";
        }
    }

    String extractText(byte[] content) {
        try {
            List<Document> parts = new TikaDocumentReader(new ByteArrayResource(content), maxChars,
                    ExtractedTextFormatter.defaults()).get();
            return parts.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
        } catch (RuntimeException e) {
            throw new ExtractionException(e);
        }
    }

    /** Parsing failed (corrupt, encrypted, or over the size cap). The cause is logged by the caller. */
    static class ExtractionException extends RuntimeException {
        ExtractionException(Throwable cause) {
            super("Text extraction failed", cause);
        }
    }
}
