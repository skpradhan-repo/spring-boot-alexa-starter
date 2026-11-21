/**
 * Language helpers for voice. Pure functions, no DOM, so they are unit-tested in Node.
 *
 * Speech recognition and synthesis come from the browser (Web Speech API), which is free but not part of this
 * app: Chrome and Edge recognise speech through their vendor's online service, and which languages work
 * depends on the browser and on the voices installed in the operating system.
 */

/** Languages offered for speaking a question. The answer's language is detected from its own script. */
export const VOICE_LANGUAGES = [
    { code: 'en-IN', label: 'English (India)' },
    { code: 'en-US', label: 'English (US)' },
    { code: 'hi-IN', label: 'Hindi' },
    { code: 'or-IN', label: 'Odia' },
    { code: 'bn-IN', label: 'Bengali' },
    { code: 'ta-IN', label: 'Tamil' },
    { code: 'te-IN', label: 'Telugu' },
    { code: 'kn-IN', label: 'Kannada' },
    { code: 'ml-IN', label: 'Malayalam' },
    { code: 'gu-IN', label: 'Gujarati' },
    { code: 'mr-IN', label: 'Marathi' },
    { code: 'pa-IN', label: 'Punjabi' },
];

export const DEFAULT_VOICE_LANGUAGE = 'en-IN';

/** Unicode block (inclusive) to the language spoken when that script dominates a text. */
const SCRIPT_RANGES = [
    { from: 0x0B00, to: 0x0B7F, lang: 'or-IN' },
    { from: 0x0900, to: 0x097F, lang: 'hi-IN' },
    { from: 0x0980, to: 0x09FF, lang: 'bn-IN' },
    { from: 0x0A00, to: 0x0A7F, lang: 'pa-IN' },
    { from: 0x0A80, to: 0x0AFF, lang: 'gu-IN' },
    { from: 0x0B80, to: 0x0BFF, lang: 'ta-IN' },
    { from: 0x0C00, to: 0x0C7F, lang: 'te-IN' },
    { from: 0x0C80, to: 0x0CFF, lang: 'kn-IN' },
    { from: 0x0D00, to: 0x0D7F, lang: 'ml-IN' },
    { from: 0x0600, to: 0x06FF, lang: 'ar-SA' },
    { from: 0x0400, to: 0x04FF, lang: 'ru-RU' },
];

/**
 * Chooses the language to speak a reply in: the dominant non-Latin script of the text, otherwise English
 * (keeping the selected English variant). Latin text is never spoken with a Hindi voice, for example.
 */
export function speechLanguageFor(text, selected = DEFAULT_VOICE_LANGUAGE) {
    const counts = new Map();
    let latin = 0;
    for (const char of text ?? '') {
        const code = char.codePointAt(0);
        const range = SCRIPT_RANGES.find((r) => code >= r.from && code <= r.to);
        if (range) {
            counts.set(range.lang, (counts.get(range.lang) ?? 0) + 1);
        } else if (/\p{Script=Latin}/u.test(char)) {
            latin++;
        }
    }

    let best = null;
    let bestCount = latin;
    for (const [lang, count] of counts) {
        if (count > bestCount) {
            best = lang;
            bestCount = count;
        }
    }
    if (best) {
        return best;
    }
    return selected.startsWith('en') ? selected : DEFAULT_VOICE_LANGUAGE;
}

/** Best installed voice for a language: exact locale first, then any voice of the same base language. */
export function pickVoice(voices, lang) {
    const wanted = lang.toLowerCase();
    const base = wanted.split('-')[0];
    return voices.find((voice) => voice.lang?.toLowerCase().replace('_', '-') === wanted)
        ?? voices.find((voice) => voice.lang?.toLowerCase().replace('_', '-').split('-')[0] === base)
        ?? null;
}

/** Label for a language code, falling back to the code itself. */
export function languageLabel(code) {
    return VOICE_LANGUAGES.find((language) => language.code === code)?.label ?? code;
}

/** A readable message for a Web Speech recognition error code; empty for errors that need no message. */
export function describeSpeechError(error, lang = DEFAULT_VOICE_LANGUAGE) {
    switch (error) {
        case 'aborted':
            return '';
        case 'no-speech':
            return 'No speech was heard. Click the microphone and try again.';
        case 'not-allowed':
        case 'service-not-allowed':
            return 'Microphone access is blocked. Allow the microphone for this page in the browser, then try again.';
        case 'audio-capture':
            return 'No microphone was found.';
        case 'language-not-supported':
            return `This browser cannot recognise ${languageLabel(lang)}. Pick another language or type your question.`;
        case 'network':
            return 'The browser speech service could not be reached. Chrome and Edge need an internet connection for voice input; you can type instead.';
        default:
            return `Speech recognition error: ${error}`;
    }
}
