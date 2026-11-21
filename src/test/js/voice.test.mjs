import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { localLanguageCode } from '../../main/resources/static/js/languages.js';
import { describeSpeechState, getSpeechStatus, transcribeLocal } from '../../main/resources/static/js/localSpeech.js';
import { canRecord, describeMicError } from '../../main/resources/static/js/recorder.js';
import { readRecognitionEvent } from '../../main/resources/static/js/speech.js';
import {
    SUPPORT_KEY, createSupportTracker, describeEngine, languageOptionLabel,
} from '../../main/resources/static/js/support.js';
import { bindVoice } from '../../main/resources/static/js/voice.js';
import { TARGET_RATE, encodeWav, resample, toMono, wavSeconds } from '../../main/resources/static/js/wav.js';

// ---------------------------------------------------------------- wav

describe('wav encoding', () => {
    it('writes the canonical 44-byte header for 16 kHz mono 16-bit PCM', () => {
        const wav = encodeWav(new Float32Array(16_000), TARGET_RATE);
        const view = new DataView(wav.buffer);
        const text = (offset, length) => String.fromCharCode(...wav.slice(offset, offset + length));

        assert.equal(text(0, 4), 'RIFF');
        assert.equal(text(8, 4), 'WAVE');
        assert.equal(text(12, 4), 'fmt ');
        assert.equal(view.getUint16(20, true), 1);
        assert.equal(view.getUint16(22, true), 1);
        assert.equal(view.getUint32(24, true), 16_000);
        assert.equal(view.getUint32(28, true), 32_000);
        assert.equal(view.getUint16(34, true), 16);
        assert.equal(text(36, 4), 'data');
        assert.equal(view.getUint32(40, true), 32_000);
        assert.equal(view.getUint32(4, true), wav.length - 8);
        assert.equal(wav.length, 44 + 32_000);
    });

    it('clamps samples and maps full scale to the 16-bit limits', () => {
        const wav = encodeWav(new Float32Array([2, -2, 0, 0.5]));
        const view = new DataView(wav.buffer);
        assert.equal(view.getInt16(44, true), 32767);
        assert.equal(view.getInt16(46, true), -32768);
        assert.equal(view.getInt16(48, true), 0);
        assert.equal(view.getInt16(50, true), Math.trunc(0.5 * 0x7fff));
    });

    it('resamples to 16 kHz and keeps the duration', () => {
        const wav = encodeWav(new Float32Array(48_000), 48_000);
        assert.equal(wavSeconds(wav), 1);
    });

    it('averages channels into mono and passes a single channel through', () => {
        const left = new Float32Array([1, 0.5]);
        const right = new Float32Array([0, 0.5]);
        assert.deepEqual([...toMono([left, right])], [0.5, 0.5]);
        assert.equal(toMono([left]), left);
    });

    it('returns the same samples when no resampling is needed', () => {
        const samples = new Float32Array([0.1, 0.2]);
        assert.equal(resample(samples, 16_000), samples);
    });

    it('does not fail on a one-sample input', () => {
        assert.equal(resample(new Float32Array([0.3]), 8_000).length, 2);
    });
});

// ---------------------------------------------------------------- recognition events

describe('readRecognitionEvent', () => {
    const result = (transcript, isFinal) => Object.assign([{ transcript }], { isFinal });

    it('separates interim from final text', () => {
        assert.deepEqual(readRecognitionEvent({ resultIndex: 0, results: [result('hel', false)] }), { interim: 'hel', final: '' });
        assert.deepEqual(readRecognitionEvent({ resultIndex: 0, results: [result(' hello ', true)] }), { interim: '', final: 'hello' });
    });

    it('starts at resultIndex so earlier settled results are not repeated', () => {
        const event = { resultIndex: 1, results: [result('old', true), result('new', false)] };
        assert.deepEqual(readRecognitionEvent(event), { interim: 'new', final: '' });
    });

    it('copes with an empty alternative list', () => {
        const empty = Object.assign([], { isFinal: true });
        assert.deepEqual(readRecognitionEvent({ results: [empty] }), { interim: '', final: '' });
    });
});

