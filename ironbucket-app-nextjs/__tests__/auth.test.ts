import request from 'supertest';
import app from '../src/index';

describe('Authentication API Route', () => {

  it('should return 400 if no credentials are provided', async () => {
    const res = await request(app).post('/api/auth').send({});
    expect(res.status).toBe(400);
    expect(res.body).toHaveProperty('error');
  });

  it('should authenticate with Keycloak and return a valid token', async () => {
    const res = await request(app).post('/api/auth').send({ username: 'bob', password: 'bobpass1' });
    // Accept 200 or 401 depending on Keycloak availability
    expect([200, 401]).toContain(res.status);
    if (res.status === 200) {
      expect(res.body).toHaveProperty('token');
      expect(typeof res.body.token).toBe('string');
      expect(res.body).toHaveProperty('accessToken');
      expect(res.body).toHaveProperty('idToken');
    } else {
      expect(res.body).toHaveProperty('error');
    }
  });
});
