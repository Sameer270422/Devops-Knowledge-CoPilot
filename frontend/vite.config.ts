// Imported from 'vitest/config' rather than plain 'vite' so the `test` block below is
// type-checked — it's a superset of vite's defineConfig, not a different build tool.
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      // In dev, frontend talks to the Spring Boot backend directly.
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
  },
});