// ---------------------------------------------------------------- support tracker

function memoryStorage(initial = {}) {
    const data = { ...initial };
    return { data, getItem: (key) => data[key] ?? null, setItem: (key, value) => { data[key] = value; } };
}

describe('support tracker', () => {
    it('is unknown until something has been seen, and is kept per engine and language', () => {
        const tracker = createSupportTracker(memoryStorage());
        assert.equal(tracker.status('browser', 'or-IN'), 'unknown');

        tracker.record('browser', 'or-IN', 'failed');
        tracker.record('local', 'or-IN', 'works');

        assert.equal(tracker.status('browser', 'or-IN'), 'failed');
        assert.equal(tracker.status('local', 'or-IN'), 'works');
        assert.equal(tracker.status('browser', 'hi-IN'), 'unknown');
    });

    it('persists to storage and ignores outcomes other than works or failed', () => {
        const storage = memoryStorage();
        createSupportTracker(storage).record('browser', 'hi-IN', 'works');
        createSupportTracker(storage).record('browser', 'hi-IN', 'maybe');

        assert.equal(createSupportTracker(storage).status('browser', 'hi-IN'), 'works');
    });

    it('survives corrupt or missing storage', () => {
        assert.equal(createSupportTracker(memoryStorage({ [SUPPORT_KEY]: '{not json' })).status('browser', 'hi-IN'), 'unknown');
        const tracker = createSupportTracker(null);
        tracker.record('browser', 'hi-IN', 'works');
        assert.equal(tracker.status('browser', 'hi-IN'), 'unknown');
    });

    it('does not throw when storage refuses writes', () => {
        const tracker = createSupportTracker({ getItem: () => null, setItem: () => { throw new Error('full'); } });
        assert.doesNotThrow(() => tracker.record('browser', 'hi-IN', 'works'));
    });
});

describe('engine descriptions', () => {
    it('never claims support that has not been seen', () => {
        assert.match(describeEngine('browser', 'Odia', 'unknown', true), /has not been tested in this browser yet/);
        assert.match(describeEngine('local', 'Odia', 'unknown', true), /has not been tested here yet/);
        assert.doesNotMatch(describeEngine('browser', 'Odia', 'unknown', true), /worked|works/);
    });

    it('says the browser engine needs internet and the local engine stays on this computer', () => {
        assert.match(describeEngine('browser', 'Hindi', 'works', true), /sends the audio to its vendor.*internet/);
        assert.match(describeEngine('local', 'Hindi', 'works', true), /stays on this computer/);
    });

    it('points to the local engine only when it has a model for the language', () => {
        assert.match(describeEngine('browser', 'Odia', 'failed', true), /try the local engine/);
        assert.doesNotMatch(describeEngine('browser', 'Spanish', 'failed', false), /local engine/);
        assert.match(describeEngine('local', 'Spanish', 'unknown', false), /no model for Spanish/);
    });

    it('labels language options with what has been seen', () => {
        assert.equal(languageOptionLabel('Odia', 'works'), 'Odia - works here');
        assert.equal(languageOptionLabel('Odia', 'failed'), 'Odia - not supported by this engine');
        assert.equal(languageOptionLabel('Odia', 'unknown'), 'Odia');
    });

    it('maps voice languages to local model codes', () => {
        assert.equal(localLanguageCode('or-IN'), 'or');
        assert.equal(localLanguageCode('en-IN'), null);
    });
});

// ---------------------------------------------------------------- local speech client

const response = (status, body) => async () => ({
    ok: status < 400,
    status,
    text: async () => (typeof body === 'string' ? body : JSON.stringify(body)),
});

