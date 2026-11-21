import { postAlexaRequest } from './js/api.js';
import { askAssistant } from './js/chat.js';
import { askDocuments, describeDocument, uploadDocument, validateUpload, waitForIndexing } from './js/documents.js';
import { h } from './js/dom.js';
import { INTENTS, REQUEST_TYPES, SESSION_END_REASONS, findIntent } from './js/interactionModel.js';
import { DEFAULT_VOICE_LANGUAGE, VOICE_LANGUAGES } from './js/languages.js';
import { Simulator, DEFAULT_SKILL_ID } from './js/simulator.js';
import { SimulatorSession } from './js/session.js';
import { cancelSpeech, describeSpeakResult, speak } from './js/speech.js';
import { bindVoice } from './js/voice.js';

const $ = (id) => document.getElementById(id);

const simulator = new Simulator({
    session: new SimulatorSession(),
    post: postAlexaRequest,
});

// ---------------------------------------------------------------- mode toggle

const MODE_KEY = 'alexa-simulator-mode';

function setMode(mode) {
    document.body.dataset.mode = mode;
    $('modeSimple').setAttribute('aria-pressed', String(mode === 'simple'));
    $('modeDeveloper').setAttribute('aria-pressed', String(mode === 'developer'));
    try {
        localStorage.setItem(MODE_KEY, mode);
    } catch {
        // storage can be unavailable (private mode); the toggle still works for this page view
    }
    if (mode === 'developer') {
        refreshEditorFromBuilder();
    }
}

$('modeSimple').addEventListener('click', () => setMode('simple'));
$('modeDeveloper').addEventListener('click', () => setMode('developer'));

// ---------------------------------------------------------------- request builder

function fillSelect(select, values, labelOf = (value) => value) {
    select.replaceChildren(...values.map((value) => h('option', { value, text: labelOf(value) })));
}

fillSelect($('requestType'), REQUEST_TYPES);
fillSelect($('intentSelect'), INTENTS.map((intent) => intent.name));
fillSelect($('endReason'), SESSION_END_REASONS);
$('requestType').value = 'IntentRequest';
$('skillId').value = DEFAULT_SKILL_ID;

/** Shows only the builder fields relevant to the selected request type. */
function syncBuilderVisibility() {
    const type = $('requestType').value;
    document.querySelectorAll('[data-for]').forEach((element) => {
        element.hidden = element.dataset.for !== type;
    });
}

function renderSlotFields() {
    const intent = findIntent($('intentSelect').value);
    const rows = (intent?.slots ?? []).flatMap((slot) => [
        h('label', { for: `slot-${slot.name}`, text: slot.name }),
        h('input', {
            type: 'text',
            id: `slot-${slot.name}`,
            'data-slot': slot.name,
            placeholder: slot.sample,
            autocomplete: 'off',
        }),
    ]);
    $('slotFields').replaceChildren(...rows);
    if (rows.length === 0) {
        $('slotFields').append(h('span', { class: 'hint', text: 'This intent has no slots.' }));
    }
}

/** Reads the builder form into a request specification. */
function readSpec() {
    const type = $('requestType').value;
    if (type === 'LaunchRequest') {
        return { type };
    }
    if (type === 'SessionEndedRequest') {
        return { type, reason: $('endReason').value };
    }
    const slots = {};
    $('slotFields').querySelectorAll('input[data-slot]').forEach((input) => {
        slots[input.dataset.slot] = input.value;
    });
    return { type, intentName: $('intentSelect').value, slots };
}

function refreshEditorFromBuilder() {
    $('jsonEditor').value = JSON.stringify(simulator.previewEnvelope(readSpec()), null, 2);
}

$('requestType').addEventListener('change', () => {
    syncBuilderVisibility();
    refreshEditorFromBuilder();
});
$('intentSelect').addEventListener('change', () => {
    renderSlotFields();
    refreshEditorFromBuilder();
});
$('endReason').addEventListener('change', refreshEditorFromBuilder);
$('slotFields').addEventListener('input', refreshEditorFromBuilder);
$('resetEditor').addEventListener('click', refreshEditorFromBuilder);
$('skillId').addEventListener('change', () => {
    simulator.setSkillId($('skillId').value);
    refreshEditorFromBuilder();
});

$('sendBuilder').addEventListener('click', () => simulator.sendSpec(readSpec()));
$('sendJson').addEventListener('click', () => simulator.sendRaw($('jsonEditor').value));

// ---------------------------------------------------------------- tabs and voice settings

const TABS = ['alexa', 'chat', 'docs'];
const LANG_KEY = 'assistant-voice-language';

