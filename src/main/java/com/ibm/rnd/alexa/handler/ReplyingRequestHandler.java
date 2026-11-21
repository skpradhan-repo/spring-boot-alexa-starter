package com.ibm.rnd.alexa.handler;

import java.util.Optional;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.Response;

/**
 * Template Method base for handlers that answer with a {@link SkillReply}.
 * <p>
 * Subclasses declare <i>when</i> they apply ({@link #canHandle}) and <i>what</i> they answer
 * ({@link #reply}); turning the reply into an SDK response lives in one place.
 */
public abstract class ReplyingRequestHandler implements RequestHandler {

    @Override
    public final Optional<Response> handle(HandlerInput input) {
        return ReplyRenderer.render(input, reply(input));
    }

    /** @return the reply for a request this handler {@linkplain #canHandle accepted} */
    protected abstract SkillReply reply(HandlerInput input);
}
