import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: 'http://127.0.0.1:3000',
    trace: 'on-first-retry'
  },
  webServer: {
    command: 'npm run start -- --hostname 127.0.0.1 --port 3000',
    url: 'http://127.0.0.1:3000',
    timeout: 120_000,
    reuseExistingServer: true,
    env: {
      NEXT_PUBLIC_GRAPHQL_ENDPOINT:
        process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT ?? 'http://127.0.0.1:8080/graphql',
      E2E_SENTINEL_URL: process.env.E2E_SENTINEL_URL ?? 'http://127.0.0.1:8080',
      E2E_GATEWAY_GRAPHQL_URL:
        process.env.E2E_GATEWAY_GRAPHQL_URL ?? 'http://127.0.0.1:8080/graphql',
      E2E_KEYCLOAK_TOKEN_URL:
        process.env.E2E_KEYCLOAK_TOKEN_URL ??
        'http://127.0.0.1:7081/realms/dev/protocol/openid-connect/token'
    }
  }
});
