/**
 * Posts a request body to the skill webhook and normalises every outcome (success, HTTP error, network error,
 * non-JSON reply) into one result object, so callers never need try/catch.
 *
 * The body is sent exactly as given (a string), so the developer-mode JSON editor can deliberately send
 * malformed JSON to see how the backend reacts.
 *
 * @returns {Promise<{ok:boolean,status:number,durationMs:number,rawText:string,body:object|null,error:string|null}>}
 */
export async function postAlexaRequest(bodyText, { url = '/alexa', fetchImpl = globalThis.fetch.bind(globalThis), clock = () => performance.now() } = {}) {
    const started = clock();
    let response;
    try {
        response = await fetchImpl(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: bodyText,
        });
    } catch (cause) {
        return failure(0, clock() - started, '', `Network error: ${cause.message}`);
    }

    const rawText = await response.text();
    const durationMs = clock() - started;
    let body = null;
    try {
        body = rawText ? JSON.parse(rawText) : null;
    } catch {
        // handled below: not JSON
    }

    if (!response.ok) {
        // ProblemDetail uses "detail"; older error shapes used "message".
        const reason = body?.detail ?? body?.message ?? `HTTP ${response.status}`;
        return { ok: false, status: response.status, durationMs, rawText, body, error: `Backend rejected the request: ${reason}` };
    }
    if (body === null) {
        return failure(response.status, durationMs, rawText, 'Backend reply was not valid JSON');
    }
    return { ok: true, status: response.status, durationMs, rawText, body, error: null };
}

function failure(status, durationMs, rawText, error) {
    return { ok: false, status, durationMs, rawText, body: null, error };
}