function setTab(tab) {
    document.body.dataset.tab = tab;
    for (const name of TABS) {
        const selected = name === tab;
        const button = document.querySelector(`.tab[data-tab="${name}"]`);
        button.setAttribute('aria-selected', String(selected));
        button.tabIndex = selected ? 0 : -1;
    }
    $('paneAlexa').hidden = tab !== 'alexa';
    $('paneChat').hidden = tab !== 'chat';
    $('paneDocs').hidden = tab !== 'docs';
    cancelSpeech();
}

document.querySelectorAll('.tab').forEach((button) => {
    button.addEventListener('click', () => setTab(button.dataset.tab));
    button.addEventListener('keydown', (event) => {
        const step = { ArrowRight: 1, ArrowLeft: -1 }[event.key];
        if (!step) {
            return;
        }
        const next = TABS[(TABS.indexOf(button.dataset.tab) + step + TABS.length) % TABS.length];
        setTab(next);
        document.querySelector(`.tab[data-tab="${next}"]`).focus();
    });
});

fillSelect($('voiceLang'), VOICE_LANGUAGES.map((language) => language.code),
    (code) => VOICE_LANGUAGES.find((language) => language.code === code).label);
try {
    const saved = localStorage.getItem(LANG_KEY);
    $('voiceLang').value = VOICE_LANGUAGES.some((language) => language.code === saved) ? saved : DEFAULT_VOICE_LANGUAGE;
} catch {
    $('voiceLang').value = DEFAULT_VOICE_LANGUAGE;
}
$('voiceLang').addEventListener('change', () => {
    try {
        localStorage.setItem(LANG_KEY, $('voiceLang').value);
    } catch {
        // storage can be unavailable; the selection still applies to this page view
    }
});

const voiceLang = () => $('voiceLang').value;

/** Reads a reply aloud when enabled, and tells the user when that is not possible instead of staying silent. */
function speakReply(text, setStatusFn) {
    if (!$('speakReplies').checked) {
        return;
    }
    const problem = describeSpeakResult(speak(text, voiceLang()), text, voiceLang());
    if (problem) {
        setStatusFn(problem);
    }
}

// ---------------------------------------------------------------- Q&A logs (tabs 2 and 3)

const ASSISTANT_LABEL = 'Assistant';

/** A chat log for the tabs that call plain REST endpoints (the Alexa tab renders from simulator state). */
function createLog(element, emptyText) {
    const empty = () => h('p', { class: 'empty', text: emptyText });
    return {
        add(role, label, text, ...extras) {
            element.querySelector('.empty')?.remove();
            element.append(h('div', { class: `bubble ${role}` },
                h('span', { class: 'tag', text: label }),
                document.createTextNode(text),
                ...extras));
            element.scrollTop = element.scrollHeight;
        },
        clear() {
            element.replaceChildren(empty());
        },
    };
}

const generalLog = createLog($('generalLog'), 'Ask any question. The local model answers from its own knowledge, not from your documents.');
const documentLog = createLog($('askLog'), 'Upload a document, then ask about it by voice or text. Answers come only from your documents and show their sources.');

function sourcesBlock(sources) {
    if (!sources.length) {
        return null;
    }
    return h('div', { class: 'extra' },
        ...sources.map((source) => h('div', {},
            h('strong', { text: `${source.document} (v${source.version}, ${Math.round(source.score * 100)}% match)` }),
            h('div', { text: source.excerpt }))));
}

// ---------------------------------------------------------------- tab 2: ask anything

let generalBusy = false;

function setGeneralStatus(text) {
    $('generalStatus').textContent = text;
}

async function handleGeneral() {
    const question = $('generalInput').value.trim();
    if (!question) {
        setGeneralStatus('Type or speak a question first.');
        return;
    }
    if (generalBusy) {
        return;
    }
    generalBusy = true;
    $('generalSend').disabled = true;
    $('generalInput').value = '';
    generalLog.add('user', 'You', question);
    setGeneralStatus('Thinking... a local model can take several seconds.');
    try {
        const result = await askAssistant(question);
        if (!result.ok) {
            generalLog.add('error', 'Error', result.error);
            setGeneralStatus(result.error);
            return;
        }
        generalLog.add('alexa', ASSISTANT_LABEL, result.answer);
        setGeneralStatus('');
        speakReply(result.answer, setGeneralStatus);
    } finally {
        generalBusy = false;
        $('generalSend').disabled = false;
    }
}

$('generalSend').addEventListener('click', handleGeneral);
$('generalInput').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
        handleGeneral();
    }
});
$('generalClear').addEventListener('click', () => {
    cancelSpeech();
    generalLog.clear();
    setGeneralStatus('');
});
bindVoice({
    micButton: $('generalMic'),
    stopButton: $('generalStop'),
    input: $('generalInput'),
    submit: handleGeneral,
    setStatus: setGeneralStatus,
    getLang: voiceLang,
    indicator: $('generalLog'),
});

