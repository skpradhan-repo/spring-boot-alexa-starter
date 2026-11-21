package com.ibm.rnd.alexa.knowledge;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Question answering over uploaded documents. Like upload, {@code access_group} stands in for the caller's
 * identity until JWT lands; the search is already restricted to it in SQL.
 */
@RestController
@RequestMapping("/api/knowledge")
@Validated
@ConditionalOnProperty(prefix = "assistant.knowledge", name = "enabled", havingValue = "true")
class KnowledgeController {

    record AskRequest(
            @NotBlank @Size(max = KnowledgeAnswerService.MAX_QUESTION_CHARS) String question,
            @JsonProperty("access_group") String accessGroup) {
    }

    private final KnowledgeAnswerService answers;

    KnowledgeController(KnowledgeAnswerService answers) {
        this.answers = answers;
    }

    @PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    KnowledgeAnswerService.Answer ask(@Valid @RequestBody AskRequest request) {
        return answers.ask(request.question(), request.accessGroup() == null ? "public" : request.accessGroup());
    }
}
