import { encodeWav, toMono } from './wav.js';

/** Longest recording in seconds; keeps the upload under the server limit (about 32 KB per second). */
export const MAX_RECORDING_SECONDS = 25;

/** A readable message for a getUserMedia failure. */
export function describeMicError(error) {
    switch (error?.name) {
        case 'NotAllowedError':
        case 'SecurityError':
            return 'Microphone access is blocked. Click the lock icon in the address bar, allow the microphone, then try again.';
        case 'NotFoundError':
        case 'OverconstrainedError':
            return 'No microphone was found.';
        case 'NotReadableError':
            return 'The microphone is in use by another application.';
        default:
            return `Could not open the microphone: ${error?.message ?? 'unknown error'}`;
    }
}

const browserDeps = () => ({
    mediaDevices: globalThis.navigator?.mediaDevices,
    MediaRecorder: globalThis.MediaRecorder,
    AudioContext: globalThis.AudioContext ?? globalThis.webkitAudioContext,
});

/** True when this page can record audio at all (needs a secure context: https or localhost). */
export function canRecord(deps = browserDeps()) {
    return Boolean(deps.mediaDevices?.getUserMedia && deps.MediaRecorder && deps.AudioContext);
}

/**
 * Starts recording the microphone. The browser records in its own compressed format; on {@code finish()} it is
 * decoded and re-encoded as 16 kHz mono WAV for the local recognition service. Nothing is uploaded here.
 *
 * @param {object} [options]
 * @param {number} [options.maxSeconds]
 * @param {() => void} [options.onAutoStop] called when the time limit stops the recording; the caller should then call finish()
 * @returns {Promise<{finish: () => Promise<Uint8Array>, cancel: () => void}>}
 */
export async function startRecording({ maxSeconds = MAX_RECORDING_SECONDS, onAutoStop, deps = browserDeps() } = {}) {
    const stream = await deps.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
    });
    const release = () => stream.getTracks().forEach((track) => track.stop());

    const chunks = [];
    let recorder;
    try {
        recorder = new deps.MediaRecorder(stream);
    } catch (error) {
        release();
        throw error;
    }
    recorder.ondataavailable = (event) => {
        if (event.data?.size > 0) {
            chunks.push(event.data);
        }
    };
    const stopped = new Promise((resolve) => {
        recorder.onstop = resolve;
    });
    recorder.start();

    const timer = setTimeout(() => {
        if (recorder.state !== 'inactive') {
            recorder.stop();
        }
        onAutoStop?.();
    }, maxSeconds * 1000);

    return {
        async finish() {
            clearTimeout(timer);
            if (recorder.state !== 'inactive') {
                recorder.stop();
            }
            await stopped;
            release();

            const blob = new Blob(chunks, { type: recorder.mimeType || 'audio/webm' });
            if (blob.size === 0) {
                throw new Error('Nothing was recorded');
            }
            const context = new deps.AudioContext({ sampleRate: 16_000 });
            try {
                const audio = await context.decodeAudioData(await blob.arrayBuffer());
                const channels = Array.from({ length: audio.numberOfChannels }, (_, i) => audio.getChannelData(i));
                return encodeWav(toMono(channels), audio.sampleRate);
            } finally {
                context.close?.();
            }
        },
        cancel() {
            clearTimeout(timer);
            if (recorder.state !== 'inactive') {
                recorder.stop();
            }
            release();
        },
    };
}
