/**
 * Audio helpers for the local recognition engine. Pure functions (no DOM, no Web Audio), so they run in Node tests.
 *
 * The local service wants a canonical WAV: 16 kHz, mono, 16-bit PCM, 44-byte header. The Java side validates
 * exactly that, so this encoder and `WavValidator` must stay in step.
 */

export const TARGET_RATE = 16_000;

/** Averages several channels into one. A single channel is returned as is. */
export function toMono(channels) {
    if (channels.length === 1) {
        return channels[0];
    }
    const length = channels[0].length;
    const mono = new Float32Array(length);
    for (const channel of channels) {
        for (let i = 0; i < length; i++) {
            mono[i] += channel[i] / channels.length;
        }
    }
    return mono;
}

/** Linear-interpolation resampling. Good enough for speech recognition; avoids an audio library. */
export function resample(samples, fromRate, toRate = TARGET_RATE) {
    if (fromRate === toRate) {
        return samples;
    }
    const length = Math.max(1, Math.round(samples.length * toRate / fromRate));
    const out = new Float32Array(length);
    const step = fromRate / toRate;
    for (let i = 0; i < length; i++) {
        const position = i * step;
        const index = Math.floor(position);
        const next = Math.min(index + 1, samples.length - 1);
        const fraction = position - index;
        out[i] = samples[Math.min(index, samples.length - 1)] * (1 - fraction) + samples[next] * fraction;
    }
    return out;
}

/**
 * @param {Float32Array} samples mono samples in [-1, 1]
 * @param {number} sampleRate rate of {@code samples}; resampled to 16 kHz when different
 * @returns {Uint8Array} a complete WAV file
 */
export function encodeWav(samples, sampleRate = TARGET_RATE) {
    const pcm = resample(samples, sampleRate);
    const dataBytes = pcm.length * 2;
    const buffer = new ArrayBuffer(44 + dataBytes);
    const view = new DataView(buffer);
    const text = (offset, value) => [...value].forEach((char, i) => view.setUint8(offset + i, char.charCodeAt(0)));

    text(0, 'RIFF');
    view.setUint32(4, 36 + dataBytes, true);
    text(8, 'WAVE');
    text(12, 'fmt ');
    view.setUint32(16, 16, true);
    view.setUint16(20, 1, true); // PCM
    view.setUint16(22, 1, true); // mono
    view.setUint32(24, TARGET_RATE, true);
    view.setUint32(28, TARGET_RATE * 2, true); // byte rate
    view.setUint16(32, 2, true); // block align
    view.setUint16(34, 16, true); // bits per sample
    text(36, 'data');
    view.setUint32(40, dataBytes, true);

    for (let i = 0; i < pcm.length; i++) {
        const clamped = Math.max(-1, Math.min(1, pcm[i]));
        view.setInt16(44 + i * 2, clamped < 0 ? clamped * 0x8000 : clamped * 0x7fff, true);
    }
    return new Uint8Array(buffer);
}

/** Duration in seconds of a WAV produced by {@link encodeWav}. */
export function wavSeconds(wav) {
    return Math.max(0, (wav.length - 44) / 2 / TARGET_RATE);
}
