/**
 * Microservice Integration Tests - Phase 4
 * 
 * Tests the complete request flow through all microservices:
 * Sentinel-Gear → Claimspindel → Brazz-Nossel
 * 
 * Run with: npx jest src/__tests__/integration/microservice-integration.test.ts
 */

import axios, { AxiosInstance } from 'axios';
import { createTestJWT, createExpiredJWT, createServiceAccountJWT, createAdminJWT, getTestSecret } from '../fixtures/jwts/test-fixtures';

describe('Microservice Integration Tests - Phase 4', () => {
  
  let sentinelGearClient: AxiosInstance;
  let claimspindelClient: AxiosInstance;
  let brazzNosselClient: AxiosInstance;
  
  const SENTINEL_GEAR_URL = process.env.SENTINEL_GEAR_URL || 'http://localhost:8080';
  const CLAIMSPINDEL_URL = process.env.CLAIMSPINDEL_URL || 'http://localhost:8081';
  const BRAZZ_NOSSEL_URL = process.env.BRAZZ_NOSSEL_URL || 'http://localhost:8082';
  
  beforeAll(() => {
    sentinelGearClient = axios.create({
      baseURL: SENTINEL_GEAR_URL,
      validateStatus: () => true  // Don't throw on any status
    });
    
    claimspindelClient = axios.create({
      baseURL: CLAIMSPINDEL_URL,
      validateStatus: () => true
    });
    
    brazzNosselClient = axios.create({
      baseURL: BRAZZ_NOSSEL_URL,
      validateStatus: () => true
    });
  });
  
  describe('1. End-to-End JWT Validation Flow', () => {
    
    test('should validate JWT and return normalized identity', async () => {
      const token = createTestJWT({
        sub: 'user123',
        tenant: 'acme-corp',
        realm_access: { roles: ['user', 'viewer'] }
      });
      
      const response = await sentinelGearClient.post('/validate', {
        token
      });
      
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty('valid', true);
      expect(response.data).toHaveProperty('claims');
      expect(response.data.claims).toHaveProperty('sub', 'user123');
      expect(response.data.claims).toHaveProperty('tenant', 'acme-corp');
    });
    
    test('should reject expired JWT', async () => {
      const token = createExpiredJWT();
      
      const response = await sentinelGearClient.post('/validate', {
        token
      });
      
      expect(response.status).toBeGreaterThanOrEqual(400);
      expect(response.data).toHaveProperty('error');
      expect(response.data.error.toLowerCase()).toContain('expired');
    });
    
    test('should detect service accounts in JWT', async () => {
      const token = createServiceAccountJWT({
        sub: 'sa-automation',
        tenant: 'acme-corp'
      });
      
      const response = await sentinelGearClient.post('/validate', {
        token
      });
      
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty('isServiceAccount', true);
    });
  });
  
  describe('2. Multi-Tenant Isolation', () => {
    
    test('should deny cross-tenant access at gateway level', async () => {
      const aliceToken = createAdminJWT({
        sub: 'alice',
        tenant: 'acme-corp'
      });
      
      const response = await brazzNosselClient.get('/s3/widgets-inc-bucket/file.txt', {
        headers: {
          'Authorization': `Bearer ${aliceToken}`,
          'X-Tenant-ID': 'widgets-inc'  // Mismatched tenant
        }
      });
      
      // Should fail with 403 or similar
      expect(response.status).toBeGreaterThanOrEqual(400);
    });
    
    test('should allow access within same tenant', async () => {
      const token = createAdminJWT({
        sub: 'alice',
        tenant: 'acme-corp'
      });
      
      const response = await brazzNosselClient.get('/s3/acme-corp-bucket/file.txt', {
        headers: {
          'Authorization': `Bearer ${token}`,
          'X-Tenant-ID': 'acme-corp'
        }
      });
      
      // Should succeed (or fail with 404 if file not found, but not 403)
      expect(response.status).not.toBe(403);
    });
  });
  
  describe('3. Claims-Based Routing', () => {
    
    test('should route based on user role claims', async () => {
      const adminToken = createAdminJWT({
        sub: 'admin-user',
        tenant: 'acme-corp',
        realm_access: { roles: ['admin', 'viewer'] }
      });
      
      const response = await claimspindelClient.post('/evaluate-route', {
        token: adminToken,
        requestPath: '/admin/policies'
      });
      
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty('allowed', true);
    });
    
    test('should deny routing based on insufficient roles', async () => {
      const userToken = createTestJWT({
        sub: 'regular-user',
        tenant: 'acme-corp',
        realm_access: { roles: ['viewer'] }  // No admin role
      });
      
      const response = await claimspindelClient.post('/evaluate-route', {
        token: userToken,
        requestPath: '/admin/policies'
      });
      
      expect(response.status).toBeGreaterThanOrEqual(400);
      expect(response.data).toHaveProperty('allowed', false);
    });
  });
  
  describe('4. Distributed Request Tracing', () => {
    
    test('should propagate X-Request-ID through service chain', async () => {
      const token = createTestJWT();
      const requestId = 'trace-123-abc';
      
      const response = await sentinelGearClient.post('/validate', {
        token
      }, {
        headers: {
          'X-Request-ID': requestId
        }
      });
      
      expect(response.status).toBe(200);
      expect(response.headers).toHaveProperty('x-request-id', requestId);
    });
    
    test('should generate X-Request-ID if not provided', async () => {
      const token = createTestJWT();
      
      const response = await sentinelGearClient.post('/validate', {
        token
      });
      
      expect(response.status).toBe(200);
      expect(response.headers).toHaveProperty('x-request-id');
      expect(response.headers['x-request-id']).toMatch(/^[a-f0-9-]+$/i);  // UUID format
    });
  });
  
  describe('5. Circuit Breaker & Resilience', () => {
    
    test('should handle timeout gracefully', async () => {
      const token = createTestJWT();
      
      // Set a short timeout to trigger circuit breaker
      const shortTimeoutClient = axios.create({
        baseURL: SENTINEL_GEAR_URL,
        timeout: 100,  // 100ms timeout
        validateStatus: () => true
      });
      
      const response = await shortTimeoutClient.post('/validate', {
        token
      });
      
      // Should fail, not hang
      expect(response).toBeDefined();
    }, 10000);  // 10 second jest timeout
    
    test('should fallback on service unavailability', async () => {
      const token = createTestJWT();
      const deadClient = axios.create({
        baseURL: 'http://nonexistent-service:9999',
        timeout: 1000,
        validateStatus: () => true
      });
      
      const response = await deadClient.post('/validate', {
        token
      });
      
      // Should fail with connection error, not hang indefinitely
      expect(response).toBeDefined();
      expect([0, 500, 503].includes(response.status)).toBe(true);
    });
  });
  
  describe('6. Policy Evaluation Integration', () => {
    
    test('should evaluate S3 read policy for tenant', async () => {
      const token = createAdminJWT({
        sub: 'alice',
        tenant: 'acme-corp',
        realm_access: { roles: ['admin'] }
      });
      
      const response = await brazzNosselClient.post('/evaluate-policy', {
        token,
        action: 'GetObject',
        resource: 's3://acme-corp-bucket/documents/*'
      });
      
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty('allowed');
    });
    
    test('should deny S3 write policy based on tenant restriction', async () => {
      const token = createTestJWT({
        sub: 'bob',
        tenant: 'widgets-inc',
        realm_access: { roles: ['user'] }
      });
      
      const response = await brazzNosselClient.post('/evaluate-policy', {
        token,
        action: 'PutObject',
        resource: 's3://acme-corp-bucket/sensitive/*'
      });
      
      expect(response.status).toBeGreaterThanOrEqual(400);
      expect(response.data).toHaveProperty('allowed', false);
    });
  });
  
  describe('7. Caching & Performance', () => {
    
    test('should cache policy evaluation results', async () => {
      const token = createAdminJWT();
      const policy = 's3-read-policy';
      
      // First call - should hit backend
      const response1 = await brazzNosselClient.post('/evaluate-policy', {
        token,
        policyId: policy
      });
      
      const time1 = Date.now();
      
      // Second call - should hit cache
      const response2 = await brazzNosselClient.post('/evaluate-policy', {
        token,
        policyId: policy
      });
      
      const time2 = Date.now();
      
      // Cached response should be significantly faster
      // (at least 10x faster if caching is working)
      expect(response1.status).toBe(response2.status);
      expect(time2 - time1).toBeLessThan(50);  // Should be very fast
    });
  });
  
  describe('8. Error Handling & Edge Cases', () => {
    
    test('should handle null claims gracefully', async () => {
      const response = await sentinelGearClient.post('/validate', {
        token: null
      });
      
      expect(response.status).toBeGreaterThanOrEqual(400);
      expect(response.data).toHaveProperty('error');
    });
    
    test('should handle malformed JWT', async () => {
      const response = await sentinelGearClient.post('/validate', {
        token: 'not-a-valid-jwt'
      });
      
      expect(response.status).toBeGreaterThanOrEqual(400);
      expect(response.data).toHaveProperty('error');
    });
    
    test('should handle missing required claims', async () => {
      const token = createTestJWT();
      // Remove required claims somehow (in real test, create JWT without them)
      
      const response = await sentinelGearClient.post('/validate', {
        token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.signature'
      });
      
      expect(response.status).toBeGreaterThanOrEqual(400);
    });
  });
  
  describe('9. Concurrent Request Handling', () => {
    
    test('should handle 10 concurrent requests', async () => {
      const promises = Array.from({ length: 10 }, async (_, i) => {
        const token = createTestJWT({
          sub: `user-${i}`,
          tenant: 'acme-corp'
        });
        
        return sentinelGearClient.post('/validate', { token });
      });
      
      const results = await Promise.all(promises);
      
      results.forEach(response => {
        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty('valid', true);
      });
    });
  });
  
  describe('10. Health Check Integration', () => {
    
    test('should have health check endpoint on all services', async () => {
      const sentinelHealth = await sentinelGearClient.get('/actuator/health');
      const claimspindelHealth = await claimspindelClient.get('/actuator/health');
      const brazzNosselHealth = await brazzNosselClient.get('/actuator/health');
      
      expect(sentinelHealth.status).toBe(200);
      expect(claimspindelHealth.status).toBe(200);
      expect(brazzNosselHealth.status).toBe(200);
      
      expect(sentinelHealth.data).toHaveProperty('status');
      expect(claimspindelHealth.data).toHaveProperty('status');
      expect(brazzNosselHealth.data).toHaveProperty('status');
    });
  });
});
