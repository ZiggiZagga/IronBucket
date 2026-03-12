import express, { Request, Response } from 'express';

const app = express();
app.use(express.json());

async function authenticate(username: string, password: string) {
	const { discovery, ClientSecretPost, genericGrantRequest } = await import('openid-client');
	const issuerUrl = process.env.KEYCLOAK_ISSUER_URL || 'https://localhost:7082/realms/ironbucket-lab';
	const clientId = process.env.KEYCLOAK_CLIENT_ID || 'sentinel-gear-app';
	const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET || 'sentinel-gear-app-secret';

	const config = await discovery(new URL(issuerUrl), clientId, clientSecret, ClientSecretPost(clientSecret), { execute: [] });
	return genericGrantRequest(config, 'password', { username, password, scope: 'openid' });
}

async function authHandler(req: Request, res: Response) {
	const { username, password } = req.body ?? {};
	if (!username || !password) {
		return res.status(400).json({ error: 'Username and password required' });
	}

	try {
		const tokenSet: any = await authenticate(username, password);
		const accessToken = tokenSet.access_token;
		const idToken = tokenSet.id_token;

		return res.status(200).json({
			token: idToken || accessToken,
			accessToken,
			idToken,
		});
	} catch (err: any) {
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

export default app;

