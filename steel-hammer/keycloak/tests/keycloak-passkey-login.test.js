// Test: Keycloak User Login with Passkey (WebAuthn)
// This is a placeholder for a browser-based test (e.g. Playwright or Puppeteer)
// Here we only check if the WebAuthn login page is available

const axios = require('axios');

describe('Keycloak Passkey Login Page', () => {
  it('should expose WebAuthn login option on login page', async () => {
    const baseUrl = 'http://localhost:7081';
    const realm = 'ironbucket-lab';
    const clientId = 'sentinel-gear-app';
    try {
      const res = await axios.get(`${baseUrl}/realms/${realm}/protocol/openid-connect/auth?client_id=${clientId}&response_type=code`);
      expect(res.data).toMatch(/webauthn|passkey/i);
    } catch (err) {
      throw new Error('WebAuthn/Passkey option not found on login page: ' + err.message);
    }
  });
});
