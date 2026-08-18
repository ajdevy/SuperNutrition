import { defineConfig } from 'vitest/config';

// AD-33: every compiled production source belongs to exactly one coverage
// denominator, and the product aggregate maintains at least 85% line and branch
// coverage. Thresholds are enforced here so a drop fails the run rather than a
// dashboard.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['apps/**/*.test.ts', 'packages/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'json-summary', 'lcov'],
      reportsDirectory: './coverage',
      all: true,
      include: ['apps/api/src/**/*.ts', 'packages/*/src/**/*.ts'],
      exclude: [
        // Generated output and non-executable declaration surfaces only.
        // AD-32: exclusions are exact paths, never broad patterns.
        '**/*.test.ts',
        '**/dist/**',
        'packages/database/src/migrations/**',
      ],
      thresholds: {
        lines: 85,
        branches: 85,
        functions: 85,
        statements: 85,
      },
    },
  },
});
