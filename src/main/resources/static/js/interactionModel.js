/**
 * Stand-in for the interaction model you would define in the Alexa developer console:
 * custom slot types (with synonyms and canonical ids) and the intents the skill understands.
 * The browser simulator uses it to offer intents/slots in the UI, interpret typed or spoken text,
 * and attach entity resolutions to slots exactly like Alexa does.
 *
 * Keep in sync with the backend handlers (OrderDrinkIntentHandler, Drink, Size ...).
 */

const entry = (id, name, synonyms = []) => Object.freeze({ id, name, synonyms: Object.freeze(synonyms) });

export const SLOT_TYPES = Object.freeze({
    DRINK: Object.freeze([
        entry('COFFEE', 'coffee', ['java', 'espresso']),
        entry('TEA', 'tea', ['chai']),
        entry('JUICE', 'juice', ['orange juice', 'fruit juice']),
    ]),
    SIZE: Object.freeze([
        entry('SMALL', 'small', ['tiny', 'short']),
        entry('MEDIUM', 'medium', ['regular', 'normal']),
        entry('LARGE', 'large', ['big', 'tall']),
    ]),
});

const slot = (name, type = null, sample = '') => Object.freeze({ name, type, sample });

export const INTENTS = Object.freeze([
    Object.freeze({ name: 'HelloWorldIntent', slots: Object.freeze([slot('message', null, 'good morning')]) }),
    Object.freeze({
        name: 'OrderDrinkIntent',
        slots: Object.freeze([slot('drink', 'DRINK', 'coffee'), slot('size', 'SIZE', 'large')]),
    }),
    Object.freeze({ name: 'AMAZON.HelpIntent', slots: Object.freeze([]) }),
    Object.freeze({ name: 'AMAZON.StopIntent', slots: Object.freeze([]) }),
    Object.freeze({ name: 'AMAZON.CancelIntent', slots: Object.freeze([]) }),
    Object.freeze({ name: 'AMAZON.FallbackIntent', slots: Object.freeze([]) }),
]);

export const REQUEST_TYPES = Object.freeze(['LaunchRequest', 'IntentRequest', 'SessionEndedRequest']);
export const SESSION_END_REASONS = Object.freeze(['USER_INITIATED', 'ERROR', 'EXCEEDED_MAX_REPROMPTS']);

export function findIntent(name) {
    return INTENTS.find((intent) => intent.name === name) ?? null;
}

/** @returns the slot type entry whose name or synonym equals {@code spoken}, or null */
export function matchEntry(typeName, spoken) {
    const values = SLOT_TYPES[typeName];
    if (!values || typeof spoken !== 'string' || spoken.trim() === '') {
        return null;
    }
    const needle = spoken.trim().toLowerCase();
    return values.find((value) => value.name === needle || value.synonyms.includes(needle)) ?? null;
}

/** All spoken forms (names and synonyms) of a slot type, longest first so "orange juice" beats "juice". */
export function spokenForms(typeName) {
    const forms = (SLOT_TYPES[typeName] ?? []).flatMap((value) =>
        [value.name, ...value.synonyms].map((form) => ({ form, entry: value })));
    return forms.sort((a, b) => b.form.length - a.form.length);
}
