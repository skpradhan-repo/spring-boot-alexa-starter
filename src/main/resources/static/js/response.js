import { ssmlToText } from './ssml.js';

/** Reduces a response envelope to the parts a person wants to read. */
export function summarizeResponse(envelope) {
    const response = envelope?.response ?? {};
    const speechOf = (output) => ssmlToText(output?.ssml ?? output?.text ?? '');

    return {
        speech: speechOf(response.outputSpeech),
        reprompt: speechOf(response.reprompt?.outputSpeech),
        card: response.card
            ? { type: response.card.type, title: response.card.title ?? '', text: response.card.content ?? response.card.text ?? '' }
            : null,
        directives: (response.directives ?? []).map((directive) =>
            directive.slotToElicit ? `${directive.type} (${directive.slotToElicit})` : directive.type),
        endSession: response.shouldEndSession ?? null,
    };
}
