# spring-boot-alexa-starter
A local end-to-end **Alexa Skill simulator** powered by **Spring Boot**, the **Amazon Alexa Skills Kit (ASK) Java SDK**, and a vanilla-JS browser UI. Test full Alexa `RequestEnvelope` lifecycles locally without an Echo device or AWS Lambda deployment.

## Features
* **Spring Boot webhook (`POST /alexa`):** parses raw Alexa JSON, validates and verifies it, then routes it through ASK `RequestHandler` beans.
* **Skill coverage:** `LaunchRequest`, `HelloWorldIntent`, `AMAZON.HelpIntent`, `AMAZON.StopIntent`, `AMAZON.CancelIntent`, `AMAZON.FallbackIntent`, `SessionEndedRequest`, and a multi-turn `OrderDrinkIntent` (two slots, entity resolutions, `Dialog.ElicitSlot`, reprompts, cards, SSML, session attributes).
* **Browser simulator:**
  * Intent selector, request builder with slot editing, and a raw JSON editor.
  * Conversation history, request/response inspector, session inspector and error panel.
  * Voice start/stop (Web Speech API, Chrome or Edge) and optional spoken replies.
  * New session / clear conversation.
  * **Simple Mode** vs **Developer Mode** toggle (persisted in `localStorage`).
* **Secure by default:** signature, timestamp and skill-id verification are on unless the `local` profile is active. The server binds to `127.0.0.1`. User text is escaped in SSML, and clients only receive generic errors.

## Tech Stack
* **Backend:** Java 17+, Spring Boot 4.1.2, ASK SDK 2.86.0 (`ask-sdk-core`, `ask-sdk-servlet-support`)
* **Frontend:** HTML5, CSS3, vanilla JavaScript (ES modules)

## Quick Start

1. **Start the application** (uses the `local` profile by default, bound to `127.0.0.1`):
   ```bash
   ./mvnw spring-boot:run
   ```
2. **Open the simulator:** `http://localhost:8080/` (Chrome or Edge for voice input).
3. **Try it:** pick an intent, or type "order a latte" and press **Send**. The skill asks for any missing slot and keeps the session between turns.
4. **Run the tests:**
   ```bash
   ./mvnw test
   node --test src/test/js/simulator.test.mjs
   ```

Use the online `./mvnw`: an offline build (`-o`) fails unless the ASK servlet jar is already in the local Maven cache.

## Using the simulator

1. Pick an intent from the selector, or type text and press **Send**.
2. Say or type "order a latte". The skill asks for the missing size, and your next reply fills it. The **Session** inspector shows the attributes carrying over (for example `ordersPlaced`).
3. Switch to **Developer Mode** for the JSON editor, request builder, and request/response inspector. Edit a slot or the raw JSON and resend.
4. **New session** resets the session id and attributes. **Clear** wipes the conversation history.
5. **Start / Stop** control voice input, and the "speak replies" checkbox reads responses aloud.

## Calling the webhook directly

```bash
curl -X POST http://127.0.0.1:8080/alexa -H "Content-Type: application/json" -d @request.json
```

| Case | Result |
|---|---|
| Valid Alexa request | `200` with the Alexa response JSON (speech, reprompt, card, directives, `sessionAttributes`) |
| Malformed JSON, or missing `version`, `request.requestId`, or the intent name on an `IntentRequest` | `400` `ProblemDetail` with `"detail":"Invalid Alexa request"` |
| Failed signature, timestamp or skill-id verification (`prod`) | `400` `ProblemDetail`; the cause is logged server-side only |
| Error inside a skill handler | `200` with a spoken apology from `GenericErrorHandler`; no internals are returned |
| Any other server error | `500` `ProblemDetail` with `"detail":"Internal server error"` |

## Skill reference

| Request / intent | Behaviour |
|---|---|
| `LaunchRequest` | Welcome message with a reprompt; session stays open |
| `HelloWorldIntent` | Echoes the `message` slot (default "Hello World") and adds a card; ends the session |
| `OrderDrinkIntent` | Multi-turn: slots `drink` and `size`; elicits the missing one with `Dialog.ElicitSlot`; confirms with a card and SSML; counts `ordersPlaced` in session attributes |
| `AMAZON.HelpIntent` | Lists what the skill can do, assembled from all `HelpTopic` beans; session stays open |
| `AMAZON.StopIntent` | "Goodbye!" and ends the session |
| `AMAZON.CancelIntent` | "Okay, I've cancelled that." and ends the session |
| `AMAZON.FallbackIntent` | Apologises and reprompts with "say help"; session stays open |
| `SessionEndedRequest` | Logs the reason and returns an empty response (Alexa forbids speech here) |

## Architecture

```
config/       AlexaProperties (typed config), AlexaSkillConfiguration (Skill bean wiring)
controller/   AlexaController - thin POST /alexa HTTP adapter
service/      AlexaRequestService - parse -> validate -> verify -> dispatch -> serialize
validation/   AlexaRequestValidator, SkillIdVerifier, SimpleAlexaHttpRequest
exception/    GlobalExceptionHandler (errors -> ProblemDetail), InvalidAlexaRequestException
handler/      RequestHandler beans, SkillReply/ReplyRenderer, shared helpers (Ssml, Slots, Sessions)
```

* **Add a capability:** create a `@Component` extending `ReplyingRequestHandler` (or implementing `RequestHandler`). It is picked up automatically; no other class changes.
* **Add a help topic:** register another `HelpTopic` bean.
* **Add a verification step:** register another verifier bean in `AlexaSkillConfiguration`.

The UI lives in `src/main/resources/static/` (`app.js` is the shell; logic is in `js/`, with the DOM-free model in `js/simulator.js`).

## Configuration

| Property | Default | Description |
|---|---|---|
| `alexa.skill-id` (`ALEXA_SKILL_ID`) | empty | Reject requests for other skills. Empty disables the check. |
| `alexa.verification.enabled` | `true` (`false` in `local`) | Enforce Alexa signature and timestamp verification. |
| `alexa.verification.timestamp-tolerance` | `150s` | Maximum request age. |

For real Alexa traffic run with `SPRING_PROFILES_ACTIVE=prod` behind a TLS-terminating proxy, and set `ALEXA_SKILL_ID`.
