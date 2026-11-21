import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { postAlexaRequest } from '../../main/resources/static/js/api.js';
import { buildEnvelope, buildRequest, buildSlot } from '../../main/resources/static/js/requestBuilder.js';
import { interpret } from '../../main/resources/static/js/nlu.js';
import { summarizeResponse } from '../../main/resources/static/js/response.js';
import { SimulatorSession } from '../../main/resources/static/js/session.js';
import { Simulator, describeSpec } from '../../main/resources/static/js/simulator.js';
import { ssmlToText } from '../../main/resources/static/js/ssml.js';

const counter = (prefix) => {
    let n = 0;
    return () => `${prefix}${++n}`;
};
const fixedDeps = () => ({ now: () => '2026-01-01T00:00:00.000Z', uuid: counter('id-') });

// ------------------------------------------------------------------ ssml

describe('ssmlToText', () => {
    it('strips the speak wrapper and tags', () => {
        assert.equal(ssmlToText('<speak>Got it. <emphasis level="moderate">large tea</emphasis><break time="300ms"/> ok</speak>'),
            'Got it. large tea ok');
    });

    it('decodes entities after removing tags so escaped markup stays visible as text', () => {
        assert.equal(ssmlToText('<speak>&lt;break time=&quot;10s&quot;/&gt; &amp; more</speak>'), '<break time="10s"/> & more');
    });

    it('returns empty text for non-strings', () => {
        assert.equal(ssmlToText(undefined), '');
        assert.equal(ssmlToText(null), '');
    });
});

// ------------------------------------------------------------------ request builder

describe('buildSlot', () => {
    it('adds a successful resolution with the canonical id for a synonym', () => {
        const slot = buildSlot('drink', 'java', 'DRINK');

        assert.equal(slot.value, 'java');
        const resolution = slot.resolutions.resolutionsPerAuthority[0];
        assert.equal(resolution.status.code, 'ER_SUCCESS_MATCH');
        assert.deepEqual(resolution.values[0].value, { name: 'coffee', id: 'COFFEE' });
    });

    it('reports no match for unknown values', () => {
        const resolution = buildSlot('drink', 'soup', 'DRINK').resolutions.resolutionsPerAuthority[0];

        assert.equal(resolution.status.code, 'ER_SUCCESS_NO_MATCH');
        assert.equal(resolution.values, undefined);
    });

    it('omits the value of an empty slot and adds no resolutions', () => {
        const slot = buildSlot('drink', '  ', 'DRINK');

        assert.equal('value' in slot, false);
        assert.equal('resolutions' in slot, false);
    });

    it('leaves untyped slots without resolutions', () => {
        assert.equal('resolutions' in buildSlot('message', 'hi'), false);
    });
});

describe('buildRequest', () => {
    it('builds a launch request', () => {
        const request = buildRequest({ type: 'LaunchRequest' }, fixedDeps());

        assert.deepEqual(request, {
            type: 'LaunchRequest', requestId: 'amzn1.echo-api.request.id-1',
            timestamp: '2026-01-01T00:00:00.000Z', locale: 'en-US',
        });
    });

    it('builds a session ended request with a default reason', () => {
        assert.equal(buildRequest({ type: 'SessionEndedRequest' }, fixedDeps()).reason, 'USER_INITIATED');
        assert.equal(buildRequest({ type: 'SessionEndedRequest', reason: 'ERROR' }, fixedDeps()).reason, 'ERROR');
    });

    it('builds an intent request with every slot of the model, filled or not', () => {
        const request = buildRequest({ type: 'IntentRequest', intentName: 'OrderDrinkIntent', slots: { drink: 'tea' } }, fixedDeps());

        assert.equal(request.intent.name, 'OrderDrinkIntent');
        assert.deepEqual(Object.keys(request.intent.slots), ['drink', 'size']);
        assert.equal(request.intent.slots.drink.value, 'tea');
        assert.equal('value' in request.intent.slots.size, false);
    });

    it('passes slots unknown to the model through untyped', () => {
        const request = buildRequest({ type: 'IntentRequest', intentName: 'HelloWorldIntent', slots: { extra: 'x' } }, fixedDeps());

        assert.equal(request.intent.slots.extra.value, 'x');
    });

    it('rejects unsupported request types', () => {
        assert.throws(() => buildRequest({ type: 'Nope' }, fixedDeps()), /Unsupported request type/);
    });
});

