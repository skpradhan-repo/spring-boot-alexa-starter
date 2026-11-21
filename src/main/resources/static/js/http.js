/**
 * Shared JSON call helper for the browser APIs (`/api/chat`, `/api/documents`, `/api/knowledge/ask`). Every
 * outcome (success, HTTP error, network error, non-JSON body) is normalised into one result object, so callers
 * never need try/catch.
 *
 * @typedef {{ok:boolean,status:number,data:object|null,error:string|null}} JsonResult
 */

/**
 * @param {() => Promise<Response>} request performs the fetch
 * @param {(status:number, body:object|null) => string} mapError turns a failed status into a readable message
 * @returns {Promise<JsonResult>}
 */
export async function callJson(request, mapError = statusMessage) {
    let response;
    try {
        response = await request();
    } catch (cause) {
        return { ok: false, status: 0, data: null, error: `Network error: ${cause.message}` };
    }

    const text = await response.text();
    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        // not JSON: handled below
    }

    if (!response.ok) {
        return { ok: false, status: response.status, data: null, error: mapError(response.status, body) };
    }
    if (body === null) {
        return { ok: false, status: response.status, data: null, error: 'The server reply was not valid JSON.' };
    }
    return { ok: true, status: response.status, data: body, error: null };
}

/** Messages that are the same for every API: the model is busy, the model is down, or the server's own detail. */
export function statusMessage(status, body) {
    if (status === 429) {
        return 'The server is busy (indexing queue or model is full). Try again in a moment.';
    }
    if (status === 503) {
        return 'The model is not reachable. Check that Ollama is running, then try again.';
    }
    return body?.detail ?? `HTTP ${status}`;
}
