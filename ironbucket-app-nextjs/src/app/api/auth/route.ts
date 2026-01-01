import { NextRequest, NextResponse } from 'next/server';

export async function POST(req: NextRequest) {
  const { username, password } = await req.json();
  if (!username || !password) {
    return NextResponse.json({ error: 'Username and password required' }, { status: 400 });
  }
  try {
    const { discovery, ClientSecretPost, genericGrantRequest } = await import('openid-client');
    const issuerUrl = process.env.KEYCLOAK_ISSUER_URL || 'https://localhost:7082/realms/ironbucket-lab';
    const clientId = process.env.KEYCLOAK_CLIENT_ID || 'sentinel-gear-app';
    const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET || 'sentinel-gear-app-secret';
    const config = await discovery(new URL(issuerUrl), clientId, clientSecret, ClientSecretPost(clientSecret), { execute: [] });
    const tokenSet = await genericGrantRequest(
      config,
      'password',
      { username, password, scope: 'openid' }
    );
    return NextResponse.json({ token: tokenSet.id_token || tokenSet.access_token });
  } catch (err: any) {
    return NextResponse.json({ error: 'Authentication failed', details: err.message }, { status: 401 });
  }
}