describe('buildEnvelope', () => {
    const request = { type: 'LaunchRequest' };

    it('omits session attributes on the first request of a session', () => {
        const envelope = buildEnvelope({ sessionId: 's', isNew: true, attributes: {} }, request, { skillId: 'skill' });

        assert.equal(envelope.session.new, true);
        assert.equal('attributes' in envelope.session, false);
        assert.equal(envelope.context.System.application.applicationId, 'skill');
        assert.equal(envelope.session.application.applicationId, 'skill');
    });

    it('echoes attributes in later requests', () => {
        const envelope = buildEnvelope({ sessionId: 's', isNew: false, attributes: { a: 1 } }, request, { skillId: 'skill' });

        assert.equal(envelope.session.new, false);
        assert.deepEqual(envelope.session.attributes, { a: 1 });
    });
});

// ------------------------------------------------------------------ session

describe('SimulatorSession', () => {
    const open = (extra = {}) => ({ response: { shouldEndSession: false, ...extra } });

    it('starts a new session on first use and keeps the id afterwards', () => {
        const session = new SimulatorSession({ uuid: counter('u') });

        const first = session.prepare();
        session.applyResponse(open());
        const second = session.prepare();

        assert.equal(first.isNew, true);
        assert.equal(second.isNew, false);
        assert.equal(second.sessionId, first.sessionId);
    });

    it('peek does not start a session and returns a stable pending id', () => {
        const session = new SimulatorSession({ uuid: counter('u') });

        const a = session.peek();
        const b = session.peek();

        assert.equal(a.sessionId, b.sessionId);
        assert.equal(session.snapshot().status, 'none');
        assert.equal(session.prepare().sessionId, a.sessionId);
    });

    it('echoes response session attributes into the next request', () => {
        const session = new SimulatorSession({ uuid: counter('u') });
        session.prepare();
        session.applyResponse({ sessionAttributes: { drink: 'TEA' }, ...open() });

        assert.deepEqual(session.prepare().attributes, { drink: 'TEA' });
    });

    it('ends the session when shouldEndSession is true or missing, and starts a fresh one next', () => {
        for (const response of [{ shouldEndSession: true }, {}]) {
            const session = new SimulatorSession({ uuid: counter('u') });
            const first = session.prepare();
            session.applyResponse({ sessionAttributes: { a: 1 }, response });

            assert.equal(session.snapshot().status, 'ended');
            const next = session.prepare();
            assert.notEqual(next.sessionId, first.sessionId);
            assert.equal(next.isNew, true);
            assert.deepEqual(next.attributes, {});
        }
    });

    it('remembers the slot Alexa asked for and forgets it when the session ends', () => {
        const session = new SimulatorSession({ uuid: counter('u') });
        session.prepare();
        session.applyResponse(open({ directives: [{ type: 'Dialog.ElicitSlot', slotToElicit: 'size', updatedIntent: { name: 'OrderDrinkIntent' } }] }));

        assert.deepEqual(session.awaiting, { slot: 'size', intent: 'OrderDrinkIntent' });

        session.applyResponse({ response: { shouldEndSession: true } });
        assert.equal(session.awaiting, null);
    });

    it('reset forgets everything', () => {
        const session = new SimulatorSession({ uuid: counter('u') });
        session.prepare();
        session.applyResponse({ sessionAttributes: { a: 1 }, ...open() });
        session.reset();

        assert.deepEqual(session.snapshot(), { status: 'none', sessionId: null, isNew: false, attributes: {}, awaiting: null, turns: 0 });
    });

    it('snapshots are copies that cannot mutate the session', () => {
        const session = new SimulatorSession({ uuid: counter('u') });
        session.prepare();
        session.applyResponse({ sessionAttributes: { a: 1 }, ...open() });

        session.snapshot().attributes.a = 99;

        assert.equal(session.snapshot().attributes.a, 1);
    });
});

