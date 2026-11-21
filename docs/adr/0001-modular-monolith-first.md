# ADR 0001: Modular monolith first, extract services later

Status: accepted

## Context
The goal is an enterprise AI assistant with Assistant, Model Gateway and Knowledge responsibilities.
The working Alexa simulator must keep running, and the target machine is a single laptop (15.5 GB RAM, CPU only).

## Decision
Keep one deployable application. Separate the responsibilities into packages with narrow interfaces
(`ChatGateway`, a future retrieval interface) and give the Knowledge module its own tables. Extract a module into its own
service only when there is a concrete need: independent scaling, a different release cadence, or isolation of a failure mode.

## Consequences
- No network hop, shared-database coupling or distributed tracing cost today.
- The ports make extraction mechanical. The first likely candidate is the Model Gateway, because inference is the heavy,
  separately scaled workload.
- Three JVMs would not fit comfortably in 15.5 GB beside a 2B model and PostgreSQL.