describe('local speech client', () => {
    it('reads a ready status', async () => {
        const status = await getSpeechStatus({ fetchImpl: response(200, { ready: true, state: 'ready', languages: ['or'] }) });
        assert.deepEqual(status, { ok: true, ready: true, state: 'ready', languages: ['or'], error: null });
    });

    it('reports a disabled engine for 404 and an unreachable one for other failures', async () => {
        assert.equal((await getSpeechStatus({ fetchImpl: response(404, '') })).state, 'disabled');
        assert.equal((await getSpeechStatus({ fetchImpl: response(500, { detail: 'x' }) })).state, 'unreachable');
        assert.equal((await getSpeechStatus({ fetchImpl: async () => { throw new Error('down'); } })).state, 'unreachable');
    });

    it('has a message for every state and none when ready', () => {
        assert.equal(describeSpeechState('ready'), '');
        for (const state of ['loading', 'model_missing', 'disabled', 'unreachable', 'error']) {
            assert.notEqual(describeSpeechState(state), '');
        }
    });

    it('posts the WAV as audio/wav with an encoded language', async () => {
        let call;
        const fetchImpl = async (url, init) => {
            call = { url, init };
            return response(200, { text: 'ନମସ୍କାର' })();
        };
        const wav = new Uint8Array([1, 2, 3]);

        const result = await transcribeLocal(wav, 'or', { fetchImpl });

        assert.deepEqual(result, { ok: true, text: 'ନମସ୍କାର', error: null });
        assert.equal(call.url, '/api/speech/transcribe?lang=or');
        assert.equal(call.init.method, 'POST');
        assert.equal(call.init.headers['Content-Type'], 'audio/wav');
        assert.equal(call.init.body, wav);
    });

    it('turns server failures into readable messages without leaking details', async () => {
        const busy = await transcribeLocal(new Uint8Array(1), 'or', { fetchImpl: response(503, { detail: 'secret internals' }) });
        assert.equal(busy.ok, false);
        assert.match(busy.error, /not running, is still loading, or is busy/);
        assert.doesNotMatch(busy.error, /secret/);

        const off = await transcribeLocal(new Uint8Array(1), 'or', { fetchImpl: response(404, '') });
        assert.match(off.error, /not enabled/);
    });
});

describe('recorder helpers', () => {
    it('detects whether recording is possible', () => {
        assert.equal(canRecord({}), false);
        assert.equal(canRecord({ mediaDevices: { getUserMedia() {} }, MediaRecorder: class {}, AudioContext: class {} }), true);
    });

    it('explains microphone failures', () => {
        assert.match(describeMicError({ name: 'NotAllowedError' }), /blocked/);
        assert.match(describeMicError({ name: 'NotFoundError' }), /No microphone/);
        assert.match(describeMicError({ name: 'NotReadableError' }), /in use/);
        assert.match(describeMicError(new Error('boom')), /boom/);
    });
});

// ---------------------------------------------------------------- bindVoice

function fakeElement(initial = {}) {
    const listeners = {};
    const classes = new Set();
    return {
        value: '',
        disabled: false,
        classList: { add: (name) => classes.add(name), remove: (name) => classes.delete(name), has: (name) => classes.has(name) },
        addEventListener: (type, fn) => { listeners[type] = fn; },
        click: () => listeners.click?.(),
        ...initial,
    };
}

function setup({ engine = 'browser', lang = 'or-IN', deps = {}, supportedBrowser = true } = {}) {
    const mic = fakeElement();
    const stop = fakeElement({ disabled: true });
    const clear = fakeElement();
    const input = fakeElement();
    const indicator = fakeElement();
    const statuses = [];
    const submitted = [];
    const records = [];
    const state = { engine, lang };
    let handlers;
    let recognizerCalls = 0;

    const defaultDeps = {
        createRecognizer(options) {
            handlers = options;
            return { supported: supportedBrowser, start: () => { recognizerCalls++; }, stop() {} };
        },
        cancelSpeech() {},
        canRecord: () => true,
        getSpeechStatus: async () => ({ ready: true, state: 'ready' }),
        startRecording: async () => ({ finish: async () => new Uint8Array([9]), cancel() {} }),
        transcribeLocal: async () => ({ ok: true, text: 'ନମସ୍କାର', error: null }),
        ...deps,
    };
    const support = { record: (e, l, o) => records.push([e, l, o]) };

    const binding = bindVoice({
        micButton: mic, stopButton: stop, clearButton: clear, input, indicator,
        submit: () => submitted.push(input.value),
        setStatus: (text) => statuses.push(text),
        getLang: () => state.lang,
        getEngine: () => state.engine,
        support,
        deps: defaultDeps,
    });
    return {
        mic, stop, clear, input, indicator, statuses, submitted, records, state, binding,
        handlers: () => handlers, recognizerCalls: () => recognizerCalls,
    };
}

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));