// ------------------------------------------------------------------ nlu

describe('interpret', () => {
    it('maps control words to standard intents', () => {
        assert.deepEqual(interpret('open'), { type: 'LaunchRequest' });
        assert.equal(interpret('help').intentName, 'AMAZON.HelpIntent');
        assert.equal(interpret('stop').intentName, 'AMAZON.StopIntent');
        assert.equal(interpret('Goodbye').intentName, 'AMAZON.StopIntent');
        assert.equal(interpret('cancel that').intentName, 'AMAZON.CancelIntent');
    });

    it('extracts drink and size in one utterance', () => {
        assert.deepEqual(interpret('I want a large coffee'), {
            type: 'IntentRequest', intentName: 'OrderDrinkIntent', slots: { drink: 'coffee', size: 'large' },
        });
    });

    it('prefers the longest synonym', () => {
        assert.equal(interpret('order an orange juice').slots.drink, 'orange juice');
    });

    it('passes an unknown drink through so the skill can reject it', () => {
        const spec = interpret('order a large soup');

        assert.equal(spec.intentName, 'OrderDrinkIntent');
        assert.equal(spec.slots.drink, 'soup');
        assert.equal(spec.slots.size, 'large');
    });

    it('does not report generic words as an unknown drink', () => {
        assert.deepEqual(interpret('order a drink').slots, {});
    });

    it('treats an answer as the slot Alexa asked for', () => {
        const awaiting = { slot: 'size', intent: 'OrderDrinkIntent' };

        assert.deepEqual(interpret('large', awaiting).slots, { size: 'large' });
        assert.deepEqual(interpret('gigantic', awaiting).slots, { size: 'gigantic' });
    });

    it('still honours control words while a question is open', () => {
        assert.equal(interpret('cancel', { slot: 'size', intent: 'OrderDrinkIntent' }).intentName, 'AMAZON.CancelIntent');
    });

    it('sends anything else to HelloWorldIntent as the message', () => {
        assert.deepEqual(interpret('good morning'), {
            type: 'IntentRequest', intentName: 'HelloWorldIntent', slots: { message: 'good morning' },
        });
    });
});

// ------------------------------------------------------------------ response summary

describe('summarizeResponse', () => {
    it('extracts speech, reprompt, card and directives', () => {
        const summary = summarizeResponse({
            response: {
                outputSpeech: { ssml: '<speak>Hello</speak>' },
                reprompt: { outputSpeech: { ssml: '<speak>Still there?</speak>' } },
                card: { type: 'Simple', title: 'T', content: 'C' },
                directives: [{ type: 'Dialog.ElicitSlot', slotToElicit: 'size' }],
                shouldEndSession: false,
            },
        });

        assert.deepEqual(summary, {
            speech: 'Hello', reprompt: 'Still there?', card: { type: 'Simple', title: 'T', text: 'C' },
            directives: ['Dialog.ElicitSlot (size)'], endSession: false,
        });
    });

    it('copes with an empty response', () => {
        const summary = summarizeResponse({});

        assert.equal(summary.speech, '');
        assert.equal(summary.card, null);
        assert.deepEqual(summary.directives, []);
    });
});

// ------------------------------------------------------------------ api

