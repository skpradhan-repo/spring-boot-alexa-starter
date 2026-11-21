import { languageLabel, localLanguageCode } from './languages.js';
import { describeSpeechState, getSpeechStatus, transcribeLocal } from './localSpeech.js';
import { canRecord, describeMicError, startRecording } from './recorder.js';
import { cancelSpeech, createRecognizer } from './speech.js';

export const ENGINES = [
    { id: 'browser', label: 'Browser (online, built in)' },
    { id: 'local', label: 'Local (offline, needs setup)' },
];

const defaults = { createRecognizer, startRecording, canRecord, getSpeechStatus, transcribeLocal, cancelSpeech };

/**
 * Wires a microphone to a text input for one tab. Speaking fills the input (live while the browser engine is
 * still listening) and, when speech ends, the question is submitted exactly as if it had been typed. Typing
 * therefore always remains an equal alternative.
 *
 * Two engines share this one interface: the browser's Web Speech API, and the optional local service. The engine
 * is read on every click, and there is never an automatic switch: when the chosen engine cannot do the job the
 * user is told why and what to try, and nothing is recognised in a different language than the one selected.
 *
 * @param {object} options
 * @param {HTMLButtonElement} options.micButton
 * @param {HTMLButtonElement} options.stopButton
 * @param {HTMLButtonElement} [options.clearButton] empties the input
 * @param {HTMLInputElement} options.input
 * @param {() => void} options.submit called with the spoken text already in {@code input}
 * @param {(text:string) => void} options.setStatus
 * @param {() => string} options.getLang selected voice language, for example "or-IN"
 * @param {() => 'browser'|'local'} [options.getEngine]
 * @param {HTMLElement} [options.indicator] gets the "listening" class while the microphone is open
 * @param {{record:(engine:string,lang:string,outcome:string)=>void}} [options.support] learns what works
 * @param {() => void} [options.onSupportChange] called after {@code support} changed
 * @param {object} [options.deps] replacements for the browser-facing functions (tests)
 * @returns {{supported:boolean}} whether the browser engine exists in this browser
 */
export function bindVoice({
    micButton, stopButton, clearButton, input, submit, setStatus, getLang, getEngine = () => 'browser',
    indicator, support, onSupportChange, deps = {},
}) {
    const d = { ...defaults, ...deps };
    let failed = false;
    let recording = null;
    let recordingLang = null;
    let working = false;

    const learn = (engine, outcome) => {
        support?.record(engine, getLang(), outcome);
        onSupportChange?.();
    };

    function setListening(on) {
        micButton.disabled = on || working;
        stopButton.disabled = !on;
        if (on) {
            indicator?.classList.add('listening');
        } else {
            indicator?.classList.remove('listening');
        }
    }

    // ---- browser engine -------------------------------------------------------------------------------------

    const recognizer = d.createRecognizer({
        lang: getLang,
        onStart() {
            failed = false;
            input.value = '';
            setStatus('Listening... speak now');
            setListening(true);
        },
        onInterim(text) {
            input.value = text;
            setStatus('Listening... (live text, not final yet)');
        },
        onResult(transcript) {
            input.value = transcript;
            learn('browser', 'works');
        },
        onError(message, code) {
            failed = true;
            if (code === 'language-not-supported') {
                learn('browser', 'failed');
                const hint = localLanguageCode(getLang()) ? ' You can switch Voice engine to Local (see the README).' : '';
                setStatus(`${message}${hint}`);
                return;
            }
            setStatus(message);
        },
        onEnd() {
            setListening(false);
            if (input.value.trim()) {
                submit();
            } else if (!failed) {
                setStatus('Nothing was heard. Click the microphone and try again, or type.');
            }
        },
    });

    // ---- local engine ---------------------------------------------------------------------------------------

    async function startLocal() {
        const lang = localLanguageCode(getLang());
        if (!lang) {
            setStatus(`The local engine has no model for ${languageLabel(getLang())}. Use the browser engine or type your question.`);
            return;
        }
        if (!d.canRecord()) {
            setStatus('This page cannot record audio. Open it at http://localhost:8080 in Chrome or Edge and allow the microphone.');
            return;
        }

        working = true;
        micButton.disabled = true;
        setStatus('Checking the local speech engine...');
        try {
            const status = await d.getSpeechStatus();
            if (!status.ready) {
                setStatus(describeSpeechState(status.state));
                return;
            }
            recording = await d.startRecording({ onAutoStop: () => finishLocal() });
            recordingLang = lang;
            input.value = '';
            setListening(true);
            setStatus('Recording... click Stop when you have finished speaking.');
        } catch (error) {
            recording = null;
            setStatus(describeMicError(error));
        } finally {
            working = false;
            if (!recording) {
                micButton.disabled = false;
            }
        }
    }

    async function finishLocal() {
        if (!recording) {
            return;
        }
        const current = recording;
        const lang = recordingLang;
        recording = null;
        working = true;
        setListening(false);
        micButton.disabled = true;
        setStatus('Recognising on this computer... this can take several seconds.');
        try {
            const wav = await current.finish();
            const result = await d.transcribeLocal(wav, lang);
            if (!result.ok) {
                setStatus(result.error);
                return;
            }
            if (!result.text.trim()) {
                setStatus('No speech was recognised. Speak closer to the microphone and try again.');
                return;
            }
            input.value = result.text;
            learn('local', 'works');
            submit();
        } catch (error) {
            setStatus(error?.message === 'Nothing was recorded' ? 'Nothing was recorded. Try again.' : describeMicError(error));
        } finally {
            working = false;
            micButton.disabled = false;
        }
    }

    // ---- shared controls ------------------------------------------------------------------------------------

    micButton.addEventListener('click', () => {
        d.cancelSpeech();
        if (working || recording) {
            return;
        }
        if (getEngine() === 'local') {
            startLocal();
        } else if (recognizer.supported) {
            recognizer.start();
        } else {
            setStatus('Browser voice input needs Chrome or Edge. Switch Voice engine to Local, or type your question.');
        }
    });

    stopButton.addEventListener('click', () => {
        if (recording) {
            finishLocal();
        } else {
            recognizer.stop();
        }
    });

    clearButton?.addEventListener('click', () => {
        input.value = '';
        setStatus('');
    });

    if (!recognizer.supported) {
        setStatus('Browser voice input needs Chrome or Edge. You can type, or switch Voice engine to Local.');
    }
    return { supported: recognizer.supported };
}
