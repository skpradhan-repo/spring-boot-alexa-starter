import { callJson } from './http.js';

/**
 * General question to the local model (`POST /api/chat`). The model answers from its own knowledge; nothing is
 * looked up in uploaded documents.
 *
 * @returns {Promise<{ok:boolean,status:number,answer:string|null,error:string|null}>}
 */
export async function askAssistant(message, { url = '/api/chat', fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    const result = await callJson(() => fetchImpl(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
    }));
    return { ok: result.ok, status: result.status, answer: result.data?.answer ?? null, error: result.error };
}
