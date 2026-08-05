import { test, expect } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FIXTURE_PATH = path.join(__dirname, '..', 'fixtures', 'sample-runbook.txt');

// Full happy-path walkthrough of the product's core value proposition: register, upload a
// document, wait for it to be indexed, then ask a question and get back an answer grounded
// in — and citing — that specific document. Requires the real docker-compose stack (a live
// Postgres, the Spring Boot backend, and the nginx-served frontend) plus real LLM/embedding
// API keys, since this exercises the actual RAG pipeline end to end rather than mocks.
test('register, upload a runbook, and get a grounded, cited chat answer', async ({ page }) => {
  const uniqueEmail = `e2e-${Date.now()}@example.com`;
  const password = 'Str0ngPass!42';

  await page.goto('/register');
  await page.getByLabel('Email').fill(uniqueEmail);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: /create account/i }).click();

  // A successful register auto-logs-in and redirects to /documents (see RegisterPage.tsx).
  await expect(page).toHaveURL(/\/documents$/, { timeout: 15_000 });

  const fileChooserPromise = page.waitForEvent('filechooser');
  await page.getByRole('button', { name: 'Choose file' }).click();
  const fileChooser = await fileChooserPromise;
  await fileChooser.setFiles(FIXTURE_PATH);
  await page.getByRole('button', { name: 'Upload' }).click();

  // Ingestion (extraction -> chunking -> embedding) runs asynchronously on the backend;
  // the UI polls every 3s (see DocumentsPage.tsx) so we just wait for the badge to flip.
  const statusBadge = page.locator('tr', { hasText: 'sample-runbook.txt' }).locator('.badge');
  await expect(statusBadge).toHaveText('INDEXED', { timeout: 60_000 });

  await page.getByRole('link', { name: 'Chat' }).click();
  await page.getByPlaceholder('Ask about your documents…').fill(
    'How many replicas should zephyr-notification-worker be scaled to for the ZephyrQueueBacklog alert?',
  );
  await page.getByRole('button', { name: 'Send' }).click();

  const assistantBubble = page.locator('.chat-bubble.assistant').last();
  await expect(assistantBubble).toContainText('12', { timeout: 30_000 });

  const sources = page.locator('.chat-sources').last();
  await expect(sources).toContainText('sample-runbook.txt');
});
