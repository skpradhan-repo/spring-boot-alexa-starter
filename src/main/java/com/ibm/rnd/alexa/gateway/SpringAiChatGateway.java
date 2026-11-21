package com.ibm.rnd.alexa.gateway;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import com.ibm.rnd.alexa.exception.ModelUnavailableException;

import reactor.core.publisher.Flux;

/**
 * Adapter from {@link ChatGateway} to Spring AI (which talks to Ollama). Knows nothing about
 * resilience: timeouts, concurrency and circuit breaking are layered on by {@link ResilientChatGateway}.
 */
public class SpringAiChatGateway implements ChatGateway {

    private final ChatClient client;

    public SpringAiChatGateway(ChatModel chatModel) {
        this.client = ChatClient.create(chatModel);
    }

    @Override
    public String complete(ChatPrompt prompt) {
        try {
            String content = request(prompt).call().content();
            return content == null ? "" : content.strip();
        } catch (RuntimeException e) {
            throw new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE, e);
        }
    }

    @Override
    public Flux<String> stream(ChatPrompt prompt) {
        return request(prompt).stream().content()
                .onErrorMap(e -> !(e instanceof ModelUnavailableException),
                        e -> new ModelUnavailableException(ModelUnavailableException.Reason.UNAVAILABLE, e));
    }

    private ChatClient.ChatClientRequestSpec request(ChatPrompt prompt) {
        ChatClient.ChatClientRequestSpec spec = client.prompt();
        if (!prompt.system().isBlank()) {
            spec = spec.system(prompt.system());
        }
        // Thinking is disabled: hidden reasoning tokens would add seconds of latency to a spoken answer.
        return spec.user(prompt.user())
                .options(OllamaChatOptions.builder().numPredict(prompt.maxTokens()).disableThinking());
    }
}
