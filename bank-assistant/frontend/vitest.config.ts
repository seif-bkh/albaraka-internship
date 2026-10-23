import { defineConfig } from 'vitest/config';

/**
 * Merged into the Angular unit-test builder's Vitest config (`runnerConfig`).
 * Coverage gate (7.1): scoped by `coverageInclude` in angular.json to the
 * service layer — the SPA's only pure logic; pages are covered by e2e (7.3).
 * Measured 52% lines when introduced (auth + governance fully covered, legacy
 * document/user/stat/conversation services not yet). Ratchet up, never down.
 */
export default defineConfig({
  test: {
    coverage: {
      thresholds: { lines: 50, statements: 50 },
    },
  },
});
