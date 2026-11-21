import { findIntent, spokenForms } from './interactionModel.js';

/** Words that mean "a drink" without naming one, so they must not be reported as an unknown drink. */
const GENERIC_DRINK_WORDS = new Set(['drink', 'a drink', 'something', 'anything', 'one', 'it', 'that']);

const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * Minimal stand-in for Alexa's language understanding, used by the chat box of the simulator to turn typed or
 * spoken text into a request. Utterances that match nothing go to HelloWorldIntent, whose {@code message} slot
 * accepts free text (as before).
 *
 * @param {string} utterance
 * @param {{slot:string,intent:string|null}|null} awaiting the slot Alexa asked for in its last reply
 * @returns {{type:'LaunchRequest'}|{type:'IntentRequest',intentName:string,slots:Record<string,string>}}
 */
export function interpret(utterance, awaiting = null) {
    const text = utterance.trim();
    const lower = text.toLowerCase();

    if (/^(open|launch|start)\b/.test(lower)) {
        return { type: 'LaunchRequest' };
    }
    if (/^(help|what can you do)\b/.test(lower)) {
        return intent('AMAZON.HelpIntent');
    }
    if (/^(stop|quit|exit|bye|goodbye)\b/.test(lower)) {
        return intent('AMAZON.StopIntent');
    }
    if (/^cancel\b/.test(lower)) {
        return intent('AMAZON.CancelIntent');
    }

    // Answering a question Alexa just asked: the text belongs to the intent that is collecting slots.
    if (awaiting?.intent && findIntent(awaiting.intent)) {
        const slots = extractTypedSlots(awaiting.intent, lower);
        if (Object.keys(slots).length === 0) {
            slots[awaiting.slot] = text;
        }
        return intent(awaiting.intent, slots);
    }

    const slots = extractTypedSlots('OrderDrinkIntent', lower);
    const orderWords = /\b(?:order|i want|i'd like|i would like|get me)\b/.test(lower);
    if (Object.keys(slots).length > 0 || orderWords) {
        if (!slots.drink) {
            const unknownDrink = remainderAfterOrderWords(lower, slots);
            if (unknownDrink) {
                slots.drink = unknownDrink;
            }
        }
        return intent('OrderDrinkIntent', slots);
    }

    return intent('HelloWorldIntent', { message: text });
}

function intent(intentName, slots = {}) {
    return { type: 'IntentRequest', intentName, slots };
}

/** Finds, for every typed slot of the intent, the longest spoken form present in the text. */
function extractTypedSlots(intentName, lowerText) {
    const slots = {};
    for (const def of findIntent(intentName)?.slots ?? []) {
        if (!def.type) {
            continue;
        }
        const found = spokenForms(def.type)
            .find(({ form }) => new RegExp(`\\b${escapeRegExp(form)}\\b`).test(lowerText));
        if (found) {
            slots[def.name] = found.form;
        }
    }
    return slots;
}

/** "order a large soup" -> "soup": what is left after the order words and already-understood slot values. */
function remainderAfterOrderWords(lowerText, slots) {
    const match = lowerText.match(/\b(?:order|i want|i'd like|i would like|get me)\s+(?:a|an|the|some)?\s*(.+)$/);
    if (!match) {
        return null;
    }
    let remainder = match[1];
    for (const value of Object.values(slots)) {
        remainder = remainder.replace(new RegExp(`\\b${escapeRegExp(value)}\\b`), '');
    }
    remainder = remainder.replace(/\s+/g, ' ').trim();
    return remainder && !GENERIC_DRINK_WORDS.has(remainder) ? remainder : null;
}
