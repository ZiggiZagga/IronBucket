// Test: WebAuthn/Passkey Support in Keycloak
// Run with: npx jest tests/keycloak-webauthn.test.js

const axios = require('axios');

describe('Keycloak WebAuthn/Passkey Support', () => {
  it('should have WebAuthn/Passkey enabled in realm config', async () => {
    // This test assumes Keycloak admin API is accessible and admin credentials are set
    const adminUser = process.env.KC_ADMIN_USER || 'admin';
    const adminPass = process.env.KC_ADMIN_PASS || 'admin';
    const baseUrl = 'http://localhost:7081';
    let token;
    try {
      // Get admin token
      const res = await axios.post(`${baseUrl}/realms/master/protocol/openid-connect/token`,
        new URLSearchParams({
          username: adminUser,
          password: adminPass,
          grant_type: 'password',
          client_id: 'admin-cli',
        }),
        { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } }
      );
      token = res.data.access_token;
    } catch (err) {
      throw new Error('Failed to get admin token: ' + err.message);
    }
    try {
      // Get realm config
      const realm = 'ironbucket-lab';
      const res = await axios.get(`${baseUrl}/admin/realms/${realm}/authentication/flows`, {
        headers: { Authorization: `Bearer ${token}` }
      });
      const hasWebAuthn = res.data.some(flow => flow.alias && flow.alias.toLowerCase().includes('webauthn'));
      expect(hasWebAuthn).toBe(true);
    } catch (err) {
      throw new Error('WebAuthn/Passkey flow not found in realm: ' + err.message);
    }
  });
});
