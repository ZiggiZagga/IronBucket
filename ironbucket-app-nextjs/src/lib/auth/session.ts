export type AppSessionUser = {
  username: string;
  displayName: string;
  email?: string;
  roles: string[];
  tenantId?: string;
  realm?: string;
};

export type AppSession = {
  user: AppSessionUser;
  accessToken: string;
  idToken?: string;
  expiresAt?: string;
  issuedAt: string;
};

type JwtPayload = {
  preferred_username?: string;
  email?: string;
  name?: string;
  sub?: string;
  iss?: string;
  exp?: number;
  iat?: number;
  tenant_id?: string;
  realm_access?: {
    roles?: string[];
  };
  resource_access?: Record<string, { roles?: string[] }>;
};

export const APP_SESSION_STORAGE_KEY = 'ironbucket.console.session';
export const APP_ACCESS_TOKEN_STORAGE_KEY = 'ironbucket.console.accessToken';

function decodeBase64Url(value: string) {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized.padEnd(normalized.length + ((4 - (normalized.length % 4)) % 4), '=');

  if (typeof window !== 'undefined' && typeof window.atob === 'function') {
    return window.atob(padded);
  }

  return Buffer.from(padded, 'base64').toString('utf-8');
}

export function decodeJwtPayload(token: string): JwtPayload | null {
  const parts = token.split('.');
  if (parts.length < 2) {
    return null;
  }

  try {
    return JSON.parse(decodeBase64Url(parts[1])) as JwtPayload;
  } catch {
    return null;
  }
}

export function createSessionFromTokenSet(tokenSet: {
  accessToken: string;
  idToken?: string;
}): AppSession {
  const payload = decodeJwtPayload(tokenSet.accessToken) ?? decodeJwtPayload(tokenSet.idToken ?? '') ?? {};
  const resourceRoles = Object.values(payload.resource_access ?? {}).flatMap((entry) => entry.roles ?? []);
  const roles = Array.from(new Set([...(payload.realm_access?.roles ?? []), ...resourceRoles]));
  const username = payload.preferred_username ?? payload.email ?? payload.sub ?? 'operator';

  return {
    user: {
      username,
      displayName: payload.name ?? username,
      email: payload.email,
      roles: roles.length > 0 ? roles : ['admin'],
      tenantId: payload.tenant_id,
      realm: payload.iss
    },
    accessToken: tokenSet.accessToken,
    idToken: tokenSet.idToken,
    expiresAt: payload.exp ? new Date(payload.exp * 1000).toISOString() : undefined,
    issuedAt: payload.iat ? new Date(payload.iat * 1000).toISOString() : new Date().toISOString()
  };
}

export function getStoredSession() {
  if (typeof window === 'undefined') {
    return null;
  }

  const raw = window.localStorage.getItem(APP_SESSION_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AppSession;
  } catch {
    return null;
  }
}

export function persistSession(session: AppSession) {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.setItem(APP_SESSION_STORAGE_KEY, JSON.stringify(session));
  window.localStorage.setItem(APP_ACCESS_TOKEN_STORAGE_KEY, session.accessToken);
}

export function clearStoredSession() {
  if (typeof window === 'undefined') {
    return;
  }

  window.localStorage.removeItem(APP_SESSION_STORAGE_KEY);
  window.localStorage.removeItem(APP_ACCESS_TOKEN_STORAGE_KEY);
}