import { defineConfig } from '@playwright/test';

// This suite runs against the real docker-compose stack (postgres + backend + frontend),
// not a mocked backend, and the chat step makes a genuine call to the configured LLM
// provider — see e2e/README.md for why this is not part of the default `ci.yml` workflow
// that runs on every push/PR.
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
  },
});
