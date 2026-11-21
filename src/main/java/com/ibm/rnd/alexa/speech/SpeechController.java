package com.ibm.rnd.alexa.speech;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Optional local speech recognition for languages the browser engine cannot recognise (notably Odia). The
 * browser sends a short 16 kHz mono WAV; the text comes back in the language's own script, never translated.
 * Not registered unless {@code assistant.speech.enabled=true}, so by default the endpoint answers 404 and the
 * UI explains that the local engine is not enabled.
 */
@RestController
@RequestMapping("/api/speech")
@ConditionalOnProperty(prefix = "assistant.speech", name = "enabled", havingValue = "true")
class SpeechController {

    /** @param ready true only when the model is loaded and can transcribe now */
    record StatusResponse(boolean ready, String state, List<String> languages) {
    }

    record TranscriptResponse(String text, String language) {
    }

    private final SpeechService speech;

    SpeechController(SpeechService speech) {
        this.speech = speech;
    }

    @GetMapping(path = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    StatusResponse status() {
        SpeechRecognizer.Status status = speech.status();
        return new StatusResponse(status.ready(), status.state(), status.languages());
    }

    @PostMapping(path = "/transcribe", consumes = "audio/wav", produces = MediaType.APPLICATION_JSON_VALUE)
    TranscriptResponse transcribe(@RequestParam("lang") String lang, @RequestBody byte[] wav) {
        return new TranscriptResponse(speech.transcribe(wav, lang), lang);
    }
}
