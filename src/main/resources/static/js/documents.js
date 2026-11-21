/**
 * Client for the knowledge document API (`/api/documents`). Like {@code postAlexaRequest}, every outcome
 * (success, HTTP error, network error) is normalised into one result object, so callers never need try/catch.
 *
 * @typedef {{ok:boolean,status:number,document:object|null,error:string|null}} DocumentResult
 */

export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
export const ALLOWED_EXTENSIONS = ['pdf', 'docx', 'txt', 'html', 'md'];
const ACCESS_GROUP_PATTERN = /^[a-z0-9_-]{1,64}$/;

/** Mirrors the server rules so obvious mistakes are caught before any bytes are sent. The server still decides. */
export function validateUpload(file, accessGroup) {
    if (!file) {
        return 'Choose a file first.';
    }
    const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '';
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
        return `Unsupported file type. Use ${ALLOWED_EXTENSIONS.join(', ')}.`;
    }
    if (file.size === 0) {
        return 'The file is empty.';
    }
    if (file.size > MAX_UPLOAD_BYTES) {
        return 'The file is larger than 20 MB.';
    }
    if (!ACCESS_GROUP_PATTERN.test(accessGroup)) {
        return 'Access group: 1-64 characters, lowercase letters, digits, "-" or "_".';
    }
    return null;
}

export async function uploadDocument(file, accessGroup, { baseUrl = '/api/documents', fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    const form = new FormData();
    form.append('file', file);
    form.append('access_group', accessGroup);
    return call(() => fetchImpl(baseUrl, { method: 'POST', body: form }));
}

export async function getDocument(id, accessGroup, { baseUrl = '/api/documents', fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    const url = `${baseUrl}/${encodeURIComponent(id)}?access_group=${encodeURIComponent(accessGroup)}`;
    return call(() => fetchImpl(url));
}

/**
 * Polls until the document leaves PENDING (or the attempts run out), reporting each state through
 * {@code onUpdate}. Indexing usually takes a few seconds; the first embedding call can take longer.
 */
export async function waitForIndexing(id, accessGroup, { onUpdate = () => {}, intervalMs = 1500, maxAttempts = 40, sleep = defaultSleep, ...api } = {}) {
    let last = null;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
        last = await getDocument(id, accessGroup, api);
        onUpdate(last);
        if (!last.ok || last.document.status !== 'PENDING') {
            return last;
        }
        await sleep(intervalMs);
    }
    return last;
}

const FAILURE_TEXT = {
    EXTRACTION_FAILED: 'The text could not be read from the file.',
    EMPTY_DOCUMENT: 'The file contains no readable text.',
    EMBEDDING_UNAVAILABLE: 'The embedding model (Ollama bge-m3) is not reachable.',
    EMBEDDING_MISMATCH: 'The embedding model returned an unexpected result.',
    STORAGE_FAILED: 'The chunks could not be saved to the database.',
};

/** One human-readable line for a document state. */
export function describeDocument(document) {
    if (document.status === 'INDEXED') {
        const language = { en: 'English', hi: 'Hindi', or: 'Odia' }[document.language] ?? document.language ?? 'unknown';
        const note = document.duplicate ? ' (identical content was already stored)' : '';
        return `Indexed: ${document.chunkCount} chunk(s), language ${language}, version ${document.version}${note}.`;
    }
    if (document.status === 'FAILED') {
        return `Failed: ${FAILURE_TEXT[document.failureReason] ?? 'indexing did not complete.'}`;
    }
    return 'Indexing in progress...';
}

/**
 * Asks a question about the uploaded documents of an access group.
 *
 * @returns {Promise<{ok:boolean,status:number,answer:{answer:string,grounded:boolean,sources:object[]}|null,error:string|null}>}
 */
export async function askDocuments(question, accessGroup, { url = '/api/knowledge/ask', fetchImpl = globalThis.fetch.bind(globalThis) } = {}) {
    const result = await call(() => fetchImpl(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ question, access_group: accessGroup }),
    }));
    return { ok: result.ok, status: result.status, answer: result.document, error: result.error };
}

async function call(request) {
    let response;
    try {
        response = await request();
    } catch (cause) {
        return { ok: false, status: 0, document: null, error: `Network error: ${cause.message}` };
    }

    const text = await response.text();
    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        // not JSON: handled below
    }

    if (!response.ok) {
        return { ok: false, status: response.status, document: null, error: errorText(response.status, body) };
    }
    if (body === null) {
        return { ok: false, status: response.status, document: null, error: 'The server reply was not valid JSON.' };
    }
    return { ok: true, status: response.status, document: body, error: null };
}

function errorText(status, body) {
    if (status === 404 && /static resource/i.test(body?.detail ?? '')) {
        return 'The knowledge service is not enabled. Start the app with SPRING_PROFILES_ACTIVE=local,knowledge.';
    }
    if (status === 404) {
        return 'Document not found in this access group.';
    }
    if (status === 429) {
        return 'The server is busy (indexing queue or model is full). Try again in a moment.';
    }
    if (status === 503) {
        return 'The model is not reachable. Check that Ollama is running, then try again.';
    }
    return body?.detail ?? `HTTP ${status}`;
}

function defaultSleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
