"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const express_1 = __importDefault(require("express"));
const app = (0, express_1.default)();
app.use(express_1.default.json());
async function authenticate(username, password) {
    const { discovery, ClientSecretPost, genericGrantRequest } = await Promise.resolve().then(() => __importStar(require('openid-client')));
    const issuerUrl = process.env.KEYCLOAK_ISSUER_URL || 'https://localhost:7082/realms/ironbucket-lab';
    const clientId = process.env.KEYCLOAK_CLIENT_ID || 'sentinel-gear-app';
    const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET || 'sentinel-gear-app-secret';
    const config = await discovery(new URL(issuerUrl), clientId, clientSecret, ClientSecretPost(clientSecret), { execute: [] });
    return genericGrantRequest(config, 'password', { username, password, scope: 'openid' });
}
async function authHandler(req, res) {
    const { username, password } = req.body ?? {};
    if (!username || !password) {
        return res.status(400).json({ error: 'Username and password required' });
    }
    try {
        const tokenSet = await authenticate(username, password);
        const accessToken = tokenSet.access_token;
        const idToken = tokenSet.id_token;
        return res.status(200).json({
            token: idToken || accessToken,
            accessToken,
            idToken,
        });
    }
    catch (err) {
        return res.status(401).json({
            error: 'Authentication failed',
            details: err?.message || 'Unknown authentication error',
        });
    }
}
app.post('/auth', authHandler);
app.post('/api/auth', authHandler);
const PORT = process.env.PORT || 3000;
if (process.env.NODE_ENV !== 'test') {
    app.listen(PORT, () => {
        console.log(`Server running on port ${PORT}`);
    });
}
exports.default = app;
