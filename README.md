# spring-boot-alexa-starter
A local end-to-end **Alexa Skill simulator** powered by **Spring Boot**, the **Amazon Alexa Skills Kit (ASK) Java SDK**, and a vanilla-JS browser UI. Test full Alexa `RequestEnvelope` lifecycles locally without an Echo device or AWS Lambda deployment. It is evolving into a local, free-software AI assistant (Ollama, Spring AI, PostgreSQL + pgvector); see [ARCHITECTURE.md](ARCHITECTURE.md).

## Features
* **Spring Boot webhook (`POST /alexa`):** parses raw Alexa JSON, validates and verifies it, then routes it through ASK `RequestHandler` beans.
* **Skill coverage:** `LaunchRequest`, `HelloWorldIntent`, `AMAZON.HelpIntent`, `AMAZON.StopIntent`, `AMAZON.CancelIntent`, `AMAZON.FallbackIntent`, `SessionEndedRequest`, and a multi-turn `OrderDrinkIntent` (two slots, entity resolutions, `Dialog.ElicitSlot`, reprompts, cards, SSML, session attributes).
* **Browser UI with three tabs, each usable by voice or by typing:**
  * **Alexa skill:** the original simulator. Intent selector, request builder with slot editing, raw JSON editor, request/response and session inspectors (Developer Mode), new session / clear.
  * **Ask anything:** a general question to the local model, answered from its own knowledge (`POST /api/chat`).
  * **Ask my documents:** upload a file to an access group, watch it go `PENDING` -> `INDEXED` (or `FAILED` with the reason), then ask about it; answers come only from your documents and show their sources.
  * **Voice:** one microphone per tab, a shared **Voice language** selector (English, Hindi, Odia, Bengali, Tamil, Telugu, Kannada, Malayalam, Gujarati, Marathi, Punjabi) and a **Speak replies** switch. Replies are read aloud in the language of their own script. Uses the browser Web Speech API (Chrome or Edge).
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
   node --test src/test/js/simulator.test.mjs src/test/js/documents.test.mjs src/test/js/assistant.test.mjs
   ```

Use the online `./mvnw`: an offline build (`-o`) fails unless the ASK servlet jar is already in the local Maven cache.

## Local AI and database

Ollama must be running natively (`ollama serve`) with the models pulled once:

```bash
ollama pull gemma:2b
ollama pull bge-m3
```

Chat works with just that. Start the database only when you need the knowledge service (document upload and indexing).

### How to run with the knowledge service

The app does **not** read `.env`. Docker/Podman Compose reads it to create the database, and the app takes the same password
from the `POSTGRES_PASSWORD` environment variable of the terminal that starts it. They must match, or startup fails with
`password authentication failed for user "assistant"`.

1. Create `.env` once and set `POSTGRES_PASSWORD` to a password of your choice (`.env` is git-ignored; never commit it):
   ```bash
   cp .env.example .env
   ```
2. Start the database (PostgreSQL + pgvector on `127.0.0.1:5432`):
   ```bash
   podman compose up -d postgres
   ```
3. Start the app with the `knowledge` profile, in the **same terminal**. PowerShell (Windows), which reads the password from `.env`:
   ```powershell
   cd C:\path\to\spring-boot-alexa-starter
   $env:POSTGRES_PASSWORD = ((Get-Content .env | Where-Object { $_ -match '^POSTGRES_PASSWORD=' }) -replace '^POSTGRES_PASSWORD=','').Trim()
   $env:SPRING_PROFILES_ACTIVE = "local,knowledge"
   ./mvnw spring-boot:run
   ```
   Bash (macOS/Linux):
   ```bash
   set -a; source .env; set +a
   SPRING_PROFILES_ACTIVE=local,knowledge ./mvnw spring-boot:run
   ```
   The log should show `The following 2 profiles are active: "local", "knowledge"` and Flyway creating the schema.
4. Optional: `podman compose --profile observability up -d` adds Prometheus and Grafana.

Do not change `POSTGRES_PASSWORD` in `.env` after the database has been created: PostgreSQL keeps the original password
in its data volume. To start over with a new one, run `podman compose down -v` (this deletes the stored documents).

### Uploading documents (knowledge profile)

```bash
curl -F "file=@leave-policy.pdf" -F "access_group=hr" http://127.0.0.1:8080/api/documents     # 202 + document id, status PENDING
curl "http://127.0.0.1:8080/api/documents/<id>?access_group=hr"                                # PENDING -> INDEXED (or FAILED + reason)
```

Accepted: PDF, DOCX, TXT, HTML and Markdown up to 20 MB; the content must match the extension. `access_group` defaults to
`public` when omitted. Re-uploading identical bytes
returns `200` with `"duplicate": true`. Changed content under the same name becomes the next version. Documents are only
visible within their `access_group` (a stand-in for real identity until JWT lands). Any language the `bge-m3` embedding model
understands can be uploaded and searched; English, Hindi and Odia are the ones tested with sample files.
Without the `knowledge` profile none of this is active and the simulator and chat need no database.

You can also upload from the browser: open `http://localhost:8080/` and use the **Ask my documents** tab (both modes).

