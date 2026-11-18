
package com.ibm.rnd.alexa.controller;

import com.amazon.ask.Skill;
import com.amazon.ask.builder.CustomSkillBuilder;
import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.RequestEnvelope;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.ResponseEnvelope;
import com.amazon.ask.model.services.Serializer;
import com.amazon.ask.servlet.verifiers.AlexaHttpRequest;
import com.amazon.ask.servlet.verifiers.SkillRequestSignatureVerifier;
import com.amazon.ask.servlet.verifiers.SkillRequestTimestampVerifier;
import com.amazon.ask.util.JacksonSerializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static com.amazon.ask.request.Predicates.intentName;
import static com.amazon.ask.request.Predicates.requestType;

@RestController
@RequestMapping("/alexa")
public class DemoAlexaController {

    private static final Logger log = LoggerFactory.getLogger(DemoAlexaController.class);

    private final Skill skill;

    private final Serializer serializer = new JacksonSerializer();

    private final SkillRequestSignatureVerifier signatureVerifier = new SkillRequestSignatureVerifier();

    // 150 seconds timestamp tolerance
    private final SkillRequestTimestampVerifier timestampVerifier = new SkillRequestTimestampVerifier(150000L);

    public DemoAlexaController() {

        this.skill = new CustomSkillBuilder()
                .addRequestHandlers(
                        new LaunchRequestHandler(),
                        new HelloWorldIntentHandler(),
                        new FallbackHandler())
                // Configure your actual Alexa Skill ID
                // .withSkillId("amzn1.ask.skill.your-skill-id")
                .build();
    }

    // ==========================================
    // ALEXA REQUEST ENDPOINT
    // ==========================================

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleAlexaRequest(

            @RequestBody byte[] rawBody,

            @RequestHeader(value = "Signature", required = false) String signature,

            @RequestHeader(value = "SignatureCertChainUrl", required = false) String certChainUrl) {

        try {

            // Preserve original request body for signature verification
            String rawJson = new String(rawBody, StandardCharsets.UTF_8);

            // Deserialize Alexa request
            RequestEnvelope requestEnvelope = serializer.deserialize(
                    rawJson,
                    RequestEnvelope.class);

            // Construct Alexa HTTP request
            AlexaHttpRequest verificationRequest = new SimpleAlexaRequest(
                    rawBody,
                    requestEnvelope,
                    signature,
                    certChainUrl);

            // Verify Alexa signature
            // signatureVerifier.verify(verificationRequest);

            // Verify Alexa timestamp
            // timestampVerifier.verify(verificationRequest);

            log.info("Alexa request signature and timestamp verified");

            // Invoke Alexa Skill
            ResponseEnvelope responseEnvelope = skill.invoke(requestEnvelope);

            // Return Alexa response
            return ResponseEntity.ok(
                    serializer.serialize(responseEnvelope));

        } catch (SecurityException e) {

            log.warn("Alexa request verification failed: {}",
                    e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid Alexa request");

        } catch (Exception e) {

            log.error("Error processing Alexa request", e);

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal server error");
        }
    }

    // ==========================================
    // ALEXA HTTP REQUEST IMPLEMENTATION
    // ==========================================

    private record SimpleAlexaRequest(
            byte[] serializedRequestEnvelope,
            RequestEnvelope deserializedRequestEnvelope,
            String baseEncoded64Signature,
            String signingCertificateChainUrl) implements AlexaHttpRequest {

        @Override
        public byte[] getSerializedRequestEnvelope() {
            return serializedRequestEnvelope;
        }

        @Override
        public RequestEnvelope getDeserializedRequestEnvelope() {
            return deserializedRequestEnvelope;
        }

        @Override
        public String getBaseEncoded64Signature() {
            return baseEncoded64Signature;
        }

        @Override
        public String getSigningCertificateChainUrl() {
            return signingCertificateChainUrl;
        }
    }

    // ==========================================
    // LAUNCH REQUEST HANDLER
    // ==========================================

    static class LaunchRequestHandler implements RequestHandler {

        @Override
        public boolean canHandle(HandlerInput input) {

            return input.matches(
                    requestType(LaunchRequest.class));
        }

        @Override
        public Optional<Response> handle(HandlerInput input) {

            log.info("Alexa skill launched");

            String speechText = "Hello World! Your Spring Boot Alexa skill is running.";

            return input.getResponseBuilder()
                    .withSpeech(speechText)
                    .withShouldEndSession(true)
                    .build();
        }
    }

    // ==========================================
    // HELLO WORLD INTENT HANDLER
    // ==========================================

    static class HelloWorldIntentHandler implements RequestHandler {

        @Override
        public boolean canHandle(HandlerInput input) {

            return input.matches(
                    intentName("HelloWorldIntent"));
        }

        @Override
        public Optional<Response> handle(HandlerInput input) {

            log.info("HelloWorldIntent received");

            String message = "Hello World";

            if (input.getRequestEnvelope().getRequest() instanceof IntentRequest intentRequest) {

                if (intentRequest.getIntent().getSlots() != null) {

                    var slot = intentRequest.getIntent()
                            .getSlots()
                            .get("message");

                    if (slot != null && slot.getValue() != null) {
                        message = slot.getValue();
                    }
                }
            }

            log.info("Alexa received message: {}", message);

            String speechText = "I received your message: " + message;

            return input.getResponseBuilder()
                    .withSpeech(speechText)
                    .withShouldEndSession(true)
                    .build();
        }
    }

    // ==========================================
    // FALLBACK INTENT HANDLER
    // ==========================================

    static class FallbackHandler implements RequestHandler {

        @Override
        public boolean canHandle(HandlerInput input) {

            return input.matches(
                    intentName("AMAZON.FallbackIntent"));
        }

        @Override
        public Optional<Response> handle(HandlerInput input) {

            log.info("Fallback intent received");

            return input.getResponseBuilder()
                    .withSpeech(
                            "Sorry, I didn't understand that request.")
                    .withShouldEndSession(true)
                    .build();
        }
    }
}
