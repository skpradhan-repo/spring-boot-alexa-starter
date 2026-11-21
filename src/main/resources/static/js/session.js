const defaultUuid = () => crypto.randomUUID();

/**
 * Tracks one Alexa session across turns, the way a real device does: the client keeps the sessionId, marks only
 * the first request as {@code new}, and echoes the {@code sessionAttributes} of every response back in the next
 * request. A session ends when a response sets {@code shouldEndSession} to anything but {@code false}.
 */
export class SimulatorSession {
    #uuid;
    #sessionId = null;
    #pendingId = null;
    #open = false;
    #isNew = true;
    #attributes = {};
    #awaiting = null;
    #turns = 0;

    constructor({ uuid = defaultUuid } = {}) {
        this.#uuid = uuid;
    }

    /** What the next request would carry, without starting a session (safe for previews). */
    peek() {
        if (this.#open) {
            return { sessionId: this.#sessionId, isNew: this.#isNew, attributes: structuredClone(this.#attributes) };
        }
        this.#pendingId ??= `amzn1.echo-api.session.${this.#uuid()}`;
        return { sessionId: this.#pendingId, isNew: true, attributes: {} };
    }

    /** Session portion for a request that is about to be sent. Starts a new session when none is open. */
    prepare() {
        const next = this.peek();
        if (!this.#open) {
            this.#sessionId = next.sessionId;
            this.#pendingId = null;
            this.#open = true;
            this.#isNew = true;
            this.#attributes = {};
            this.#awaiting = null;
            this.#turns = 0;
        }
        return next;
    }

    /** Records the outcome of a turn: carries attributes forward and notes an open slot question. */
    applyResponse(responseEnvelope) {
        this.#turns += 1;
        this.#isNew = false;
        this.#attributes = { ...(responseEnvelope?.sessionAttributes ?? {}) };

        const response = responseEnvelope?.response ?? {};
        const elicit = (response.directives ?? []).find((directive) => directive.type === 'Dialog.ElicitSlot');
        this.#awaiting = elicit
            ? { slot: elicit.slotToElicit, intent: elicit.updatedIntent?.name ?? null }
            : null;

        if (response.shouldEndSession !== false) {
            this.#open = false;
            this.#awaiting = null;
        }
    }

    /** Forgets everything, so the next request starts a brand new session. */
    reset() {
        this.#sessionId = null;
        this.#pendingId = null;
        this.#open = false;
        this.#isNew = true;
        this.#attributes = {};
        this.#awaiting = null;
        this.#turns = 0;
    }

    /** The slot Alexa asked for in its last reply, or null. */
    get awaiting() {
        return this.#awaiting;
    }

    get open() {
        return this.#open;
    }

    /** Read-only snapshot for the inspector. */
    snapshot() {
        return {
            status: this.#open ? 'open' : this.#sessionId === null ? 'none' : 'ended',
            sessionId: this.#sessionId,
            isNew: this.#open && this.#isNew,
            attributes: structuredClone(this.#attributes),
            awaiting: this.#awaiting,
            turns: this.#turns,
        };
    }
}