### Asking questions about your documents

In the browser, open the **Ask my documents** tab. Upload a file, then type a question, or press **Start listening** and speak it.
The access group is the one in the upload form. Set **Voice language** to the language you will speak. Answers take about 2-8
seconds on a CPU-only machine. Or call the API:

```bash
curl -X POST http://127.0.0.1:8080/api/knowledge/ask -H "Content-Type: application/json" \
  -d '{"question":"How many days of annual leave do employees get?","access_group":"hr"}'
```

The reply has `answer`, `grounded` and `sources` (document, version, match score, excerpt). Only the caller's access group is searched,
and only the latest version of each document. If nothing is similar enough, the model is not called and the answer is
"I could not find this in the uploaded documents." (`grounded: false`). On Windows PowerShell write the JSON to a file and use
`--data-binary "@file.json"`, because PowerShell mangles quotes and non-English text passed inline.

Ready-made English, Hindi and Odia test files, with expected results and a checklist, are in [`samples/`](samples/README.md).

Switch the chat model without code changes: `CHAT_MODEL=qwen3.5:9b`. On this CPU-only machine `gemma:2b` answers in about 1 s to first token, while a 9B model is far too slow for voice (measurements in [ARCHITECTURE.md](ARCHITECTURE.md)).

```bash
curl -s -X POST http://127.0.0.1:8080/api/chat -H "Content-Type: application/json" -d '{"message":"What is a leave policy?"}'
curl -N -X POST http://127.0.0.1:8080/api/chat/stream -H "Content-Type: application/json" -d '{"message":"Explain leave policy"}'
```

If Ollama is down, the Alexa intents keep working, chat returns `503` with `Retry-After`, and `/actuator/health` reports `DEGRADED`.

## Using the simulator

1. Pick an intent from the selector, or type text and press **Send**.
2. Say or type "order a latte". The skill asks for the missing size, and your next reply fills it. The **Session** inspector shows the attributes carrying over (for example `ordersPlaced`).
3. Switch to **Developer Mode** for the JSON editor, request builder, and request/response inspector. Edit a slot or the raw JSON and resend. These tools appear on the **Alexa skill** tab only.
4. **New session** resets the session id and attributes. **Clear** wipes the conversation history.

## Using voice

Every tab has **Start listening**, **Stop** and **Clear text**. Speak, and when you stop the question is sent as if you had typed it.
Two engines are available, chosen with **Voice engine**. The app never switches between them on its own and never translates: it
recognises the language you selected, or tells you it cannot.

* **Browser engine (default):** use Chrome or Edge. Recognition is done by the browser's vendor service, so **your audio leaves this
  computer** and it needs internet. You see the text live while speaking. Without it, typing still works and the page says so.
* **Local engine (optional):** recognition runs on this computer with a free model, so audio never leaves it and it works offline. It
  needs the one-time setup below. You click **Stop** when you have finished speaking (recordings are limited to 25 seconds), then wait a
  few seconds for the text.
* **Voice language:** choose the language you will speak before pressing the microphone. Browsers do not say which languages they
  support, so the list marks a language **works here** only after it has actually produced text in this browser, and **not supported by
  this engine** after the engine refused it. Odia (`or-IN`) is often refused by browsers; the local engine is the way to get Odia.
* **Spoken replies:** the answer is read in the language of its script (Odia, Hindi, Tamil and so on, otherwise English). This needs a
  matching voice installed in your operating system (Windows: Settings > Time & language > Speech). If there is none, the answer stays
  on screen and the page says no voice is installed.

### Local speech engine (optional, free, offline)

It uses [AI4Bharat IndicConformer](https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual) (MIT licence), a speech model
for 22 Indian languages: Assamese, Bengali, Bodo, Dogri, Gujarati, Hindi, Kannada, Konkani, Kashmiri, Maithili, Malayalam, Manipuri,
Marathi, Nepali, **Odia**, Punjabi, Sanskrit, Santali, Sindhi, Tamil, Telugu and Urdu. English, Spanish and the other non-Indian
languages are not covered; use the browser engine for those. (Whisper was not chosen because it has no Odia.)

1. Create a free Hugging Face account, open the model page above, accept its terms, and create a **read** access token
   (Settings > Access Tokens). The model is gated, so this is required once.