describe('postAlexaRequest', () => {
    const reply = (status, body) => async () => ({ ok: status < 400, status, text: async () => body });
    const clock = () => 0;

    it('returns the parsed body on success', async () => {
        const result = await postAlexaRequest('{}', { fetchImpl: reply(200, '{"a":1}'), clock });

        assert.equal(result.ok, true);
        assert.deepEqual(result.body, { a: 1 });
        assert.equal(result.error, null);
    });

    it('posts the body verbatim as JSON', async () => {
        let seen;
        await postAlexaRequest('{not json', { fetchImpl: async (url, init) => { seen = { url, init }; return reply(400, '{}')(); }, clock });

        assert.equal(seen.url, '/alexa');
        assert.equal(seen.init.body, '{not json');
        assert.equal(seen.init.headers['Content-Type'], 'application/json');
    });

    it('reports the ProblemDetail detail on HTTP errors', async () => {
        const result = await postAlexaRequest('{}', { fetchImpl: reply(400, '{"detail":"Invalid Alexa request"}'), clock });

        assert.equal(result.ok, false);
        assert.equal(result.status, 400);
        assert.match(result.error, /Invalid Alexa request/);
    });

    it('reports network failures', async () => {
        const result = await postAlexaRequest('{}', { fetchImpl: async () => { throw new TypeError('offline'); }, clock });

        assert.equal(result.ok, false);
        assert.equal(result.status, 0);
        assert.match(result.error, /Network error: offline/);
    });

    it('reports a non-JSON success body', async () => {
        const result = await postAlexaRequest('{}', { fetchImpl: reply(200, '<html>'), clock });

        assert.equal(result.ok, false);
        assert.match(result.error, /not valid JSON/);
    });
});

// ------------------------------------------------------------------ simulator model

