import express, { NextFunction, Request, Response } from 'express';
import {
  logUnhandledError,
  metricsContentType,
  metricsSnapshot,
  requestObservabilityMiddleware,
  startObservability
} from './observability';

const app = express();
app.use(express.json());
app.use(requestObservabilityMiddleware);

void startObservability();

async function authenticate(username: string, password: string) {
  const { discovery, ClientSecretPost, genericGrantRequest } = await import('openid-client');
  const issuerUrl = process.env.KEYCLOAK_ISSUER_URL || 'https://localhost:7082/realms/ironbucket-lab';
  const clientId = process.env.KEYCLOAK_CLIENT_ID || 'sentinel-gear-app';
  const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET || 'sentinel-gear-app-secret';

  const config = await discovery(new URL(issuerUrl), clientId, clientSecret, ClientSecretPost(clientSecret), { execute: [] });
  return genericGrantRequest(config, 'password', { username, password, scope: 'openid' });
}

async function authHandler(req: Request, res: Response) {
  const correlationId = String(res.getHeader('x-correlation-id') ?? res.getHeader('x-request-id') ?? '');
  const { username, password } = req.body ?? {};
  if (!username || !password) {
    return res.status(400).json({
      error: 'Username and password required',
      correlationId
    });
  }

  try {
    const tokenSet: any = await authenticate(username, password);
    const accessToken = tokenSet.access_token;
    const idToken = tokenSet.id_token;

    return res.status(200).json({
      token: idToken || accessToken,
      accessToken,
      idToken,
      correlationId,
    });
  } catch (err: any) {
    return res.status(401).json({
      error: 'Authentication failed',
      details: err?.message || 'Unknown authentication error',
      correlationId,
    });
  }
}

app.post('/auth', authHandler);
app.post('/api/auth', authHandler);

app.get('/metrics', async (_req: Request, res: Response) => {
  const metrics = await metricsSnapshot();
  res.setHeader('Content-Type', metricsContentType());
  res.status(200).send(metrics);
});

app.use((err: unknown, req: Request, res: Response, _next: NextFunction) => {
  logUnhandledError(err, req);
  const correlationId = String(res.getHeader('x-correlation-id') ?? res.getHeader('x-request-id') ?? '');
  res.status(500).json({
    error: 'Internal server error',
    details: err instanceof Error ? err.message : 'Unexpected server error',
    correlationId
  });
});

const PORT = process.env.PORT || 3000;
if (process.env.NODE_ENV !== 'test') {
  app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
  });
}

export default app;
