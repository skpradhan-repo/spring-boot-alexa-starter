import { findIntent, matchEntry } from './interactionModel.js';

const defaultUuid = () => crypto.randomUUID();
const defaultNow = () => new Date().toISOString();

/**
 * Builds a slot the way Alexa sends it. Typed slots get entity resolutions: ER_SUCCESS_MATCH with the
 * canonical id when the value (or a synonym) is known, ER_SUCCESS_NO_MATCH otherwise.
 */
export function buildSlot(name, value, typeName = null) {
    const slot = { name, confirmationStatus: 'NONE' };
    if (value === undefined || value === null || String(value).trim() === '') {
        return slot;
    }
    slot.value = String(value);
    if (typeName) {
        const match = matchEntry(typeName, slot.value);
        slot.resolutions = {
            resolutionsPerAuthority: [{
                authority: `amzn1.er-authority.simulator.${typeName}`,
                status: { code: match ? 'ER_SUCCESS_MATCH' : 'ER_SUCCESS_NO_MATCH' },
                ...(match && { values: [{ value: { name: match.name, id: match.id } }] }),
            }],
        };
    }
    return slot;
}

/**
 * @param {{type:'LaunchRequest'}|{type:'IntentRequest',intentName:string,slots?:Record<string,string>}
 *         |{type:'SessionEndedRequest',reason?:string}} spec
 */
export function buildRequest(spec, { now = defaultNow, uuid = defaultUuid, locale = 'en-US' } = {}) {
    const base = {
        type: spec.type,
        requestId: `amzn1.echo-api.request.${uuid()}`,
        timestamp: now(),
        locale,
    };

    switch (spec.type) {
        case 'LaunchRequest':
            return base;
        case 'SessionEndedRequest':
            return { ...base, reason: spec.reason ?? 'USER_INITIATED' };
        case 'IntentRequest':
            return { ...base, intent: buildIntent(spec.intentName, spec.slots ?? {}) };
        default:
            throw new Error(`Unsupported request type: ${spec.type}`);
    }
}

function buildIntent(intentName, values) {
    const definition = findIntent(intentName);
    const slots = {};
    for (const def of definition?.slots ?? []) {
        slots[def.name] = buildSlot(def.name, values[def.name], def.type);
    }
    // Slots outside the known model are passed through untyped so the backend can be probed with them.
    for (const [name, value] of Object.entries(values)) {
        if (!(name in slots)) {
            slots[name] = buildSlot(name, value);
        }
    }
    return { name: intentName, confirmationStatus: 'NONE', slots };
}

/**
 * Wraps a request in the envelope Alexa sends: session, context.System and request.
 * @param {{sessionId:string,isNew:boolean,attributes:Object}} session from SimulatorSession.prepare()
 */
export function buildEnvelope(session, request, { skillId, userId = 'local-test-user', deviceId = 'local-test-device' }) {
    const application = { applicationId: skillId };
    const user = { userId };
    return {
        version: '1.0',
        session: {
            new: session.isNew,
            sessionId: session.sessionId,
            application,
            // Alexa omits attributes on the first request of a session.
            ...(!session.isNew && { attributes: session.attributes }),
            user,
        },
        context: { System: { application, user, device: { deviceId, supportedInterfaces: {} } } },
        request,
    };
}