describe('Simulator', () => {
    /** A scripted backend: pops one canned response per call and records request bodies. */
    function setup(responses, options = {}) {
        const sent = [];
        const queue = [...responses];
        const post = async (bodyText) => {
            sent.push(bodyText);
            const next = queue.shift();
            return next.ok === false
                ? { ok: false, status: next.status ?? 500, durationMs: 3, rawText: next.rawText ?? '', body: null, error: next.error }
                : { ok: true, status: 200, durationMs: 3, rawText: JSON.stringify(next.body), body: next.body, error: null };
        };
        const deps = fixedDeps();
        const simulator = new Simulator({ session: new SimulatorSession({ uuid: counter('s') }), post, ...deps, ...options });
        return { simulator, sent };
    }

    const speak = (text, extra = {}) => ({ body: { response: { outputSpeech: { ssml: `<speak>${text}</speak>` }, shouldEndSession: false, ...extra } } });

    it('runs a multi-turn dialog, carrying the session between requests', async () => {
        const { simulator, sent } = setup([
            { body: { sessionAttributes: { drink: 'COFFEE' }, response: { outputSpeech: { ssml: '<speak>What size?</speak>' }, shouldEndSession: false,
                directives: [{ type: 'Dialog.ElicitSlot', slotToElicit: 'size', updatedIntent: { name: 'OrderDrinkIntent' } }] } } },
            speak('One large coffee'),
        ]);

        await simulator.say('order a coffee');
        await simulator.say('large');

        const [first, second] = sent.map((body) => JSON.parse(body));
        assert.equal(first.session.new, true);
        assert.equal(second.session.new, false);
        assert.equal(second.session.sessionId, first.session.sessionId);
        assert.deepEqual(second.session.attributes, { drink: 'COFFEE' });
        assert.equal(second.request.intent.name, 'OrderDrinkIntent');
        assert.equal(second.request.intent.slots.size.value, 'large');
        assert.deepEqual(simulator.state.history.map((entry) => entry.role), ['user', 'alexa', 'user', 'alexa']);
    });

    it('records errors, and keeps the session intact', async () => {
        const { simulator } = setup([{ ok: false, status: 400, error: 'Backend rejected the request: Invalid Alexa request' }]);

        await simulator.say('hello');

        assert.equal(simulator.state.errors.length, 1);
        assert.equal(simulator.state.history.at(-1).role, 'error');
        assert.equal(simulator.state.busy, false);
    });

    it('sends raw JSON verbatim, even when it is malformed, and does not start a session', async () => {
        const { simulator, sent } = setup([{ ok: false, status: 400, error: 'bad' }]);

        await simulator.sendRaw('{not json');

        assert.equal(sent[0], '{not json');
        assert.equal(simulator.state.history[0].text, 'Raw JSON request');
        assert.equal(simulator.state.session.status, 'none');
    });

    it('sendSpec sends exactly the chosen request type', async () => {
        const { simulator, sent } = setup([speak('Hi')]);

        await simulator.sendSpec({ type: 'LaunchRequest' });

        assert.equal(JSON.parse(sent[0]).request.type, 'LaunchRequest');
    });

    it('previewEnvelope has no side effects', () => {
        const { simulator } = setup([]);

        simulator.previewEnvelope({ type: 'LaunchRequest' });

        assert.equal(simulator.state.session.status, 'none');
        assert.equal(simulator.state.history.length, 0);
    });

    it('newSession keeps the conversation but starts a fresh Alexa session', async () => {
        const { simulator, sent } = setup([speak('one'), speak('two')]);
        await simulator.say('hello');
        const firstId = JSON.parse(sent[0]).session.sessionId;

        simulator.newSession();
        await simulator.say('hello again');

        assert.notEqual(JSON.parse(sent[1]).session.sessionId, firstId);
        assert.equal(JSON.parse(sent[1]).session.new, true);
        assert.ok(simulator.state.history.some((entry) => entry.role === 'system'));
        assert.equal(simulator.state.history.filter((entry) => entry.role === 'user').length, 2);
    });

    it('clearConversation wipes history, errors, exchange and session', async () => {
        const { simulator } = setup([{ ok: false, status: 500, error: 'boom' }]);
        await simulator.say('hello');

        simulator.clearConversation();

        const state = simulator.state;
        assert.equal(state.history.length, 0);
        assert.equal(state.errors.length, 0);
        assert.equal(state.exchange, null);
        assert.equal(state.session.status, 'none');
    });

    it('ignores blank input', async () => {
        const { simulator, sent } = setup([]);

        assert.equal(await simulator.say('   '), null);
        assert.equal(sent.length, 0);
    });

    it('does not send while a request is in flight', async () => {
        let release;
        const gate = new Promise((resolve) => { release = resolve; });
        const sent = [];
        const simulator = new Simulator({
            session: new SimulatorSession({ uuid: counter('s') }),
            post: async (body) => { sent.push(body); await gate; return { ok: true, status: 200, durationMs: 1, rawText: '{}', body: {}, error: null }; },
            ...fixedDeps(),
        });

        const first = simulator.say('one');
        const second = await simulator.say('two');
        release();
        await first;

        assert.equal(second, null);
        assert.equal(sent.length, 1);
    });

    it('notifies subscribers and supports unsubscribing', async () => {
        const { simulator } = setup([speak('Hi')]);
        const seen = [];
        const unsubscribe = simulator.subscribe((state) => seen.push(state.busy));

        await simulator.say('hello');
        unsubscribe();
        simulator.clearConversation();

        assert.deepEqual(seen, [true, false]);
    });

    it('uses the configured skill id in requests', async () => {
        const { simulator, sent } = setup([speak('Hi')]);
        simulator.setSkillId('amzn1.ask.skill.mine');

        await simulator.say('hello');

        const body = JSON.parse(sent[0]);
        assert.equal(body.session.application.applicationId, 'amzn1.ask.skill.mine');
        assert.equal(body.context.System.application.applicationId, 'amzn1.ask.skill.mine');
    });
});

describe('describeSpec', () => {
    it('describes each request type', () => {
        assert.equal(describeSpec({ type: 'LaunchRequest' }), 'Open the skill');
        assert.equal(describeSpec({ type: 'SessionEndedRequest', reason: 'ERROR' }), 'End the session (ERROR)');
        assert.equal(describeSpec({ type: 'IntentRequest', intentName: 'OrderDrinkIntent', slots: { drink: 'tea', size: '' } }),
            'OrderDrinkIntent drink=tea');
        assert.equal(describeSpec({ type: 'IntentRequest', intentName: 'AMAZON.HelpIntent', slots: {} }), 'AMAZON.HelpIntent');
    });
});
