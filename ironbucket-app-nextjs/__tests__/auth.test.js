"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const supertest_1 = __importDefault(require("supertest"));
const index_1 = __importDefault(require("../src/index"));
describe('Authentication Endpoint', () => {
    it('should be reachable and return 200', async () => {
        const res = await (0, supertest_1.default)(index_1.default).post('/auth').send({});
        expect(res.statusCode).toBe(200);
        expect(res.body).toHaveProperty('message', 'Auth endpoint reachable');
    });
    it('should authenticate with Keycloak and return a valid token', async () => {
        const res = await (0, supertest_1.default)(index_1.default).post('/auth').send({ username: 'bob', password: 'bobpass1' });
        expect(res.statusCode).toBe(200);
        expect(res.body).toHaveProperty('token');
        expect(typeof res.body.token).toBe('string');
    });
});
