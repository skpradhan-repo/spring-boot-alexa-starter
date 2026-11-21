import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
    describeDocument, getDocument, uploadDocument, validateUpload, waitForIndexing,
} from '../../main/resources/static/js/documents.js';

const reply = (status, body) => async () => ({ ok: status < 400, status, text: async () => (typeof body === 'string' ? body : JSON.stringify(body)) });
const file = (name, size = 10) => ({ name, size });
const doc = (over = {}) => ({ id: 'abc', name: 'a.txt', version: 1, status: 'INDEXED', language: 'or', chunkCount: 2, failureReason: null, duplicate: false, ...over });

describe('validateUpload', () => {
    it('accepts an allowed file and group', () => {
        assert.equal(validateUpload(file('Policy.PDF'), 'hr'), null);
    });

    it('rejects a missing file, wrong type, empty file and oversized file', () => {
        assert.match(validateUpload(null, 'hr'), /Choose a file/);
        assert.match(validateUpload(file('x.exe'), 'hr'), /Unsupported file type/);
        assert.match(validateUpload(file('noextension'), 'hr'), /Unsupported file type/);
        assert.match(validateUpload(file('a.txt', 0), 'hr'), /empty/);
        assert.match(validateUpload(file('a.txt', 21 * 1024 * 1024), 'hr'), /larger than 20 MB/);
    });

    it('rejects an invalid access group', () => {
        assert.match(validateUpload(file('a.txt'), 'HR Team'), /Access group/);
        assert.match(validateUpload(file('a.txt'), ''), /Access group/);
    });
});

describe('uploadDocument', () => {
    it('posts multipart form data with file and access_group', async () => {
        let seen;
        const result = await uploadDocument(new Blob(['hello']), 'hr', {
            fetchImpl: async (url, init) => { seen = { url, init }; return reply(202, doc({ status: 'PENDING' }))(); },
        });

        assert.equal(result.ok, true);
        assert.equal(result.status, 202);
        assert.equal(seen.url, '/api/documents');
        assert.equal(seen.init.method, 'POST');
        assert.equal(seen.init.body.get('access_group'), 'hr');
        assert.ok(seen.init.body.get('file'));
    });

    it('explains a missing knowledge profile', async () => {
        const result = await uploadDocument(new Blob(['x']), 'hr', {
            fetchImpl: reply(404, { detail: 'No static resource api/documents.' }),
        });

        assert.equal(result.ok, false);
        assert.match(result.error, /not enabled/);
        assert.match(result.error, /knowledge/);
    });

    it('maps 429, server detail and network failures to readable errors', async () => {
        assert.match((await uploadDocument(new Blob(['x']), 'hr', { fetchImpl: reply(429, {}) })).error, /queue is full/);
        assert.match((await uploadDocument(new Blob(['x']), 'hr', { fetchImpl: reply(415, { detail: 'Unsupported document type' }) })).error, /Unsupported document type/);
        const offline = await uploadDocument(new Blob(['x']), 'hr', { fetchImpl: async () => { throw new TypeError('offline'); } });
        assert.equal(offline.status, 0);
        assert.match(offline.error, /Network error: offline/);
    });

    it('reports a non-JSON success body', async () => {
        const result = await uploadDocument(new Blob(['x']), 'hr', { fetchImpl: reply(200, '<html>') });
        assert.equal(result.ok, false);
        assert.match(result.error, /not valid JSON/);
    });
});

describe('getDocument', () => {
    it('encodes the id and access group in the URL', async () => {
        let url;
        await getDocument('a b', 'hr&x', { fetchImpl: async (u) => { url = u; return reply(200, doc())(); } });
        assert.equal(url, '/api/documents/a%20b?access_group=hr%26x');
    });

    it('reports a document in another group as not found', async () => {
        const result = await getDocument('abc', 'finance', { fetchImpl: reply(404, { detail: 'Document not found' }) });
        assert.match(result.error, /not found/i);
    });
});

describe('waitForIndexing', () => {
    const noSleep = async () => {};

    it('polls until the document is no longer PENDING', async () => {
        const states = ['PENDING', 'PENDING', 'INDEXED'];
        const seen = [];
        const result = await waitForIndexing('abc', 'hr', {
            fetchImpl: async () => reply(200, doc({ status: states.shift() }))(),
            sleep: noSleep,
            onUpdate: (r) => seen.push(r.document.status),
        });

        assert.equal(result.document.status, 'INDEXED');
        assert.deepEqual(seen, ['PENDING', 'PENDING', 'INDEXED']);
    });

    it('stops on an error', async () => {
        let calls = 0;
        const result = await waitForIndexing('abc', 'hr', {
            fetchImpl: async () => { calls++; return reply(404, {})(); },
            sleep: noSleep,
        });

        assert.equal(result.ok, false);
        assert.equal(calls, 1);
    });

    it('gives up after maxAttempts and returns the last PENDING state', async () => {
        let calls = 0;
        const result = await waitForIndexing('abc', 'hr', {
            fetchImpl: async () => { calls++; return reply(200, doc({ status: 'PENDING' }))(); },
            sleep: noSleep,
            maxAttempts: 3,
        });

        assert.equal(calls, 3);
        assert.equal(result.document.status, 'PENDING');
    });
});

describe('describeDocument', () => {
    it('describes an indexed document with its language', () => {
        assert.match(describeDocument(doc()), /Indexed: 2 chunk\(s\), language Odia, version 1/);
        assert.match(describeDocument(doc({ language: 'hi' })), /Hindi/);
        assert.match(describeDocument(doc({ language: 'en' })), /English/);
    });

    it('mentions duplicates', () => {
        assert.match(describeDocument(doc({ duplicate: true })), /already stored/);
    });

    it('explains failures and pending state', () => {
        assert.match(describeDocument(doc({ status: 'FAILED', failureReason: 'EMBEDDING_UNAVAILABLE' })), /Ollama/);
        assert.match(describeDocument(doc({ status: 'FAILED', failureReason: 'SOMETHING_NEW' })), /did not complete/);
        assert.match(describeDocument(doc({ status: 'PENDING' })), /in progress/);
    });
});
