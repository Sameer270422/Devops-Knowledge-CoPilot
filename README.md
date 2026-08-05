# DevOps Knowledge Copilot

Open-source, self-hostable RAG assistant for engineering teams. Upload runbooks, incident
postmortems, CI/CD failure logs, and technical docs — then ask questions in plain language and get
answers grounded in your own material, with citations.

Full project docs (architecture, schema, API contract, security, testing, auth design) live in
[`/docs`](./docs).

## Status

Stage 4 (implementation) done — auth, document ingestion, and RAG chat are all implemented
end-to-end, frontend included. Not yet done: a real test suite (Stage 5), malware scanning on
uploads and a full security pass (Stage 6), and the actual AWS deployment (Stage 7) — right now
this only runs via docker-compose locally.

```bash
cp .env.example .env   # fill in JWT_SECRET (openssl rand -base64 48), LLM_API_KEY, EMBEDDING_API_KEY
docker compose up --build
```

Then open http://localhost:5173, register an account, upload a document, and ask it a question
once the document's status flips to INDEXED.

## Stack

Java 17 + Spring Boot (backend) · React + TypeScript (frontend) · PostgreSQL + pgvector (vector
store) · Docker (nginx serving the frontend, proxying `/api` to the backend) · AWS EKS/Fargate
(target deployment).

Backend health check: http://localhost:8080/actuator/health

## Repo layout

```
backend/    Spring Boot application
frontend/   React + TypeScript application
docs/       Project documentation, one file per SDLC stage
```
