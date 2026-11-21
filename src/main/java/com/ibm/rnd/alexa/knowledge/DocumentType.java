package com.ibm.rnd.alexa.knowledge;

import java.util.Locale;
import java.util.Optional;

/** The upload allow-list: file extension plus the content type Tika must detect from the bytes. */
enum DocumentType {
    PDF("pdf"),
    DOCX("docx"),
    TXT("txt"),
    HTML("html", "htm"),
    MARKDOWN("md", "markdown");

    private static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final String[] extensions;

    DocumentType(String... extensions) {
        this.extensions = extensions;
    }

    static Optional<DocumentType> fromFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (DocumentType type : values()) {
            for (String candidate : type.extensions) {
                if (candidate.equals(extension)) {
                    return Optional.of(type);
                }
            }
        }
        return Optional.empty();
    }

    /** True when the content-detected media type is consistent with the claimed extension. */
    boolean accepts(String detectedMediaType) {
        return switch (this) {
            case PDF -> "application/pdf".equals(detectedMediaType);
            case DOCX -> DOCX_MEDIA_TYPE.equals(detectedMediaType);
            // Plain text, Markdown and HTML are all text; Tika cannot reliably tell them apart by content.
            case TXT, HTML, MARKDOWN -> detectedMediaType.startsWith("text/")
                    || "application/xhtml+xml".equals(detectedMediaType);
        };
    }
}
