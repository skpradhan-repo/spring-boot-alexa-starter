import { cancelSpeech, createRecognizer } from './speech.js';

/**
 * Wires a microphone button pair to a text input: speaking fills the input and, when the browser stops
 * listening, the question is submitted exactly as if it had been typed. Typing therefore always remains an
 * equal alternative, and browsers without speech recognition degrade to text only.
 *
 * @param {object} options
 * @param {HTMLButtonElement} options.micButton
 * @param {HTMLButtonElement} options.stopButton
 * @param {HTMLInputElement} options.input
 * @param {() => void} options.submit called with the spoken text already in {@code input}
 * @param {(text:string) => void} options.setStatus
 * @param {() => string} options.getLang recognition language, read each time listening starts
 * @param {HTMLElement} [options.indicator] gets the "listening" class while the microphone is open
 * @returns {{supported:boolean}}
 */
export function bindVoice({ micButton, stopButton, input, submit, setStatus, getLang, indicator }) {
    let failed = false;
    const recognizer = createRecognizer({
        lang: getLang,
        onStart() {
            failed = false;
            setStatus('Listening... speak now');
            micButton.disabled = true;
            stopButton.disabled = false;
            indicator?.classList.add('listening');
        },
        onResult(transcript) {
            input.value = transcript;
        },
        onError(message) {
            failed = true;
            setStatus(message);
        },
        onEnd() {
            micButton.disabled = false;
            stopButton.disabled = true;
            indicator?.classList.remove('listening');
            if (input.value.trim()) {
                submit();
            } else if (!failed) {
                setStatus('Nothing was heard. Click the microphone and try again, or type.');
            }
        },
    });

    if (!recognizer.supported) {
        micButton.disabled = true;
        stopButton.disabled = true;
        setStatus('Voice input needs Chrome or Edge. You can type your question instead.');
        return { supported: false };
    }

    micButton.addEventListener('click', () => {
        cancelSpeech();
        recognizer.start();
    });
    stopButton.addEventListener('click', () => recognizer.stop());
    return { supported: true };
}
