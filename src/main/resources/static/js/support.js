/**
 * Remembers, per recognition engine and language, whether voice input has actually worked in this browser.
 *
 * Browsers do not say which languages their speech service supports, and accepting a language code proves
 * nothing. So support is learned from use: a non-empty transcript marks a language as working, and an explicit
 * "language not supported" error marks it as failed. Everything else leaves it unknown. The result is stored in
 * localStorage so the selector can say honestly what has and has not been seen to work.
 */

export const SUPPORT_KEY = 'assistant-voice-support';

/** @typedef {'works'|'failed'|'unknown'} SupportStatus */

/**
 * @param {Pick<Storage,'getItem'|'setItem'>|null} storage localStorage, or null when unavailable
 */
export function createSupportTracker(storage) {
    const read = () => {
        try {
            return JSON.parse(storage?.getItem(SUPPORT_KEY) ?? '{}') ?? {};
        } catch {
            return {};
        }
    };
    const keyOf = (engine, lang) => `${engine}:${lang}`;

    return {
        /** @returns {SupportStatus} */
        status(engine, lang) {
            const value = read()[keyOf(engine, lang)];
            return value === 'works' || value === 'failed' ? value : 'unknown';
        },
        /** @param {'works'|'failed'} outcome */
        record(engine, lang, outcome) {
            if (outcome !== 'works' && outcome !== 'failed') {
                return;
            }
            const all = read();
            all[keyOf(engine, lang)] = outcome;
            try {
                storage?.setItem(SUPPORT_KEY, JSON.stringify(all));
            } catch {
                // storage can be full or unavailable; the status then simply stays unknown
            }
        },
    };
}

/**
 * One honest sentence about the active engine and what is known about the selected language.
 *
 * @param {'browser'|'local'} engine
 * @param {string} languageName display name, for example "Odia"
 * @param {SupportStatus} status what has been seen to work in this browser
 * @param {boolean} hasLocalModel whether the local engine has a model for the language
 */
export function describeEngine(engine, languageName, status, hasLocalModel) {
    if (engine === 'local') {
        if (!hasLocalModel) {
            return `Local engine: it has no model for ${languageName}. Switch to the browser engine or type.`;
        }
        const seen = status === 'works' ? ` ${languageName} has been recognised here.` : ` ${languageName} has not been tested here yet.`;
        return `Local engine: audio stays on this computer.${seen}`;
    }
    const base = "Browser engine: your browser sends the audio to its vendor's speech service, so it needs internet.";
    if (status === 'works') {
        return `${base} ${languageName} has worked in this browser.`;
    }
    if (status === 'failed') {
        return `${base} ${languageName} was not recognised by this browser${hasLocalModel ? '; try the local engine' : ''}.`;
    }
    return `${base} ${languageName} has not been tested in this browser yet.`;
}

/** Selector text for a language, with what this browser has been seen to do with it. */
export function languageOptionLabel(label, status) {
    if (status === 'works') {
        return `${label} - works here`;
    }
    if (status === 'failed') {
        return `${label} - not supported by this engine`;
    }
    return label;
}