// ---------------------------------------------------------------- tab 1: chat input and voice

async function sendMessage() {
    const text = $('messageInput').value;
    if (!text.trim()) {
        setStatus('Please type or speak something first.');
        return;
    }
    $('messageInput').value = '';
    await simulator.say(text);
}

function setStatus(text) {
    $('status').textContent = text;
}

$('sendButton').addEventListener('click', sendMessage);
$('messageInput').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
        sendMessage();
    }
});

bindVoice({
    micButton: $('micButton'),
    stopButton: $('stopMicButton'),
    input: $('messageInput'),
    submit: sendMessage,
    setStatus,
    getLang: voiceLang,
    indicator: $('history'),
});

// ---------------------------------------------------------------- tab 3: knowledge documents

const documentRows = new Map();

function renderDocumentRow(id, name, state, detail) {
    const row = documentRows.get(id) ?? h('li');
    documentRows.set(id, row);
    row.replaceChildren(
        h('span', { class: 'doc-name', text: name }),
        h('span', { class: `badge ${state.toLowerCase()}`, text: state }),
        h('div', { class: 'doc-detail', text: detail }),
        h('div', { class: 'doc-detail' }, h('code', { text: id })),
    );
    $('docList').prepend(row);
}

async function handleUpload() {
    const file = $('docFile').files[0];
    const group = $('docGroup').value.trim();
    const problem = validateUpload(file, group);
    if (problem) {
        $('docStatus').textContent = problem;
        return;
    }

    $('docUpload').disabled = true;
    $('docStatus').textContent = `Uploading ${file.name}...`;
    try {
        const uploaded = await uploadDocument(file, group);
        if (!uploaded.ok) {
            $('docStatus').textContent = uploaded.error;
            return;
        }
        const { id, name } = uploaded.document;
        renderDocumentRow(id, name, uploaded.document.status, describeDocument(uploaded.document));
        $('docStatus').textContent = uploaded.document.duplicate
            ? 'Identical content was already uploaded to this group.'
            : 'Accepted. Indexing in the background...';

        const finished = await waitForIndexing(id, group, {
            onUpdate: (result) => result.ok && renderDocumentRow(id, name, result.document.status, describeDocument(result.document)),
        });
        if (!finished.ok) {
            $('docStatus').textContent = finished.error;
        } else if (finished.document.status === 'PENDING') {
            $('docStatus').textContent = 'Still indexing. The first embedding call can be slow; check again shortly.';
        } else {
            $('docStatus').textContent = describeDocument({ ...finished.document, duplicate: uploaded.document.duplicate });
            $('docFile').value = '';
        }
    } finally {
        $('docUpload').disabled = false;
    }
}

$('docUpload').addEventListener('click', handleUpload);

// ---------------------------------------------------------------- tab 3: ask your documents

let askBusy = false;

function setAskStatus(text) {
    $('askStatus').textContent = text;
}

async function handleAsk() {
    const question = $('askInput').value.trim();
    const group = $('docGroup').value.trim();
    if (!question) {
        setAskStatus('Type or speak a question first.');
        return;
    }
    if (!group) {
        setAskStatus('Enter the access group the documents were uploaded to.');
        return;
    }
    if (askBusy) {
        return;
    }

    askBusy = true;
    $('askButton').disabled = true;
    $('askInput').value = '';
    documentLog.add('user', 'You', question);
    setAskStatus('Searching your documents and asking the model... this can take several seconds.');
    try {
        const result = await askDocuments(question, group);
        if (!result.ok) {
            documentLog.add('error', 'Error', result.error);
            setAskStatus(result.error);
            return;
        }
        const { answer, grounded, sources } = result.answer;
        documentLog.add('alexa', ASSISTANT_LABEL, answer, sourcesBlock(sources));
        setAskStatus(grounded
            ? `Answered from ${sources.length} passage(s).`
            : `Nothing in access group "${group}" matched. Check the group name and that the document shows INDEXED.`);
        speakReply(answer, setAskStatus);
    } finally {
        askBusy = false;
        $('askButton').disabled = false;
    }
}

$('askButton').addEventListener('click', handleAsk);
$('askInput').addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
        handleAsk();
    }
});
$('askClear').addEventListener('click', () => {
    cancelSpeech();
    documentLog.clear();
    setAskStatus('');
});
bindVoice({
    micButton: $('askMic'),
    stopButton: $('askStop'),
    input: $('askInput'),
    submit: handleAsk,
    setStatus: setAskStatus,
    getLang: voiceLang,
    indicator: $('askLog'),
});

