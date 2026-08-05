# Stage 1 — Requirements & Planning

## Overview

An open-source, self-hostable RAG (Retrieval-Augmented Generation) application. Teams can plug in
their own knowledge sources — runbooks, incident postmortems, CI/CD failure logs, and technical
documentation — and ask questions in plain language. Instead of digging through old tickets or
pinging a senior engineer, the app retrieves the most relevant source material and generates a
grounded answer with a citation back to exactly where it came from.

The project demonstrates the full SDE + DevOps lifecycle in one build: application development,
GenAI/RAG integration, security, testing, and production-style AWS deployment — using the same
tooling already reflected in Samir's professional experience (Spring Boot, Kubernetes/EKS,
Terraform, Jenkins, Prometheus/Grafana).

## Core Feature

The chat panel is the only AI-powered feature in the app. Everything else (auth, dashboard,
document library) is standard full-stack engineering built to support it.

- **Indexing** — uploaded documents are chunked, converted to embeddings, and stored in a vector database.
- **Retrieval** — a user's question is embedded and matched against stored chunks to find the most relevant material.
- **Generation** — retrieved chunks are passed to an LLM, which answers using that grounded context and cites its source.

## Build Approach

- **Phase 1** — single-user application. Each user manages their own documents and chat history. Ship this fully working first.
- **Phase 2** — multi-tenant. Organizations, invites, and role-based permissions (Owner / Admin / Member) layered on top of the Phase 1 foundation.

## Technology Stack

### Application
- **Backend**: Java + Spring Boot (Spring Web, Spring Data JPA, Spring Security, Spring AI for the RAG/LLM integration layer)
- **Frontend**: React + TypeScript
- **Vector store**: PostgreSQL + pgvector
- **LLM / embeddings**: Claude or OpenAI API for generation; a lightweight embedding model for indexing

### Infrastructure
- **Containerization**: Docker
- **Orchestration**: Kubernetes on AWS EKS with Fargate
- **IaC**: Terraform (EKS cluster, VPC, RDS, S3, IAM)
- **CI/CD**: Jenkins or GitHub Actions — build, scan, push to ECR, deploy via Helm
- **Storage**: S3 for raw source documents; RDS (Postgres/pgvector) for vector index and app data
- **Monitoring**: Prometheus, Grafana, Alertmanager

## Security Measures

- **Secrets & credentials** — no hardcoded keys. AWS Secrets Manager / Parameter Store for API keys and DB credentials, pulled at runtime via IAM role.
- **IAM (AWS)** — IRSA-scoped permissions per pod; the app's service account can only touch its own S3 bucket and RDS instance.
- **Network isolation** — Postgres/pgvector in a private subnet, no public IP. Only the ALB is public, with TLS via ACM. No direct SSH into prod nodes.
- **App-level auth** — Spring Security: hashed passwords (BCrypt), JWT-based auth, CSRF protection, strict CORS. Default-deny on all endpoints.
- **Document upload validation** — file type allowlist, size limits, and malware/virus scanning before a document enters the pipeline.
- **Prompt injection defense** — hardened system prompt; retrieved content is always treated as data, never as instructions; output filtering before responses reach the user.
- **Multi-tenant data isolation** — strict per-user / per-workspace filtering on all vector search so one tenant can never retrieve another's documents.
- **Container & pipeline security** — Trivy image scanning in CI before ECR push, non-root containers, pinned base image versions, Dependabot / OWASP Dependency-Check.
- **Data protection** — RDS encryption at rest, TLS in transit everywhere, minimal data sent to third-party LLM APIs.
- **Abuse prevention** — rate limiting on the API and specifically the chat endpoint; AWS WAF in front of the ALB.

## Testing Strategy

- **Unit tests** — JUnit 5 + Mockito (backend), Jest + React Testing Library (frontend).
- **Integration tests** — Testcontainers spin up a real Postgres/pgvector instance during test runs; `@SpringBootTest` for full context wiring.
- **RAG evaluation** — a golden dataset of sample Q&A pairs. Retrieval quality measured as recall/precision @k; generation quality checked for faithfulness via an LLM-as-judge pattern. Ingestion edge cases (empty docs, huge PDFs, malformed encoding) tested separately.
- **API / contract tests** — RestAssured or Postman/Newman to keep endpoint contracts stable across changes.
- **Security testing** — SonarQube (SAST) on every build, OWASP ZAP (DAST) against a running instance, and a small adversarial prompt-injection test suite.
- **Infrastructure testing** — `terraform validate` / `plan` review, checkov or tflint for insecure IaC patterns, `helm lint` and dry-run installs.
- **End-to-end tests** — Playwright or Cypress covering signup → login → upload document → ask question → grounded answer with citation.
- **Load / performance testing** — k6 or Locust against the chat endpoint to validate latency and rate limiting under load.
- **CI gates** — unit + integration tests and security scans block every PR merge; deployment to AWS only fires after all gates pass on main.

## Authentication & Authorization

- **Mechanism** — JWT-based, stateless; fits a horizontally-scaled, multi-pod EKS deployment better than server-side sessions.
- **Token handling** — short-lived access token kept in memory on the frontend (not localStorage); refresh token in an httpOnly secure cookie; refresh token rotation and revocation on logout.
- **Passwords** — BCrypt hashing via Spring Security, basic password policy, rate-limited login attempts.
- **Authorization model** — role-based from day one: `USER` and `ADMIN` roles even in the single-user phase, so Phase 2 (`OWNER` / `ADMIN` / `MEMBER` per workspace) extends the model instead of rewriting it.
- **Enforcement** — Spring Security `@PreAuthorize` at the method level; every document/chat query checks resource ownership in the service layer, not just at the route.
- **LLM key protection** — the frontend never calls the LLM API directly. Every chat request goes through the backend, which authenticates first, then calls the LLM server-side using a key from Secrets Manager.
- **Future multi-tenant hook** — swap `user_id` ownership checks for `workspace_id` scoping, add an invite-by-email-token flow — additive on top of the Phase 1 role structure.

## Roadmap Summary

- **Phase 1** — single-user app, core RAG pipeline, security baseline, test suite, AWS deployment via EKS/Fargate.
- **Phase 2** — multi-tenant support: organizations, invites, RBAC, per-workspace data isolation.
- **Ongoing** — open-source hygiene: README, SECURITY.md, docker-compose for local self-hosting, contribution guidelines.
