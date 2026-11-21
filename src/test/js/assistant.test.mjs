import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
    DEFAULT_VOICE_LANGUAGE, VOICE_LANGUAGES, describeSpeechError, languageLabel, pickVoice, speechLanguageFor,
} from '../../main/resources/static/js/languages.js';
import { askAssistant } from '../../main/resources/static/js/chat.js';
import { callJson, statusMessage } from '../../main/resources/static/js/http.js';

const reply = (status, body) => async () => ({ ok: status < 400, status, text: async () => (typeof body === 'string' ? body : JSON.stringify(body)) });

describe('speechLanguageFor', () => {
    it('picks the language of the dominant non-Latin script', () => {
        assert.equal(speechLanguageFor('\u0915\u0930\u094D\u092E\u091A\u093E\u0930\u0940 \u0915\u094B \u091B\u0941\u091F\u094D\u091F\u0940'), 'hi-IN');
        assert.equal(speechLanguageFor('\u0B15\u0B30\u0B4D\u0B2E\u0B1A\u0B3E\u0B30\u0B40\u0B19\u0B4D\u0B15 \u0B1B\u0B41\u0B1F\u0B3F'), 'or-IN');
        assert.equal(speechLanguageFor('\u0B85\u0BA9\u0BC8\u0BA4\u0BCD\u0BA4\u0BC1 \u0BAA\u0BA3\u0BBF'), 'ta-IN');
        assert.equal(speechLanguageFor('\u0645\u0648\u0638\u0641\u0648\u0646 \u0625\u062C\u0627\u0632\u0629'), 'ar-SA');
    });

    it('speaks Latin text in English even when a Hindi voice is selected', () => {
        assert.equal(speechLanguageFor('Employees get 24 days of leave.', 'hi-IN'), DEFAULT_VOICE_LANGUAGE);
    });

    it('keeps the selected English variant for English text', () => {
        assert.equal(speechLanguageFor('Hello there', 'en-US'), 'en-US');
    });

    it('lets the dominant script win in mixed text', () => {
        assert.equal(speechLanguageFor('HR policy \u0915\u0930\u094D\u092E\u091A\u093E\u0930\u0940 \u0915\u094B \u091B\u0941\u091F\u094D\u091F\u0940 \u092E\u093F\u0932\u0924\u0940'), 'hi-IN');
        assert.equal(speechLanguageFor('This policy is called \u091B\u0941\u091F\u094D\u091F\u0940'), DEFAULT_VOICE_LANGUAGE);
    });

    it('falls back to the default for empty text or digits only', () => {
        assert.equal(speechLanguageFor('', 'hi-IN'), DEFAULT_VOICE_LANGUAGE);
        assert.equal(speechLanguageFor(null), DEFAULT_VOICE_LANGUAGE);
        assert.equal(speechLanguageFor('12345 !!!'), DEFAULT_VOICE_LANGUAGE);
    });
});

describe('pickVoice', () => {
    const voices = [{ lang: 'en-US', name: 'a' }, { lang: 'hi_IN', name: 'b' }, { lang: 'ta-IN', name: 'c' }];

    it('prefers an exact locale, then the same base language, then nothing', () => {
        assert.equal(pickVoice(voices, 'hi-IN').name, 'b');
        assert.equal(pickVoice(voices, 'en-IN').name, 'a');
        assert.equal(pickVoice(voices, 'or-IN'), null);
        assert.equal(pickVoice([], 'en-IN'), null);
    });
});

describe('voice language list', () => {
    it('includes English, Hindi and Odia, with unique codes', () => {
        const codes = VOICE_LANGUAGES.map((language) => language.code);
        assert.ok(['en-IN', 'hi-IN', 'or-IN'].every((code) => codes.includes(code)));
        assert.equal(new Set(codes).size, codes.length);
        assert.equal(languageLabel('or-IN'), 'Odia');
        assert.equal(languageLabel('xx-XX'), 'xx-XX');
    });
});

describe('describeSpeechError', () => {
    it('explains common failures', () => {
        assert.match(describeSpeechError('not-allowed'), /Microphone access is blocked/);
        assert.match(describeSpeechError('no-speech'), /No speech was heard/);
        assert.match(describeSpeechError('audio-capture'), /No microphone/);
        assert.match(describeSpeechError('network'), /internet/);
        assert.match(describeSpeechError('language-not-supported', 'or-IN'), /Odia/);
        assert.match(describeSpeechError('weird'), /weird/);
    });

    it('stays silent when the user stopped listening', () => {
        assert.equal(describeSpeechError('aborted'), '');
    });
});

describe('callJson', () => {
    it('returns parsed data on success', async () => {
        const result = await callJson(reply(200, { a: 1 }));
        assert.deepEqual(result, { ok: true, status: 200, data: { a: 1 }, error: null });
    });

    it('reports network failures, non-JSON bodies and HTTP errors', async () => {
        assert.match((await callJson(async () => { throw new TypeError('offline'); })).error, /Network error: offline/);
        assert.match((await callJson(reply(200, '<html>'))).error, /not valid JSON/);
        assert.equal((await callJson(reply(500, { detail: 'boom' }))).error, 'boom');
        assert.equal((await callJson(reply(500, 'plain'))).error, 'HTTP 500');
    });

    it('uses the supplied error mapper', async () => {
        const result = await callJson(reply(404, {}), (status) => `custom ${status}`);
        assert.equal(result.error, 'custom 404');
    });
});

describe('statusMessage', () => {
    it('maps busy and unavailable to readable text', () => {
        assert.match(statusMessage(429, null), /busy/);
        assert.match(statusMessage(503, null), /Ollama/);
        assert.equal(statusMessage(400, { detail: 'bad' }), 'bad');
    });
});

describe('askAssistant', () => {
    it('posts the message as JSON and returns the answer text', async () => {
        let seen;
        const result = await askAssistant('What is leave?', {
            fetchImpl: async (url, init) => { seen = { url, init }; return reply(200, { answer: 'Time off.' })(); },
        });

        assert.equal(result.ok, true);
        assert.equal(result.answer, 'Time off.');
        assert.equal(seen.url, '/api/chat');
        assert.equal(seen.init.method, 'POST');
        assert.deepEqual(JSON.parse(seen.init.body), { message: 'What is leave?' });
    });

    it('explains a down model, a busy server and a network failure', async () => {
        const down = await askAssistant('q', { fetchImpl: reply(503, {}) });
        assert.equal(down.ok, false);
        assert.equal(down.answer, null);
        assert.match(down.error, /Ollama/);
        assert.match((await askAssistant('q', { fetchImpl: reply(429, {}) })).error, /busy/);
        assert.match((await askAssistant('q', { fetchImpl: async () => { throw new TypeError('offline'); } })).error, /Network error/);
    });
});