// ---------------------------------------------------------------- session controls

$('newSession').addEventListener('click', () => simulator.newSession());
$('clearConversation').addEventListener('click', () => {
    cancelSpeech();
    simulator.clearConversation();
    refreshEditorFromBuilder();
});
$('dismissErrors').addEventListener('click', () => simulator.dismissErrors());

// ---------------------------------------------------------------- rendering

const ROLE_LABEL = { user: 'You', alexa: 'Alexa', error: 'Error', system: '' };

function renderBubble(entry) {
    const children = [];
    if (ROLE_LABEL[entry.role]) {
        const tag = entry.role === 'user' && entry.intent ? `${ROLE_LABEL.user} - ${entry.intent}` : ROLE_LABEL[entry.role];
        children.push(h('span', { class: 'tag', text: tag }));
    }
    children.push(document.createTextNode(entry.text));

    const summary = entry.summary;
    if (summary) {
        const extras = [];
        if (summary.reprompt) {
            extras.push(h('div', { text: `Reprompt: ${summary.reprompt}` }));
        }
        if (summary.card) {
            extras.push(h('div', { text: `Card - ${summary.card.title}: ${summary.card.text}` }));
        }
        const chips = [
            ...summary.directives,
            summary.endSession === false ? 'session open' : 'session ended',
        ];
        extras.push(h('div', {}, ...chips.map((chip) => h('span', { class: 'chip', text: chip }))));
        children.push(h('div', { class: 'extra' }, ...extras));
    }
    return h('div', { class: `bubble ${entry.role}` }, ...children);
}

let lastSpokenId = 0;

function renderHistory(state) {
    const history = $('history');
    if (state.history.length === 0) {
        history.replaceChildren(h('p', { class: 'empty', text: 'Say or type something, for example "order a large coffee", "help" or "open".' }));
        return;
    }
    const nearBottom = history.scrollHeight - history.scrollTop - history.clientHeight < 60;
    history.replaceChildren(...state.history.map(renderBubble));
    if (nearBottom) {
        history.scrollTop = history.scrollHeight;
    }
}

function renderErrors(state) {
    $('errorPanel').hidden = state.errors.length === 0;
    $('errorList').replaceChildren(...state.errors.map((error) => h('li', { text: error.message })));
}

function renderInspectors(state) {
    const { exchange, session } = state;
    $('rawRequest').textContent = exchange?.request ?? 'No request sent yet.';
    $('rawResponse').textContent = exchange?.response ?? 'No response yet.';
    $('exchangeMeta').textContent = exchange ? `HTTP ${exchange.status || 'n/a'} - ${exchange.durationMs} ms` : '';

    const rows = [
        ['Status', h('span', { class: `badge ${session.status}`, text: session.status })],
        ['Session ID', session.sessionId ?? '-'],
        ['New session', session.status === 'open' ? String(session.isNew) : '-'],
        ['Turns', String(session.turns)],
        ['Waiting for', session.awaiting ? `${session.awaiting.slot} (${session.awaiting.intent ?? 'unknown intent'})` : '-'],
    ];
    $('sessionInfo').replaceChildren(...rows.flatMap(([name, value]) => [h('dt', { text: name }), h('dd', {}, value)]));
    $('sessionAttributes').textContent = JSON.stringify(session.attributes, null, 2);
}

function speakNewReply(state) {
    const latest = state.history.at(-1);
    if (latest?.role === 'alexa' && latest.id !== lastSpokenId) {
        lastSpokenId = latest.id;
        speakReply(latest.text, setStatus);
    }
}

simulator.subscribe((state) => {
    renderHistory(state);
    renderErrors(state);
    renderInspectors(state);
    speakNewReply(state);

    $('sendButton').disabled = state.busy;
    $('sendBuilder').disabled = state.busy;
    $('sendJson').disabled = state.busy;
    if (state.busy) {
        setStatus('Waiting for the skill...');
    } else if (state.errors.length > 0 && state.history.at(-1)?.role === 'error') {
        setStatus('Something went wrong - see the error panel.');
    } else if (state.history.length > 0) {
        setStatus(state.session.status === 'open' ? 'Session open - you can keep talking.' : 'Session ended.');
    }
});

// ---------------------------------------------------------------- start

syncBuilderVisibility();
renderSlotFields();
renderInspectors(simulator.state);
let savedMode = 'simple';
try {
    savedMode = localStorage.getItem(MODE_KEY) === 'developer' ? 'developer' : 'simple';
} catch {
    // ignore unavailable storage
}
setMode(savedMode);
