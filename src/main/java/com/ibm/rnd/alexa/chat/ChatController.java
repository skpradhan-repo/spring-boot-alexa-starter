package com.ibm.rnd.alexa.chat;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import reactor.core.publisher.Flux;

/**
 * Browser chat API, separate from the Alexa webhook (whose response contract is a single JSON
 * envelope). {@code POST /api/chat} answers once; {@code POST /api/chat/stream} streams tokens as
 * Server-Sent Events so the user sees text as it is generated.
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    public record ChatRequest(@NotBlank @Size(max = ChatService.MAX_MESSAGE_CHARS) String message) {
    }

    public record ChatReply(String answer) {
    }

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatReply answer(@Valid @RequestBody ChatRequest request) {
        return new ChatReply(chat.answer(request.message()));
    }

    @PostMapping(path = "/stream", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatRequest request) {
        return chat.stream(request.message())
                .map(token -> ServerSentEvent.builder(token).event("token").build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder("[DONE]").event("done").build()));
    }
}
