# Stage 4 — Implementation

## What was built

All three backend modules from the Stage 2 design, plus a working frontend wired to them.

### Auth module (`backend/.../auth`)
- `User`, `RefreshToken` entities; JWT access tokens (15 min) via `JwtService` (jjwt);
  opaque refresh tokens, stored only as a SHA-256 hash (`RefreshTokenRepository`), rotated
  on every use.
- `JwtAuthFilter` populates `SecurityContext` per request; `AuthenticatedUser` /
  `CurrentUser` give controllers the caller's id without a DB round trip.
- `LoginRateLimiter` — 5 failed attempts per email locks out for 15 minutes (in-memory;
  moves to Redis when the app runs multi-replica).
- `AuthController` — register/login/refresh/logout, refresh token set as an httpOnly,
  `SameSite=Strict` cookie, never in a JSON body.
- `SecurityConfig` updated: JWT filter wired in, CORS locked to a single configured
  origin (never `*`), CSRF disabled with the reasoning documented inline (header-carried
  access token + SameSite cookie already cover it).

### Document module (`backend/.../document`)
- `Document`, `Chunk` entities. `Chunk` intentionally has no JPA-mapped `embedding` field
  — pgvector's type isn't a first-class Hibernate type, so that column is written/read via
  plain JDBC in `VectorStore` instead (see below).
- `DocumentUploadValidator` — extension allowlist, size limit. **Malware/virus scanning is
  not implemented** (needs an external scanner like ClamAV this environment can't stand
  up) — flagged as a known gap for Stage 6, not silently skipped.
- `FileStorageService` interface with two implementations: `LocalFileStorageService`
  (default, used by docker-compose) and `S3FileStorageService` (`aws` profile, credentials
  come from the pod's IRSA role — never a static access key).
- `TextExtractor` (PDFBox for `.pdf`, plain read for `.txt`/`.md`/`.log`/`.csv`) →
  `ChunkingService` (paragraph-aware, ~700 tokens/chunk, ~12% overlap) →
  `IngestionService.ingestAsync` runs the whole pipeline off the request thread
  (`AsyncConfig`), updating `document.status` as it goes so the frontend can poll.

### Vector store & LLM integration (`backend/.../vector`, `.../llm`)
- `VectorStore` — the one place that speaks pgvector SQL directly (`embedding <=> ?::vector`
  cosine search via `JdbcTemplate`). Every query is scoped by `user_id`; this is the actual
  enforcement point for "no cross-user data leakage," not a filter bolted on after the fact.
- `EmbeddingClient` → `OpenAiEmbeddingClient` (`text-embedding-3-small`, plain
  `java.net.http.HttpClient`, no SDK dependency).
- `ChatLlmClient` → `AnthropicChatClient` (default) or `OpenAiChatClient`, selected by
  `app.llm.provider`.

### Chat / RAG module (`backend/.../chat`)
- `ChatSession`, `ChatMessage` (citations stored as `jsonb` via Hibernate 6's native
  `@JdbcTypeCode(SqlTypes.JSON)` — no manual converter needed).
- `RetrievalService` embeds the question, retrieves top-k chunks scoped to the caller.
- `PromptBuilder` — this is the concrete prompt-injection defense from the security plan:
  retrieved content is wrapped in labeled `<context>` blocks with an explicit system-prompt
  instruction to treat it as data, never as instructions.
- `ChatService.ask()` — save user message → retrieve → build prompt → generate → save
  assistant message with cited chunk ids → return citations (filename + excerpt) to the client.

### Frontend (`frontend/src`)
- `AuthContext` — access token kept in memory only (never localStorage); silent resume on
  page load via the refresh cookie; `api/client.ts` auto-retries one 401 through
  `/api/auth/refresh` before giving up.
- Pages: `LoginPage`, `RegisterPage`, `DocumentsPage` (upload with source-type tagging,
  status polling while ingestion runs, delete), `ChatPage` (session list, ask/answer with
  cited filenames shown under each response).
- `ProtectedRoute` + `Layout` gate everything except `/login` and `/register`.

## Database migration
`V1__init.sql` (Flyway) now matches the live entities exactly — `users`, `refresh_tokens`,
`documents`, `chunks` (with the `vector(1536)` column and an HNSW cosine index),
`chat_sessions`, `chat_messages`.

## Fixed while building
The CI workflow from Stage 3 would have failed the moment a real test existed: it ran
`./gradlew build` with no committed wrapper binary and no database for the
`@SpringBootTest` smoke test to connect to. Fixed by pinning Gradle via
`gradle/actions/setup-gradle` (no wrapper jar needed) and adding a `pgvector/pgvector:pg16`
service container with CI-only dummy secrets to the backend job.

## Known gaps (deliberately not done here)

- **Malware scanning on upload** — noted above, needs an external scanner.
- **CopilotApplicationTests is still just a context-load smoke test** — real unit,
  integration, and RAG-evaluation tests are Stage 5's job, not squeezed in here.
- **Citations on reloaded chat history are empty** — `cited_chunk_ids` is persisted, but
  `ChatController.getMessages` doesn't re-hydrate filenames/excerpts for old messages yet
  (only for the message just generated in the same request).
- **No committed `gradlew` wrapper** — CI and Docker both pin Gradle 8.9 explicitly instead;
  works fine, just means `./gradlew` won't work locally without running `gradle wrapper` once.

## Verification

Same constraint as Stage 3: this sandbox can't run a full `gradle build` (Java 11, no
Gradle binary) or complete `npm install` (slow network path). What was checked instead:
brace/paren balance across all 66 Java files (clean), the full file tree against the
Stage 2 API contract and schema (all 15 endpoints and 6 tables implemented), and
YAML/JSON syntax on every config file touched. Full build verification — `docker compose
up --build`, hitting each endpoint — should happen on your machine as the real gate before
Stage 5.

