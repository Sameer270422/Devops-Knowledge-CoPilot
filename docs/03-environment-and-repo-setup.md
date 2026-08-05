# Stage 3 — Environment & Repo Setup

## What was scaffolded

A monorepo (`devops-knowledge-copilot/`) with a `backend/` (Spring Boot) and `frontend/`
(React + TypeScript) project, wired together for local development via Docker Compose, plus a
CI skeleton.

```
devops-knowledge-copilot/
├── backend/
│   ├── build.gradle, settings.gradle       # Java 17, Spring Boot 3.3
│   ├── src/main/java/com/copilot/
│   │   ├── CopilotApplication.java         # entry point
│   │   ├── config/SecurityConfig.java      # default-deny baseline; JWT filter added in Stage 4
│   │   ├── auth/, document/, chat/         # empty modules with package-info.java placeholders
│   ├── src/main/resources/application.yml  # env-var driven config, actuator health+prometheus
│   ├── src/test/.../CopilotApplicationTests.java  # context-loads smoke test
│   └── Dockerfile                          # multi-stage: gradle:8.9-jdk17 -> eclipse-temurin:17-jre-alpine
├── frontend/
│   ├── package.json, tsconfig.json, vite.config.ts
│   ├── src/main.tsx, src/App.tsx           # boots and pings /actuator/health
│   └── Dockerfile                          # multi-stage: node:20-alpine build -> serve static
├── docker-compose.yml                      # postgres (pgvector/pgvector:pg16) + backend + frontend
├── .env.example                            # DB creds, JWT_SECRET, LLM_PROVIDER, LLM_API_KEY
├── .gitignore
├── README.md
└── .github/workflows/ci.yml                # gradle build + npm build, runs on push/PR
```

## Design decisions

- **Single Spring Boot app, not microservices** — `auth`, `document`, `chat` are packages inside one
  deployable unit, matching the Stage 2 architecture. Keeps deployment simple for a portfolio-scale
  project while still enforcing clean module boundaries.
- **Security default-deny from commit one** — `SecurityConfig` locks every endpoint except
  `/actuator/health` and `/api/auth/**` before a single business feature exists, so nothing ships
  accidentally open. The JWT filter itself is implemented in Stage 4 alongside the auth module.
- **pgvector via the official `pgvector/pgvector:pg16` image** — no manual extension install step
  needed for local dev; the same approach carries over to RDS in Stage 7 (`CREATE EXTENSION vector`).
- **CI runs on GitHub Actions** — no server to maintain for an open-source repo; contributors get
  build+test feedback on every PR with zero setup. A Jenkins pipeline is added in Stage 7 for the
  actual AWS deployment automation, reusing Samir's existing Jenkins/Groovy experience where it adds
  more value (infra + deploy) rather than duplicating what Actions already covers (build + test).
- **All secrets via `.env` / environment variables**, never committed — `.env.example` documents
  what's required without containing real values.

## Verification

- `application.yml`, `docker-compose.yml`, and `.github/workflows/ci.yml` were parsed with a YAML
  linter — all valid.
- `package.json` / `tsconfig.json` are valid JSON.
- Full `docker compose up --build` and `./gradlew build` were **not** run in this environment (the
  sandbox has Java 11 and a slow npm network path; the project targets Java 17 / Node 20, which the
  Dockerfiles pin explicitly). Run these locally as the real verification step:

```bash
cp .env.example .env   # fill in JWT_SECRET (openssl rand -base64 48) and LLM_API_KEY
docker compose up --build
curl http://localhost:8080/actuator/health   # expect {"status":"UP"}
```

## Next

Stage 4 fills in the `auth`, `document`, and `chat` packages: JWT issuance/validation, user
registration/login, document upload + async chunking/embedding pipeline, and the RAG retrieval +
chat endpoint.
