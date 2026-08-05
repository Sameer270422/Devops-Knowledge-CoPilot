# End-to-end tests

`register-login-chat.spec.ts` walks the full product flow against the **real**
docker-compose stack: register → auto-login → upload a runbook → wait for it to be
indexed → ask a question → assert the answer is grounded in, and cites, that document.

## Why this isn't in the default `ci.yml`

Unlike the backend/frontend unit and integration tests, this suite:

- needs the full stack running (`docker compose up`), and
- makes a real call to the configured LLM and embedding providers, which costs money and
  can be flaky (provider latency/rate limits), unlike the fully mocked or
  Testcontainers-isolated tests in `backend/` and `frontend/`.

Running it on every push or PR — including PRs from forks, which shouldn't have access to
real API keys anyway — isn't the right trade-off for a project this size. Instead it's
wired as a manually-triggered workflow (`.github/workflows/e2e.yml`, `workflow_dispatch`)
that a maintainer runs deliberately (e.g. before a release), and it's meant to be run
locally the same way.

## Running locally

```bash
# from the repo root, with a real .env already filled in
docker compose up -d --build

cd e2e
npm install
npx playwright install --with-deps chromium
npm test
```

The test polls for the upload to reach `INDEXED` status (up to 60s) and for the chat
reply to arrive (up to 30s) — ingestion and generation both happen asynchronously against
real external APIs, so these aren't instant.
