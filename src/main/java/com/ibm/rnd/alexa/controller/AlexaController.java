package com.ibm.rnd.alexa.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.rnd.alexa.service.AlexaRequestService;

/** HTTP adapter for the Alexa webhook. Contains no business logic; it only delegates. */
@RestController
@RequestMapping("/alexa")
public class AlexaController {

    private final AlexaRequestService service;

    public AlexaController(AlexaRequestService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handle(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "Signature", required = false) String signature,
            @RequestHeader(value = "SignatureCertChainUrl", required = false) String certChainUrl) {
        return ResponseEntity.ok(service.handle(rawBody, signature, certChainUrl));
    }
}
