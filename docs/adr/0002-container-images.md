# ADR 0002: Container images and the Red Hat registry rule

Status: accepted, with a documented exception

## Context
Project security policy requires public images from `registry.redhat.io`, minimal and non-root. The assistant needs
PostgreSQL with the pgvector extension, which Red Hat does not publish.

## Decision
- `postgres`: `docker.io/pgvector/pgvector:pg16`, pinned by tag. This is a deliberate, recorded exception for the extension.
- `prometheus` and `grafana`: upstream images pinned by tag, optional `observability` profile, development use only.
- Ollama runs natively on the host, not in a container, because that is faster on this CPU-only machine.
- All published ports bind to `127.0.0.1`. Passwords come from a git-ignored `.env`; compose refuses to start without them.

## Follow-ups before any shared or production deployment
- Rebuild the database image on `registry.redhat.io/rhel9/postgresql-16` plus the pgvector extension, or obtain an approved image.
- Scan images and verify signatures in CI.
- The upstream pgvector image runs the server as the `postgres` user after its entrypoint drops privileges; confirm this meets policy.
