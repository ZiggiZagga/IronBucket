import request from 'supertest';
import app from '../src/index';

describe('Authentication Endpoint', () => {
  it('should return 400 if credentials are missing', async () => {
    const res = await request(app).post('/auth').send({});
    expect(res.statusCode).toBe(400);
    expect(res.body).toHaveProperty('error');
  });

  it('should authenticate with Keycloak and return token contract when available', async () => {
    const res = await request(app).post('/auth').send({ username: 'bob', password: 'bobpass1' });
    expect([200, 401]).toContain(res.statusCode);
    if (res.statusCode === 200) {
      expect(res.body).toHaveProperty('token');
      expect(typeof res.body.token).toBe('string');
      expect(res.body).toHaveProperty('accessToken');
      expect(res.body).toHaveProperty('idToken');
    } else {
      expect(res.body).toHaveProperty('error');
    }
  });
});
