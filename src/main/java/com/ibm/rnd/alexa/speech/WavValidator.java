package com.ibm.rnd.alexa.speech;

import com.ibm.rnd.alexa.exception.InvalidAudioException;

/**
 * Checks that an upload is the audio the recogniser expects: a canonical PCM WAV, 16 kHz, mono, 16-bit. The
 * browser produces exactly this, so anything else is a client bug or a hostile upload and is rejected before
 * it reaches the model process. No audio libraries are involved: only the 44-byte header is read.
 */
final class WavValidator {

    static final int SAMPLE_RATE = 16_000;
    private static final int HEADER_BYTES = 44;
    /** Shortest clip worth sending: a quarter of a second. */
    private static final int MIN_DATA_BYTES = SAMPLE_RATE / 4 * 2;

    private WavValidator() {
    }

    static void validate(byte[] wav, int maxBytes) {
        if (wav.length == 0) {
            throw new InvalidAudioException("No audio was received");
        }
        if (wav.length > maxBytes) {
            throw new InvalidAudioException("The recording is too long");
        }
        if (wav.length < HEADER_BYTES + MIN_DATA_BYTES) {
            throw new InvalidAudioException("The recording is too short");
        }
        if (!tag(wav, 0, "RIFF") || !tag(wav, 8, "WAVE") || !tag(wav, 12, "fmt ") || !tag(wav, 36, "data")) {
            throw new InvalidAudioException("The audio must be a WAV file");
        }
        boolean pcm = u16(wav, 20) == 1;
        boolean mono = u16(wav, 22) == 1;
        boolean rate = u32(wav, 24) == SAMPLE_RATE;
        boolean bits = u16(wav, 34) == 16;
        if (!(pcm && mono && rate && bits)) {
            throw new InvalidAudioException("The audio must be 16 kHz mono 16-bit PCM");
        }
        long declared = u32(wav, 40);
        if (declared > wav.length - HEADER_BYTES) {
            throw new InvalidAudioException("The WAV header does not match its data");
        }
    }

    private static boolean tag(byte[] b, int offset, String expected) {
        for (int i = 0; i < 4; i++) {
            if (b[offset + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8;
    }

    private static long u32(byte[] b, int o) {
        return (b[o] & 0xFFL) | (b[o + 1] & 0xFFL) << 8 | (b[o + 2] & 0xFFL) << 16 | (b[o + 3] & 0xFFL) << 24;
    }
}
