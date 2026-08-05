# Stage 5 — Testing

## What was built

### Backend unit tests (`backend/src/test/java/com/copilot/...`)

- `ChunkingServiceTest` — short text stays one chunk, blank input doesn't crash, long text
  splits into multiple overlapping chunks, the overlap window actually repeats content
  across adjacent chunks, and an oversized single paragraph (no natural break point) still
  gets hard-split rather than producing one giant chunk.
- `PasswordValidatorTest` — null handling, and parameterized cases across the length /
  uppercase / number / symbol rules, including the 9-vs-10-character boundary.
- `JwtServiceTest` — constructor rejects a secret under 32 chars, a token round-trips
  through generate → parse, a token signed with a different secret is rejected, and refresh
  tokens are high-entropy, hashed (never stored raw), and carry the configured expiry.
- `PromptBuilderTest` — the concrete test of the prompt-injection defense from the security
  plan: asserts the system prompt tells the model to treat `<context>` blocks as data, that
  each retrieved chunk is actually wrapped in a labeled `<context>` block, that malicious
  instructions embedded inside a chunk's content stay contained inside that block, and that
  an empty retrieval set still produces a sane prompt instead of an empty one.
- `AuthServiceTest` (Mockito) — register, login (success and wrong-password), refresh
  (rotates the token, rejects a reused/revoked one), logout, and the rate-limiter lockout
  after 5 failed attempts.

### Backend integration test (`backend/src/test/java/com/copilot/vector/VectorStoreIntegrationTest.java`)

Runs against a real, disposable `pgvector/pgvector:pg16` container via Testcontainers —
not mocks — because `VectorStore` speaks raw pgvector SQL (`embedding <=> ?::vector`) that
a mocked `JdbcTemplate` can't meaningfully verify. Flyway applies the real `V1__init.sql`
migration against the container on context startup, same as production. Two tests:

1. `similaritySearchReturnsTheClosestVectorFirst` — stores two chunks with different
   embeddings, confirms the nearer one by cosine distance ranks first.
2. `oneUsersChunksAreNeverReturnedForAnotherUsersSearch` — the concrete test of the
   project's core security claim. User B searches with the *exact same* query vector user
   A's chunk was stored under, and must get zero results back. This is enforced by the
   `WHERE c.user_id = ?` clause in `VectorStore.similaritySearch` — the test exists so that
   clause can never be silently weakened in a future refactor without a test failing.

A real bug was caught writing this test, before it ever ran against a container: both
`documents.user_id` and `chunks.user_id` have `REFERENCES users(id)`, so inserting test
rows for a random, non-existent user id would have failed on a foreign-key violation. Fixed
by adding an `insertTestUser()` helper that creates a real `users` row first.

### Frontend unit tests (`frontend/src`)

- `utils/validation.test.ts` — pure-function tests for `isValidEmail` (valid/invalid
  formats, whitespace handling) and the four password rules plus `isPasswordValid`.
- `pages/RegisterPage.test.tsx` (Vitest + React Testing Library) — invalid email blocks
  submission and shows the error inline and in the banner; a weak password blocks
  submission; the live password checklist marks rules as met while typing; a successful
  register call navigates to `/documents`; a failed register call (mocked `ApiError`, e.g.
  "email already registered") shows the server's message instead of navigating.
- Added `vitest`'s `test` config block to `vite.config.ts` (`environment: 'jsdom'`), plus
  `jsdom` and `@testing-library/jest-dom` as new devDependencies.

One real gap was caught here too: `@testing-library/jest-dom`'s plain import only
type-augments Jest's `expect`, not Vitest's — `tsc --noEmit` (part of `npm run build`)
would have failed on every `.toBeInTheDocument()` call even though the tests pass fine at
runtime. Fixed by importing the `@testing-library/jest-dom/vitest` subpath in the test
setup file instead.

### End-to-end test (`e2e/`)

`register-login-chat.spec.ts` (Playwright) walks the full product flow against the real
docker-compose stack: register → auto-login → upload `fixtures/sample-runbook.txt` → poll
until its status badge flips to `INDEXED` → ask a question whose answer only exists in that
file → assert the reply contains the right fact and cites the filename.

This one is **not** wired into the default `ci.yml` — it needs the full stack running and
makes a real, billed call to the LLM/embedding providers, which is a bad trade-off to run
on every push or PR (cost, flakiness, and PRs from forks shouldn't have API keys anyway).
Instead it's a separate `.github/workflows/e2e.yml` triggered manually
(`workflow_dispatch`), meant to be run by a maintainer before a release — see
`e2e/README.md` for the reasoning and local run instructions.

### CI wiring

`ci.yml`'s frontend job now runs `npm test` before `npm run build`, so a broken test
fails the build the same way a broken `tsc` check does. The backend job already runs
`gradle build`, which picks up the new test files (including the Testcontainers test)
automatically — no change needed there beyond a comment noting GitHub-hosted runners have
Docker pre-installed, which is what Testcontainers needs.

## Verification

Backend: same sandbox constraint as every prior stage (no Java 17 / Gradle here) — checked
via brace-balance on all 6 new test files (all matched) and by tracing each test's
assumptions against the actual `V1__init.sql` schema and `VectorStore`/`RetrievedChunk`
source (this is what caught the foreign-key bug above). Real `gradle build` execution
should happen on your machine as the actual gate.

Frontend: this stage was different — the sandbox's npm install actually succeeded this
time, so the test suite was **run for real**, not just statically reviewed. First run
found a genuine bug (a test asserting on text that legitimately renders twice — the error
banner and the inline field error both show the same message — fixed to assert on both via
`findAllByText`). After that fix: **18/18 tests passing**, `tsc --noEmit` clean, and
`vite build` (the actual Docker build step) succeeds.

E2E: the spec was type-checked (`tsc --noEmit` against `@playwright/test`'s real types, 0
errors) but not run against a live stack — that requires Docker Compose and real API keys,
neither available in this sandbox. Run it locally per `e2e/README.md` before relying on it.

## Known gaps

- **RAG evaluation** (a small labeled question/expected-answer set to catch retrieval
  regressions) was in the original Stage 5 plan but not built — the three tests above cover
  correctness and security; answer-quality regression testing is a reasonable Stage 6+
  addition once there's more real usage to build a labeled set from.
- **E2E test is unverified by an actual run** in this environment, as noted above.
- **Citations-on-reload gap** (noted in Stage 4) is still open — untouched by this stage.
