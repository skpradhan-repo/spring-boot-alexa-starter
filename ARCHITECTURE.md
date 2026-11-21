# Architecture

Status: milestone 1 (Model Gateway) and milestone 2 (knowledge ingestion) complete. Retrieval and grounded answers are next.

## Shape

One modular Spring Boot application with explicit module boundaries. It is **not** yet split into
independently deployed services: each module below owns its data and exposes a narrow interface, so
extraction later is a mechanical move rather than a redesign (see ADR 0001).

```mermaid
flowchart LR
    Browser -->|POST /alexa| Alexa[Alexa webhook<br/>controller, service, handlers]
    Browser -->|POST /api/chat, /stream SSE| Chat[Chat API]
    Chat --> Gateway[Model Gateway<br/>ChatGateway port]
    Gateway -->|bulkhead, timeout, circuit breaker| Ollama[(Ollama<br/>gemma:2b, bge-m3)]
    Alexa -. deterministic intents .-> Alexa
    Browser -->|POST /api/documents| Ingest[Knowledge ingestion<br/>validate, dedupe, queue]
    Ingest -->|bounded executor| Indexer[Indexer<br/>Tika, normalize, chunk, embed]
    Indexer -->|bge-m3 embeddings| Ollama
    Indexer --> PG[(PostgreSQL + pgvector)]
    Ingest --> PG
```

| Module | Package | Owns |
|---|---|---|
| Alexa assistant | `controller`, `service`, `validation`, `handler`, `config` | Request parsing, verification, intents, sessions |
| Chat | `chat` | Browser chat API, system prompt, input limits |
| Model Gateway | `gateway` | `ChatGateway` port, Spring AI adapter, resilience decorator, health |
| Knowledge | `knowledge`, `db/migration` | Upload API, validation, Tika extraction, chunking, embeddings, documents and chunks in PostgreSQL |

## Knowledge ingestion

`POST /api/documents` (multipart `file`, `access_group`) answers immediately; indexing runs in the background.

1. **Validate (request thread):** access group `[a-z0-9_-]{1,64}`, file name stripped to its last path segment, size at most 20 MB,
   extension on the allow-list (pdf, docx, txt, html, md), and the content type Tika detects from the **bytes** must match the extension.
2. **Deduplicate:** SHA-256 of the bytes, scoped to the access group. Identical content returns `200` with `duplicate: true`
   and does no new work. A `FAILED` document never blocks a retry.
3. **Version:** same name, different content in the same group becomes version N+1. Numbering is per group, so a version number can
   not reveal that another group uploaded a file of the same name. A unique key plus retry settles concurrent uploads.
4. **Queue:** a bounded executor (1 thread, 10 queued). A full queue answers `429` + `Retry-After` and leaves no orphan row.
5. **Index (worker thread):** Tika text (capped at 2 million characters) -> Unicode NFC normalization -> chunking -> `bge-m3`
   embeddings in batches of 16 -> chunks and `PENDING -> INDEXED` in **one transaction**.

Chunking is recursive (paragraph, line, sentence incl. the Devanagari/Odia danda, word, then a hard cut) at about 500 tokens with
60 tokens of overlap. Tokens are estimated at 3 characters each, a conservative figure that also holds for Hindi and Odia.
Language (`en`, `hi`, `or`) is detected from the dominant Unicode script.

A failure leaves the document `FAILED` with a coarse code (`EXTRACTION_FAILED`, `EMPTY_DOCUMENT`, `EMBEDDING_UNAVAILABLE`,
`EMBEDDING_MISMATCH`, `STORAGE_FAILED`). Details go to the server log; **document text is never logged**.

`access_group` is a request parameter for now, a stand-in for the caller identity that JWT will supply. The service already applies
it in SQL on every lookup, and a document in another group answers `404`, not `403`, so existence is not revealed.

## Principles applied

- **Dependency inversion:** callers depend on `ChatGateway`, never on Spring AI or Ollama types.
- **Decorator:** `ResilientChatGateway` adds a concurrency limit, hard timeout and circuit breaker to any gateway.
- **Open/closed:** new Alexa capabilities are new `@Component` handlers; no existing class changes.
- **Graceful degradation (the CAP trade-off we chose):** the app favours availability for deterministic
  features. With the model down, Alexa intents still answer, `/api/chat` returns `503` + `Retry-After`,
  and `/actuator/health` reports `DEGRADED` (HTTP 200) rather than `DOWN`.
- **Fail fast on bad config:** `AlexaProperties` is validated at startup; verification without a skill id refuses to boot.
- **Secrets stay out of source:** database and Grafana passwords come from a git-ignored `.env`.

## Error contract

| Condition | Status |
|---|---|
| Malformed JSON / invalid envelope | 400 |
| Unsupported content type | 415 |
| Failed signature or timestamp | 401 |
| Request for a different skill | 403 |
| Invalid upload (empty, too large, bad access group) | 400 |
| Upload larger than the multipart limit | 413 |
| Unsupported file type, or content does not match the extension | 415 |
| Document not found or in another access group | 404 |
| Ingestion queue full | 429 + `Retry-After` |
| Model overloaded | 429 + `Retry-After` |
| Model unavailable or timed out | 503 + `Retry-After` |
| Anything else | 500, generic body |

Bodies never contain exception messages, hostnames or class names; details go to the server log.

## Capacity (measured on the target laptop: 12 cores, 15.5 GB RAM, CPU only)

| Model | Result |
|---|---|
| `gemma:2b` | first token about 0.5-0.9 s warm, about 7 tokens/s: default |
| `qwen3.5:9b` | 38 s for a short answer including a 23 s load, about 4.7 tokens/s: too slow for voice |
| `bge-m3` | 1024 dimensions, about 0.7 s for a batch of three warm, about 4 s cold |
| Ingestion | three one-chunk documents indexed in 8 s total including a cold embedding model (6.5 s for the first) |

Concurrency is capped at 2 because CPU inference slows every request when oversubscribed.

## Observability

`/actuator/health` (liveness and readiness probes enabled), `/actuator/prometheus` with
`assistant_gateway_slots_available`, `assistant_gateway_circuit_state`, `assistant_knowledge_ingestion_total{outcome}`
(accepted, duplicate, rejected, type_mismatch), `assistant_knowledge_indexing_seconds{outcome}` and
`assistant_knowledge_queue_size`. Prometheus and Grafana are optional compose profile `observability`.

## Dependency notes

- Spring AI's Tika reader pins Tika 4.0.0; `pom.xml` overrides it to the latest release (4.2.0) by declaring
  `tika-core` and `tika-parsers-standard-package` directly. Importing the Tika BOM was tried and rejected: it also
  overrides Boot's Micrometer version and the application context failed to start.
- `ask-sdk-servlet-support` brings `commons-io` 2.7, which breaks Tika, POI and PDFBox at runtime
  (`NoSuchMethodError`). `commons-io` is pinned to 2.22.0 in `dependencyManagement`.