## Post-implementation fixes (found while first running it)

Three real bugs surfaced only once the app was actually run end-to-end, all now fixed:

1. **Frontend build script (`tsc -b`)** — used TypeScript's project-references build mode
   without the composite/references config it requires. Fixed to `tsc --noEmit`.
2. **`ChatLlmClient` bean not found at startup** — the original design used
   `@ConditionalOnProperty` on `AnthropicChatClient`/`OpenAiChatClient` to pick between
   them via an exact string match against `app.llm.provider`. This is fragile — any stray
   whitespace in that value (e.g. from a Windows-edited `.env` file) makes both
   conditions fail with no bean registered. Replaced with `LlmConfig`, a single
   `@Configuration` class that trims/lower-cases the value explicitly and instantiates
   the right client directly. Both client classes are now plain (unannotated) classes.
3. **Frontend container crash-looping (`exec format error`) and a missing API proxy** —
   the original frontend Dockerfile served the built app with the `serve` npm package,
   which failed at runtime. While fixing it, found a second, more important bug it was
   masking: `serve` has no reverse proxy, and Vite's dev-time `/api` proxy
   (`vite.config.ts`) only applies to `npm run dev` — the production build had no
   equivalent, so every API call from the browser would have 404'd even if `serve` had
   worked. Replaced with nginx (`frontend/nginx.conf`), which serves the static build,
   proxies `/api/` and `/actuator/` to the backend container, and handles SPA routing
   fallback so client-side routes survive a page refresh.

Also missing from the original scaffold: `.dockerignore` in both `backend/` and
`frontend/`. Without it, a local `node_modules` (in this case, a partially-corrupted one
from an earlier interrupted install) got copied into the image via `COPY . .`, clobbering
the container's own fresh install. Added `.dockerignore` to both services.

**Verified working end-to-end**: `docker compose up --build` now brings up Postgres
(healthy), backend (Flyway migration applies cleanly, `LlmConfig` selects a provider,
Tomcat starts), and frontend (nginx serves the app on :5173, proxies :80/api to backend)
with no errors — this is confirmed from an actual run, not just static review.
