/**
 * Thin wrappers around the browser speech APIs. Everything is feature-detected, so the app still works
 * (typed input only) in browsers without them.
 */

import { DEFAULT_VOICE_LANGUAGE, describeSpeechError, languageLabel, pickVoice, speechLanguageFor } from './languages.js';

/**
 * Splits a Web Speech result event into the text that is still changing (interim) and the text the engine has
 * settled on (final). Pure, so it can be tested without a browser.
 *
 * @param {{resultIndex?:number,results:ArrayLike<ArrayLike<{transcript:string}> & {isFinal:boolean}>}} event
 * @returns {{interim:string,final:string}}
 */
export function readRecognitionEvent(event) {
    let interim = '';
    let final = '';
    for (let i = event.resultIndex ?? 0; i < event.results.length; i++) {
        const result = event.results[i];
        const transcript = result[0]?.transcript ?? '';
        if (result.isFinal) {
            final += transcript;
        } else {
            interim += transcript;
        }
    }
    return { interim: interim.trim(), final: final.trim() };
}

/**
 * @param {object} options
 * @param {string|(() => string)} options.lang recognition language, or a function read on every start so a
 *        language selector can change it between questions
 * @param {(text:string) => void} [options.onInterim] called while the user is still speaking, with the best guess so far
 * @param {(text:string) => void} [options.onResult] called once with the settled text
 * @param {(message:string, code:string) => void} [options.onError] readable message and the raw engine error code
 */
export function createRecognizer({ lang = DEFAULT_VOICE_LANGUAGE, onStart, onInterim, onResult, onError, onEnd } = {}) {
    const Recognition = globalThis.SpeechRecognition ?? globalThis.webkitSpeechRecognition;
    if (!Recognition) {
        return { supported: false, start() {}, stop() {} };
    }

    const recognition = new Recognition();
    let current = DEFAULT_VOICE_LANGUAGE;
    recognition.continuous = false;
    recognition.interimResults = true;
    recognition.onstart = () => onStart?.();
    recognition.onresult = (event) => {
        const { interim, final } = readRecognitionEvent(event);
        if (final) {
            onResult?.(final);
        } else if (interim) {
            onInterim?.(interim);
        }
    };
    recognition.onerror = (event) => onError?.(describeSpeechError(event.error, current), event.error);
    recognition.onend = () => onEnd?.();

    return {
        supported: true,
        start() {
            current = typeof lang === 'function' ? lang() : lang;
            recognition.lang = current;
            try {
                recognition.start();
            } catch (error) {
                // start() throws if a session is already running; keep that session instead of creating another.
                onError?.(error.message, 'start-failed');
            }
        },
        stop() {
            recognition.stop();
        },
    };
}

/**
 * Speaks a reply in the language of its own script. Returns what happened so the caller can tell the user when
 * speech is impossible (no voice installed for that language) instead of staying silent.
 *
 * @returns {'spoken'|'unsupported'|'no-voice'}
 */
export function speak(text, selectedLang = DEFAULT_VOICE_LANGUAGE) {
    if (!('speechSynthesis' in globalThis) || !text) {
        return 'unsupported';
    }
    const lang = speechLanguageFor(text, selectedLang);
    const voices = globalThis.speechSynthesis.getVoices();
    const voice = pickVoice(voices, lang);
    // The voice list can still be empty right after page load; in that case let the browser try.
    if (!voice && voices.length > 0 && !lang.startsWith('en')) {
        return 'no-voice';
    }

    globalThis.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = voice?.lang ?? lang;
    if (voice) {
        utterance.voice = voice;
    }
    globalThis.speechSynthesis.speak(utterance);
    return 'spoken';
}

/** One line telling the user why a reply was not spoken; empty when it was. */
export function describeSpeakResult(result, text, selectedLang = DEFAULT_VOICE_LANGUAGE) {
    if (result === 'no-voice') {
        return `No ${languageLabel(speechLanguageFor(text, selectedLang))} voice is installed on this device, so the answer is shown as text only.`;
    }
    if (result === 'unsupported') {
        return 'This browser cannot speak replies.';
    }
    return '';
}

export function cancelSpeech() {
    if ('speechSynthesis' in globalThis) {
        globalThis.speechSynthesis.cancel();
    }
}
