import { postAlexaRequest } from './js/api.js';
import { h } from './js/dom.js';
import { INTENTS, REQUEST_TYPES, SESSION_END_REASONS, findIntent } from './js/interactionModel.js';
import { Simulator, DEFAULT_SKILL_ID } from './js/simulator.js';
import { SimulatorSession } from './js/session.js';
import { cancelSpeech, createRecognizer, speak } from './js/speech.js';

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

// ---------------------------------------------------------------- chat input and voice

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

const recognizer = createRecognizer({
    onStart() {
        setStatus('Listening... speak now');
        $('micButton').disabled = true;
        $('stopMicButton').disabled = false;
        $('history').classList.add('listening');
    },
    onResult(transcript) {
        $('messageInput').value = transcript;
    },
    onError(error) {
        setStatus(`Speech recognition error: ${error}`);
    },
    onEnd() {
        $('micButton').disabled = false;
        $('stopMicButton').disabled = true;
        $('history').classList.remove('listening');
        if ($('messageInput').value.trim()) {
            sendMessage();
        } else {
            setStatus('Click the microphone or type a message');
        }
    },
});

if (recognizer.supported) {
    $('micButton').addEventListener('click', () => {
        cancelSpeech();
        recognizer.start();
    });
    $('stopMicButton').addEventListener('click', () => recognizer.stop());
} else {
    $('micButton').disabled = true;
    setStatus('Voice input is not supported in this browser - type your message instead.');
}

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
        if ($('speakReplies').checked) {
            speak(latest.text);
        }
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
