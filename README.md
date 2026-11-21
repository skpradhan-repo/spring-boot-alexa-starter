# spring-boot-alexa-starter
A local end-to-end **Alexa Skill simulator** powered by **Spring Boot**, the **Amazon Alexa Skills Kit (ASK) Java SDK**, and a vanilla-JS browser UI. Test full Alexa `RequestEnvelope` lifecycles locally without an Echo device or AWS Lambda deployment. It is evolving into a local, free-software AI assistant (Ollama, Spring AI, PostgreSQL + pgvector); see [ARCHITECTURE.md](ARCHITECTURE.md).

## Features
* **Spring Boot webhook (`POST /alexa`):** parses raw Alexa JSON, validates and verifies it, then routes it through ASK `RequestHandler` beans.
* **Skill coverage:** `LaunchRequest`, `HelloWorldIntent`, `AMAZON.HelpIntent`, `AMAZON.StopIntent`, `AMAZON.CancelIntent`, `AMAZON.FallbackIntent`, `SessionEndedRequest`, and a multi-turn `OrderDrinkIntent` (two slots, entity resolutions, `Dialog.ElicitSlot`, reprompts, cards, SSML, session attributes).
* **Browser simulator:**
  * Intent selector, request builder with slot editing, and a raw JSON editor.
  * Conversation history, request/response inspector, session inspector and error panel.
  * Voice start/stop (Web Speech API, Chrome or Edge) and optional spoken replies.
  * New session / clear conversation.
  * **Simple Mode** vs **Developer Mode** toggle (persisted in `localStorage`).
* **Local chat model:** `POST /api/chat` and `POST /api/chat/stream` (Server-Sent Events) talk to Ollama through a resilient `ChatGateway` (concurrency limit, timeout, circuit breaker). The Alexa webhook never depends on the model.
* **Secure by default:** signature, timestamp and skill-id verification are on unless the `local` profile is active. The server binds to `127.0.0.1`. User text is escaped in SSML, and clients only receive generic errors.

## Tech Stack
* **Backend:** Java 21, Spring Boot 4.2.0, Spring AI 2.1.0, ASK SDK 2.86.0, Resilience4j 2.4.0, Micrometer + Prometheus
* **Data:** PostgreSQL 16 + pgvector, Flyway migrations (knowledge profile)
* **Models (local, via Ollama):** `gemma:2b` for chat, `bge-m3` for multilingual (English, Hindi, Odia) embeddings
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

## Local AI and database

Ollama must be running natively (`ollama serve`) with the models pulled once:

```bash
ollama pull gemma:2b
ollama pull bge-m3
```

Chat works with just that. Start the database only when you need the knowledge schema:

```bash
cp .env.example .env                 # then set POSTGRES_PASSWORD
podman compose up -d postgres        # PostgreSQL + pgvector on 127.0.0.1:5432
SPRING_PROFILES_ACTIVE=local,knowledge ./mvnw spring-boot:run   # Flyway creates the schema
podman compose --profile observability up -d   # optional Prometheus + Grafana
```

Switch the chat model without code changes: `CHAT_MODEL=qwen3.5:9b`. On this CPU-only machine `gemma:2b` answers in about 1 s to first token, while a 9B model is far too slow for voice (measurements in [ARCHITECTURE.md](ARCHITECTURE.md)).

```bash
curl -s -X POST http://127.0.0.1:8080/api/chat -H "Content-Type: application/json" -d '{"message":"What is a leave policy?"}'
curl -N -X POST http://127.0.0.1:8080/api/chat/stream -H "Content-Type: application/json" -d '{"message":"Explain leave policy"}'
```

If Ollama is down, the Alexa intents keep working, chat returns `503` with `Retry-After`, and `/actuator/health` reports `DEGRADED`.

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
| Failed signature or timestamp (`prod`) | `401` `ProblemDetail`; the cause is logged server-side only |
| Request addressed to a different skill | `403` `ProblemDetail` |
| Non-JSON content type | `415` |
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
| `alexa.skill-id` (`ALEXA_SKILL_ID`) | empty | Reject requests for other skills. **Required while verification is enabled**: the app refuses to start without it. |
| `alexa.verification.enabled` | `true` (`false` in `local`) | Enforce Alexa signature and timestamp verification. |
| `alexa.verification.timestamp-tolerance` | `150s` | Maximum request age. |
| `CHAT_MODEL` | `gemma:2b` | Ollama chat model. |
| `OLLAMA_BASE_URL` | `http://127.0.0.1:11434` | Ollama endpoint. |
| `assistant.gateway.max-concurrent` | `2` | Simultaneous model calls; more makes CPU inference slower. |
| `assistant.gateway.queue-timeout` | `2s` | Wait for a free slot before answering `429`. |
| `assistant.gateway.request-timeout` | `45s` | Hard limit per model call. |
| `POSTGRES_PASSWORD` | none | Required by the `knowledge` profile and `compose.yaml`; keep it in `.env`. |

For real Alexa traffic run with `SPRING_PROFILES_ACTIVE=prod` behind a TLS-terminating proxy, and set `ALEXA_SKILL_ID`.