describe('bindVoice with the browser engine', () => {
    it('starts the browser recognizer and shows live interim text', () => {
        const t = setup();
        t.mic.click();
        assert.equal(t.recognizerCalls(), 1);

        t.handlers().onStart();
        assert.equal(t.mic.disabled, true);
        assert.equal(t.stop.disabled, false);
        assert.equal(t.indicator.classList.has('listening'), true);

        t.handlers().onInterim('ନମ');
        assert.equal(t.input.value, 'ନମ');
        assert.match(t.statuses.at(-1), /not final yet/);
    });

    it('submits the final text when listening ends and records that it worked', () => {
        const t = setup();
        t.handlers().onStart();
        t.handlers().onResult('ନମସ୍କାର');
        t.handlers().onEnd();

        assert.deepEqual(t.submitted, ['ନମସ୍କାର']);
        assert.deepEqual(t.records, [['browser', 'or-IN', 'works']]);
        assert.equal(t.mic.disabled, false);
        assert.equal(t.indicator.classList.has('listening'), false);
    });

    it('says so when nothing was heard instead of submitting', () => {
        const t = setup();
        t.handlers().onStart();
        t.handlers().onEnd();

        assert.deepEqual(t.submitted, []);
        assert.match(t.statuses.at(-1), /Nothing was heard/);
    });

    it('records language-not-supported, points to the local engine and never switches silently', () => {
        const t = setup();
        t.handlers().onStart();
        t.handlers().onError('Odia is not supported by this browser.', 'language-not-supported');
        t.handlers().onEnd();

        assert.deepEqual(t.records, [['browser', 'or-IN', 'failed']]);
        assert.match(t.statuses.at(-1), /switch Voice engine to Local/);
        assert.deepEqual(t.submitted, []);
        assert.equal(t.recognizerCalls(), 0);
    });

    it('does not suggest the local engine for a language it has no model for', () => {
        const t = setup({ lang: 'es-ES' });
        t.handlers().onError('Spanish is not supported by this browser.', 'language-not-supported');
        assert.doesNotMatch(t.statuses.at(-1), /Local/);
    });

    it('keeps other errors from marking the language as failed', () => {
        const t = setup();
        t.handlers().onError('The microphone is blocked.', 'not-allowed');
        assert.deepEqual(t.records, []);
        assert.equal(t.statuses.at(-1), 'The microphone is blocked.');
    });

    it('tells the user when the browser has no speech recognition', () => {
        const t = setup({ supportedBrowser: false });
        assert.equal(t.binding.supported, false);
        assert.match(t.statuses.at(-1), /Chrome or Edge/);
        t.mic.click();
        assert.match(t.statuses.at(-1), /Switch Voice engine to Local/);
    });
});

