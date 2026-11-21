import { interpret } from './nlu.js';
import { buildEnvelope, buildRequest } from './requestBuilder.js';
import { summarizeResponse } from './response.js';

export const DEFAULT_SKILL_ID = 'amzn1.ask.skill.your-skill-id';

const MAX_ERRORS = 20;

const pretty = (text) => {
    try {
        return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
        return text;
    }
};

/** Human-readable label for a request specification (what the user "did"). */
export function describeSpec(spec) {
    switch (spec.type) {
        case 'LaunchRequest':
            return 'Open the skill';
        case 'SessionEndedRequest':
            return `End the session (${spec.reason ?? 'USER_INITIATED'})`;
        default: {
            const slots = Object.entries(spec.slots ?? {})
                .filter(([, value]) => String(value).trim() !== '')
                .map(([name, value]) => `${name}=${value}`);
            return slots.length ? `${spec.intentName} ${slots.join(', ')}` : spec.intentName;
        }
    }
}

/**
 * Application model of the simulator, free of any DOM code so it can be unit-tested: owns the conversation,
 * the error list and the last request/response pair, and drives the {@link SimulatorSession}.
 *
 * Collaborators are injected: {@code post(bodyText)} sends a request (see api.js), {@code session} tracks the
 * Alexa session.
 */
export class Simulator {
    #session;
    #post;
    #deps;
    #skillId;
    #listeners = new Set();
    #seq = 0;
    #history = [];
    #errors = [];
    #exchange = null;
    #busy = false;

    constructor({ session, post, skillId = DEFAULT_SKILL_ID, now, uuid }) {
        this.#session = session;
        this.#post = post;
        this.#skillId = skillId;
        this.#deps = { now, uuid };
    }

    /** Immutable snapshot of everything the UI renders. */
    get state() {
        return {
            history: [...this.#history],
            errors: [...this.#errors],
            exchange: this.#exchange,
            busy: this.#busy,
            skillId: this.#skillId,
            session: this.#session.snapshot(),
        };
    }

    /** @returns {() => void} unsubscribe function */
    subscribe(listener) {
        this.#listeners.add(listener);
        return () => this.#listeners.delete(listener);
    }

    setSkillId(skillId) {
        this.#skillId = skillId.trim() || DEFAULT_SKILL_ID;
        this.#emit();
    }

    /** The full envelope that {@code sendSpec(spec)} would send now, without sending it or starting a session. */
    previewEnvelope(spec) {
        return buildEnvelope(this.#session.peek(), buildRequest(spec, this.#deps), { skillId: this.#skillId });
    }

    /** Chat-style input: interpret free text as an Alexa request and send it. */
    async say(utterance) {
        const text = utterance.trim();
        if (!text || this.#busy) {
            return null;
        }
        return this.#send(interpret(text, this.#session.awaiting), text);
    }

    /** Developer-style input: send exactly the requested request type, intent and slots. */
    async sendSpec(spec) {
        return this.#busy ? null : this.#send(spec, describeSpec(spec));
    }

    /** Sends the given text as the request body, verbatim - even if it is not valid JSON. */
    async sendRaw(bodyText) {
        if (this.#busy) {
            return null;
        }
        let intent = 'raw JSON';
        try {
            const parsed = JSON.parse(bodyText);
            intent = parsed?.request?.intent?.name ?? parsed?.request?.type ?? intent;
        } catch {
            // Deliberately sending malformed JSON is a valid test; the backend will answer with an error.
        }
        this.#push('user', 'Raw JSON request', { intent });
        return this.#run(bodyText);
    }

    /** Ends the session but keeps the conversation on screen. */
    newSession() {
        this.#session.reset();
        this.#push('system', 'New session - the next request starts a fresh Alexa session.');
        this.#emit();
    }

    /** Wipes conversation, errors and inspectors and starts over. */
    clearConversation() {
        this.#session.reset();
        this.#history = [];
        this.#errors = [];
        this.#exchange = null;
        this.#emit();
    }

    dismissErrors() {
        this.#errors = [];
        this.#emit();
    }

    async #send(spec, userText) {
        const envelope = buildEnvelope(this.#session.prepare(), buildRequest(spec, this.#deps), { skillId: this.#skillId });
        this.#push('user', userText, { intent: spec.intentName ?? spec.type });
        return this.#run(JSON.stringify(envelope));
    }

    async #run(bodyText) {
        this.#busy = true;
        this.#emit();

        const result = await this.#post(bodyText);

        this.#exchange = {
            request: pretty(bodyText),
            response: pretty(result.rawText),
            status: result.status,
            durationMs: Math.round(result.durationMs),
        };

        if (result.ok) {
            this.#session.applyResponse(result.body);
            const summary = summarizeResponse(result.body);
            this.#push('alexa', summary.speech || '(no speech)', { summary });
        } else {
            this.#errors = [{ id: ++this.#seq, message: result.error }, ...this.#errors].slice(0, MAX_ERRORS);
            this.#push('error', result.error);
        }

        this.#busy = false;
        this.#emit();
        return result;
    }

    #push(role, text, extra = {}) {
        this.#history.push({ id: ++this.#seq, role, text, ...extra });
    }

    #emit() {
        const state = this.state;
        this.#listeners.forEach((listener) => listener(state));
    }
}
