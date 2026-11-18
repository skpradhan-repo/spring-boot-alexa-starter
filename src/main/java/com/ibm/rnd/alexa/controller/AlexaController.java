package com.ibm.rnd.alexa.controller;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/alexa")
@CrossOrigin
public class AlexaController {

    @PostMapping("/say")
    public Map<String, String> say(
            @RequestBody Map<String, String> request) {

        String message = request.get("message");

        System.out.println("Alexa received: " + message);

        return Map.of(
                "status", "success",
                "response", "Alexa received: " + message);
    }
}
