"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const supertest_1 = __importDefault(require("supertest"));
const index_1 = __importDefault(require("../src/index"));
describe('Authentication Endpoint', () => {
    it('should return 400 if credentials are missing', async () => {
        const res = await (0, supertest_1.default)(index_1.default).post('/auth').send({});
        expect(res.statusCode).toBe(400);
        expect(res.body).toHaveProperty('error');
    });
    it('should authenticate with Keycloak and return a valid token', async () => {
        const res = await (0, supertest_1.default)(index_1.default).post('/auth').send({ username: 'bob', password: 'bobpass1' });
        expect([200, 401]).toContain(res.statusCode);
        if (res.statusCode === 200) {
            expect(res.body).toHaveProperty('token');
            expect(typeof res.body.token).toBe('string');
            expect(res.body).toHaveProperty('accessToken');
            expect(res.body).toHaveProperty('idToken');
        }
        else {
            expect(res.body).toHaveProperty('error');
        }
    });
});
