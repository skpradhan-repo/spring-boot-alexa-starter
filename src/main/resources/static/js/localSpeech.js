import { callJson } from './http.js';

/**
 * Client for the optional local speech engine (`/api/speech`). The recording is sent to this application's own
 * server, which forwards it to a service on the same machine; it is never sent to a third party.
 */

function errorText(status, body) {
    if (status === 404) {
        return 'The local speech engine is not enabled. Start the app with ASSISTANT_SPEECH_ENABLED=true and run the speech service (see the README).';
    }
    if (status === 503) {
        return 'The local speech service is not running, is still loading, or is busy with another recording.';
    }
    if (status === 415) {
        return 'The server did not accept this audio format.';
    }
    return body?.detail ?? `HTTP ${status}`;
}

/**
 * @returns {Promise<{ok:boolean,ready:boolean,state:string,languages:string[],error:string|null}>}
 */
export async function getSpeechStatus({ url = '/api/speech/status', fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    const result = await callJson(() => fetchImpl(url), errorText);
    if (!result.ok) {
        return { ok: false, ready: false, state: result.status === 404 ? 'disabled' : 'unreachable', languages: [], error: result.error };
    }
    return {
        ok: true,
        ready: result.data.ready === true,
        state: result.data.state ?? 'error',
        languages: result.data.languages ?? [],
        error: null,
    };
}

/** What to tell the user for a service state, or an empty string when it is ready. */
export function describeSpeechState(state) {
    switch (state) {
        case 'ready':
            return '';
        case 'loading':
            return 'The local speech model is still loading. Wait a minute and try again.';
        case 'model_missing':
            return 'The local speech model is not installed. Follow "Local speech engine" in the README to download it.';
        case 'disabled':
            return 'The local speech engine is not enabled. Start the app with ASSISTANT_SPEECH_ENABLED=true (see the README).';
        case 'unreachable':
            return 'The local speech service is not running. Start it as described in the README.';
        default:
            return 'The local speech service reported an error. Check its console.';
    }
}

/**
 * @param {Uint8Array} wav 16 kHz mono 16-bit WAV
 * @param {string} lang model language code, for example "or"
 * @returns {Promise<{ok:boolean,text:string,error:string|null}>}
 */
export async function transcribeLocal(wav, lang, { baseUrl = '/api/speech/transcribe', fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    const result = await callJson(() => fetchImpl(`${baseUrl}?lang=${encodeURIComponent(lang)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'audio/wav' },
        body: wav,
    }), errorText);
    return { ok: result.ok, text: result.data?.text ?? '', error: result.error };
}
