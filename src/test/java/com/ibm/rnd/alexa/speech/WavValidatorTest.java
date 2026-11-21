package com.ibm.rnd.alexa.speech;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;

import com.ibm.rnd.alexa.exception.InvalidAudioException;

class WavValidatorTest {

    private static final int MAX = 1_000_000;

    /** Builds a canonical 44-byte-header WAV with {@code seconds} of silence. */
    static byte[] wav(int sampleRate, int channels, int bits, double seconds) {
        int dataBytes = (int) (sampleRate * seconds) * channels * bits / 8;
        ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(sampleRate).putInt(sampleRate * channels * bits / 8)
                .putShort((short) (channels * bits / 8)).putShort((short) bits);
        b.put("data".getBytes()).putInt(dataBytes);
        return b.array();
    }

    @Test
    void acceptsSixteenKilohertzMonoSixteenBit() {
        WavValidator.validate(wav(16000, 1, 16, 1.0), MAX);
    }

    @Test
    void rejectsEmptyTooShortAndTooLong() {
        assertThatThrownBy(() -> WavValidator.validate(new byte[0], MAX)).isInstanceOf(InvalidAudioException.class).hasMessageContaining("No audio");
        assertThatThrownBy(() -> WavValidator.validate(wav(16000, 1, 16, 0.05), MAX)).hasMessageContaining("too short");
        assertThatThrownBy(() -> WavValidator.validate(wav(16000, 1, 16, 1.0), 30_000)).hasMessageContaining("too long");
    }

    @Test
    void rejectsWrongFormat() {
        assertThatThrownBy(() -> WavValidator.validate(wav(44100, 1, 16, 1.0), MAX)).hasMessageContaining("16 kHz mono 16-bit");
        assertThatThrownBy(() -> WavValidator.validate(wav(16000, 2, 16, 1.0), MAX)).hasMessageContaining("16 kHz mono 16-bit");
        assertThatThrownBy(() -> WavValidator.validate(wav(16000, 1, 8, 1.0), MAX)).hasMessageContaining("16 kHz mono 16-bit");
    }

    @Test
    void rejectsNonWavBytes() {
        assertThatThrownBy(() -> WavValidator.validate(new byte[100_000], MAX)).hasMessageContaining("must be a WAV");
    }

    @Test
    void rejectsAHeaderClaimingMoreDataThanSent() {
        byte[] wav = wav(16000, 1, 16, 1.0);
        ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).putInt(40, wav.length * 2);
        assertThatThrownBy(() -> WavValidator.validate(wav, MAX)).hasMessageContaining("does not match");
    }

    @Test
    void supportedLanguagesIncludeOdiaAndExcludeEnglish() {
        assertThat(SpeechLanguages.isSupported("or")).isTrue();
        assertThat(SpeechLanguages.isSupported("hi")).isTrue();
        assertThat(SpeechLanguages.isSupported("en")).isFalse();
        assertThat(SpeechLanguages.isSupported(null)).isFalse();
        assertThat(SpeechLanguages.SUPPORTED).hasSize(22);
    }
}