describe('bindVoice with the local engine', () => {
    it('records, transcribes in the selected language and submits', async () => {
        let transcribed;
        const t = setup({
            engine: 'local',
            deps: { transcribeLocal: async (wav, lang) => { transcribed = { wav, lang }; return { ok: true, text: 'ନମସ୍କାର', error: null }; } },
        });

        t.mic.click();
        await tick();
        assert.match(t.statuses.at(-1), /Recording/);
        assert.equal(t.stop.disabled, false);
        assert.equal(t.recognizerCalls(), 0);

        t.stop.click();
        await tick();

        assert.equal(transcribed.lang, 'or');
        assert.deepEqual([...transcribed.wav], [9]);
        assert.deepEqual(t.submitted, ['ନମସ୍କାର']);
        assert.deepEqual(t.records, [['local', 'or-IN', 'works']]);
        assert.equal(t.mic.disabled, false);
    });

    it('refuses a language the local engine has no model for, without recording', async () => {
        let recorded = false;
        const t = setup({ engine: 'local', lang: 'es-ES', deps: { startRecording: async () => { recorded = true; } } });
        t.mic.click();
        await tick();

        assert.equal(recorded, false);
        assert.match(t.statuses.at(-1), /no model for Spanish/);
    });

    it('explains why when the service is not ready, and does not record', async () => {
        let recorded = false;
        const t = setup({
            engine: 'local',
            deps: {
                getSpeechStatus: async () => ({ ready: false, state: 'model_missing' }),
                startRecording: async () => { recorded = true; },
            },
        });
        t.mic.click();
        await tick();

        assert.equal(recorded, false);
        assert.match(t.statuses.at(-1), /model is not installed/);
        assert.equal(t.mic.disabled, false);
    });

    it('explains a blocked microphone', async () => {
        const t = setup({
            engine: 'local',
            deps: { startRecording: async () => { throw Object.assign(new Error('denied'), { name: 'NotAllowedError' }); } },
        });
        t.mic.click();
        await tick();

        assert.match(t.statuses.at(-1), /Microphone access is blocked/);
        assert.equal(t.mic.disabled, false);
    });

    it('explains when the page cannot record at all', async () => {
        const t = setup({ engine: 'local', deps: { canRecord: () => false } });
        t.mic.click();
        await tick();
        assert.match(t.statuses.at(-1), /cannot record audio/);
    });

    it('reports a transcription error and does not submit or mark support', async () => {
        const t = setup({
            engine: 'local',
            deps: { transcribeLocal: async () => ({ ok: false, text: '', error: 'The local speech service is not running.' }) },
        });
        t.mic.click();
        await tick();
        t.stop.click();
        await tick();

        assert.equal(t.statuses.at(-1), 'The local speech service is not running.');
        assert.deepEqual(t.submitted, []);
        assert.deepEqual(t.records, []);
        assert.equal(t.mic.disabled, false);
    });

    it('does not submit or claim success for an empty transcript', async () => {
        const t = setup({ engine: 'local', deps: { transcribeLocal: async () => ({ ok: true, text: '  ', error: null }) } });
        t.mic.click();
        await tick();
        t.stop.click();
        await tick();

        assert.match(t.statuses.at(-1), /No speech was recognised/);
        assert.deepEqual(t.submitted, []);
        assert.deepEqual(t.records, []);
    });

    it('reports an empty recording', async () => {
        const t = setup({
            engine: 'local',
            deps: { startRecording: async () => ({ finish: async () => { throw new Error('Nothing was recorded'); }, cancel() {} }) },
        });
        t.mic.click();
        await tick();
        t.stop.click();
        await tick();

        assert.equal(t.statuses.at(-1), 'Nothing was recorded. Try again.');
    });

    it('transcribes once when the time limit stops the recording', async () => {
        let calls = 0;
        let autoStop;
        const t = setup({
            engine: 'local',
            deps: {
                startRecording: async ({ onAutoStop }) => { autoStop = onAutoStop; return { finish: async () => new Uint8Array([1]), cancel() {} }; },
                transcribeLocal: async () => { calls++; return { ok: true, text: 'x', error: null }; },
            },
        });
        t.mic.click();
        await tick();
        autoStop();
        t.stop.click();
        await tick();

        assert.equal(calls, 1);
    });

    it('ignores a second mic click while recording', async () => {
        let started = 0;
        const t = setup({ engine: 'local', deps: { startRecording: async () => { started++; return { finish: async () => new Uint8Array([1]), cancel() {} }; } } });
        t.mic.click();
        await tick();
        t.mic.click();
        await tick();
        assert.equal(started, 1);
    });
});

describe('bindVoice clear button', () => {
    it('empties the text and the status', () => {
        const t = setup();
        t.input.value = 'something heard wrongly';
        t.clear.click();
        assert.equal(t.input.value, '');
        assert.equal(t.statuses.at(-1), '');
    });
});
