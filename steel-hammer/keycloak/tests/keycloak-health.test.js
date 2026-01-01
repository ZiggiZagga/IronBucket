// Test: Keycloak Health and HTTPS
// Run with: npx jest tests/keycloak-health.test.js

const axios = require('axios');

describe('Keycloak Container Health', () => {
  it('should respond on HTTP port', async () => {
    try {
      const res = await axios.get('http://localhost:7081/health');
      expect(res.status).toBe(200);
    } catch (err) {
      throw new Error('Keycloak is not healthy or not running on port 7081');
    }
  });

  it('should NOT allow HTTP if HTTPS is enforced', async () => {
    try {
      await axios.get('http://localhost:7081');
      throw new Error('HTTP should not be allowed when HTTPS is enforced');
    } catch (err) {
      expect(err.response).toBeUndefined();
    }
  });

  it('should respond on HTTPS port', async () => {
    try {
      const res = await axios.get('https://localhost:7082', { httpsAgent: new (require('https').Agent)({ rejectUnauthorized: false }) });
      expect(res.status).toBe(200);
    } catch (err) {
      throw new Error('Keycloak is not healthy or not running on port 7082');
    }
  });
});
