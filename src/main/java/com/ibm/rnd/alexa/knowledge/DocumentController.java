package com.ibm.rnd.alexa.knowledge;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ibm.rnd.alexa.exception.InvalidDocumentException;

/**
 * Upload and status API for knowledge documents. The {@code access_group} parameter is a stand-in for
 * the caller's identity until JWT authentication lands; the service already enforces it on every lookup,
 * so only the source of the value will change.
 */
@RestController
@RequestMapping("/api/documents")
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "enabled", havingValue = "true")
class DocumentController {

    /** Safe to return to clients: no content, no internal paths. */
    record DocumentResponse(UUID id, String name, int version, String accessGroup, String language,
            DocumentStatus status, String failureReason, int chunkCount, Instant createdAt, boolean duplicate) {

        static DocumentResponse of(StoredDocument d, boolean duplicate) {
            return new DocumentResponse(d.id(), d.name(), d.version(), d.accessGroup(), d.language(), d.status(),
                    d.failureReason(), d.chunkCount(), d.createdAt(), duplicate);
        }
    }

    private final DocumentIngestionService ingestion;

    DocumentController(DocumentIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    /** 202 when new work was queued, 200 when identical content already exists in the group. */
    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file,
            @RequestParam(name = "access_group", defaultValue = "public") String accessGroup) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidDocumentException("The upload could not be read");
        }
        DocumentIngestionService.Submission submission = ingestion.submit(file.getOriginalFilename(), content, accessGroup);
        HttpStatus status = submission.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(DocumentResponse.of(submission.document(), submission.duplicate()));
    }

    @GetMapping("/{id}")
    DocumentResponse status(@PathVariable UUID id,
            @RequestParam(name = "access_group", defaultValue = "public") String accessGroup) {
        return DocumentResponse.of(ingestion.status(id, accessGroup), false);
    }
}
