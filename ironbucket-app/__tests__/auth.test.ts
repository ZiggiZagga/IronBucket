import request from 'supertest';
import app from '../src/index';

describe('Authentication Endpoint', () => {
  it('should be reachable and return 200', async () => {
    const res = await request(app).post('/auth').send({});
    expect(res.statusCode).toBe(200);
    expect(res.body).toHaveProperty('message', 'Auth endpoint reachable');
  });

  it('should authenticate with Keycloak and return a valid token (placeholder)', async () => {
    // Placeholder for Keycloak integration
    // const res = await request(app).post('/auth/keycloak').send({ username: 'bob', password: 'bobP@ss' });
    // expect(res.statusCode).toBe(200);
    // expect(res.body).toHaveProperty('token');
    expect(true).toBe(false); // Fails until implemented
  });
});