2. Install and start the service (Python 3.11+; the model downloads on first start. Its size and memory use were not measured here: allow several GB of disk and RAM, and expect several seconds per clip on a CPU):

   ```powershell
   cd speech-service
   python -m venv .venv
   .\.venv\Scripts\Activate.ps1
   pip install -r requirements.txt
   $env:HF_TOKEN = "<your token>"      # only needed for the first download; do not put it in a file you commit
   python -m uvicorn app:app --host 127.0.0.1 --port 8090
   ```

   After the first start the model is cached and the token is no longer needed.
3. Start the app with the engine enabled: `ASSISTANT_SPEECH_ENABLED=true` (together with your usual profiles).
4. In the page choose **Voice engine: Local**. The status line says whether the service is ready, still loading, or the model is
   missing.

Privacy and safety:

* The service and the Spring endpoints listen on `127.0.0.1` only, and `assistant.speech.service-url` is rejected at startup if it is
  not a loopback address, so recordings cannot be sent elsewhere by configuration.
* The model repository contains Python code that is run when it loads (`trust_remote_code`). The service pins revision `2a77d305...`
  so that code cannot change underneath you. That code could not be read before writing this (the repository is gated): read it
  yourself before first use, and change `SPEECH_MODEL_REVISION` only after reviewing the new code.
* Recordings are held in memory for one request and not stored. Errors shown to you are generic; details stay in the service log.

**Verification status (be aware):**

| Part | Status |
|---|---|
| Browser engine, live text, language support learned from use | Implemented; the logic is unit-tested. Whether a given language works depends on your browser and has not been verified for any language here. |
| Local engine plumbing (record, 16 kHz WAV, Spring endpoint, service) | Implemented and tested with a fake model. |
| IndicConformer model call in `speech-service/app.py` | **Not verified against real audio.** The call follows the model's usual convention but was written without access to the gated model files. |
| Odia, Hindi, Bengali and every other language | **Not tested with real speech.** Do not rely on any language until you have spoken it and checked the text. Suggested first check: Odia, Hindi, Bengali. |

Troubleshooting: *"not enabled"* means the app was started without `ASSISTANT_SPEECH_ENABLED=true`; *"not running"* means the service
is not listening on port 8090; *"model is not installed"* means the first download failed (check the token and that you accepted the
terms); *"busy"* means another recording is being recognised, so wait a few seconds.

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
exception/    GlobalExceptionHandler (errors -> ProblemDetail), Alexa and document exceptions
handler/      RequestHandler beans, SkillReply/ReplyRenderer, shared helpers (Ssml, Slots, Sessions)
chat/         Browser chat API (/api/chat, /api/chat/stream)
gateway/      ChatGateway port, Spring AI adapter, resilience decorator, health
knowledge/    Document upload API, extraction, chunking, embeddings, JDBC repository (knowledge profile)
speech/       Optional local speech API (/api/speech), WAV validation, loopback client to speech-service/ (assistant.speech.enabled)
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
| `assistant.knowledge.max-upload-size` | `20MB` | Largest accepted upload; keep `spring.servlet.multipart.max-file-size` in step. |
| `assistant.knowledge.chunk-tokens` / `chunk-overlap-tokens` | `500` / `60` | Target chunk size and overlap, in estimated tokens. |
| `assistant.knowledge.embedding-batch-size` | `16` | Chunks per embedding call. |
| `assistant.knowledge.embedding-dimensions` | `1024` | Vector size the schema stores (`bge-m3`); a mismatch fails indexing. |
| `assistant.knowledge.max-extracted-chars` | `2000000` | Cap on text extracted from one file. |
| `assistant.knowledge.ingestion-threads` / `ingestion-queue-size` | `1` / `10` | Indexing workers and waiting uploads; a full queue answers `429`. |
| `assistant.knowledge.retrieval-top-k` | `3` | Passages given to the model per question. |
| `assistant.knowledge.retrieval-min-score` | `0.4` | Cosine similarity below which a passage counts as unrelated; if none pass, the model is not called. |
| `assistant.knowledge.retrieval-context-chars` | `3000` | Upper bound on source text sent to the model. |
| `assistant.speech.enabled` (`ASSISTANT_SPEECH_ENABLED`) | `false` | Exposes `/api/speech`; needs the Python service in `speech-service/`. |
| `assistant.speech.service-url` (`ASSISTANT_SPEECH_URL`) | `http://127.0.0.1:8090` | The speech service; must be `127.0.0.1` or `localhost`. |
| `assistant.speech.max-audio-bytes` | `1000000` | Largest accepted recording (about 30 s of 16 kHz mono audio). |
| `assistant.speech.timeout` | `60s` | Limit for one recognition; a CPU needs several seconds for a short clip. |

For real Alexa traffic run with `SPRING_PROFILES_ACTIVE=prod` behind a TLS-terminating proxy, and set `ALEXA_SKILL_ID`.
