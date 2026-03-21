import { defineConfig } from '@playwright/test';

const reportRoot = process.env.PLAYWRIGHT_REPORT_DIR ?? '/workspaces/IronBucket/test-results';

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  retries: 0,
  outputDir: `${reportRoot}/ui-playwright-artifacts`,
  reporter: [
    ['list'],
    ['json', { outputFile: `${reportRoot}/ui-playwright-report.json` }],
    ['junit', { outputFile: `${reportRoot}/ui-playwright-report.xml` }],
    ['html', { outputFolder: `${reportRoot}/ui-playwright-html`, open: 'never' }]
  ],
  use: {
    baseURL: 'http://127.0.0.1:3000',
    trace: 'retain-on-failure'
  },
  webServer: {
    command: 'npm run start -- --hostname 127.0.0.1 --port 3000',
    url: 'http://127.0.0.1:3000',
    timeout: 120_000,
    reuseExistingServer: true,
    env: {
      NEXT_PUBLIC_GRAPHQL_ENDPOINT:
        process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT ?? 'https://127.0.0.1:8080/graphql',
      E2E_SENTINEL_URL: process.env.E2E_SENTINEL_URL ?? 'https://127.0.0.1:8080',
      E2E_GATEWAY_GRAPHQL_URL:
        process.env.E2E_GATEWAY_GRAPHQL_URL ?? 'https://127.0.0.1:8080/graphql',
      E2E_KEYCLOAK_TOKEN_URL:
        process.env.E2E_KEYCLOAK_TOKEN_URL ??
        'https://127.0.0.1:7081/realms/dev/protocol/openid-connect/token',
      OTEL_SERVICE_NAME: process.env.OTEL_SERVICE_NAME ?? 'ironbucket-app-nextjs',
      OTEL_EXPORTER_OTLP_ENDPOINT:
        process.env.OTEL_EXPORTER_OTLP_ENDPOINT ?? 'http://127.0.0.1:4318'
    }
  }
});
