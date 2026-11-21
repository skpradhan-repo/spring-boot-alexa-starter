/**
 * Thin wrappers around the browser speech APIs. Everything is feature-detected, so the simulator still works
 * (typed input only) in browsers without them.
 */

export function createRecognizer({ lang = 'en-IN', onStart, onResult, onError, onEnd } = {}) {
    const Recognition = globalThis.SpeechRecognition ?? globalThis.webkitSpeechRecognition;
    if (!Recognition) {
        return { supported: false, start() {}, stop() {} };
    }

    const recognition = new Recognition();
    recognition.lang = lang;
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.onstart = () => onStart?.();
    recognition.onresult = (event) => onResult?.(event.results[0][0].transcript);
    recognition.onerror = (event) => onError?.(event.error);
    recognition.onend = () => onEnd?.();

    return {
        supported: true,
        start() {
            try {
                recognition.start();
            } catch (error) {
                onError?.(error.message);
            }
        },
        stop() {
            recognition.stop();
        },
    };
}

export function speak(text, lang = 'en-IN') {
    if (!('speechSynthesis' in globalThis) || !text) {
        return;
    }
    globalThis.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = lang;
    globalThis.speechSynthesis.speak(utterance);
}

export function cancelSpeech() {
    if ('speechSynthesis' in globalThis) {
        globalThis.speechSynthesis.cancel();
    }
}
