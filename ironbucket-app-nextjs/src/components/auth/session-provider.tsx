'use client';

import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import {
  AppSession,
  clearStoredSession,
  createSessionFromTokenSet,
  getStoredSession,
  persistSession
} from '@/lib/auth/session';

type SignInPayload = {
  username: string;
  password: string;
};

type AppSessionContextValue = {
  session: AppSession | null;
  isReady: boolean;
  isAuthenticating: boolean;
  signIn: (payload: SignInPayload) => Promise<AppSession>;
  signOut: () => void;
};

const AppSessionContext = createContext<AppSessionContextValue | null>(null);

export function AppSessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<AppSession | null>(null);
  const [isReady, setIsReady] = useState(false);
  const [isAuthenticating, setIsAuthenticating] = useState(false);

  useEffect(() => {
    setSession(getStoredSession());
    setIsReady(true);
  }, []);

  const value = useMemo<AppSessionContextValue>(
    () => ({
      session,
      isReady,
      isAuthenticating,
      signIn: async ({ username, password }) => {
        setIsAuthenticating(true);
        try {
          const response = await fetch('/api/auth', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
          });

          const payload = (await response.json()) as {
            accessToken?: string;
            idToken?: string;
            error?: string;
            details?: string;
          };

          if (!response.ok || !payload.accessToken) {
            throw new Error(payload.details ?? payload.error ?? 'Authentication failed');
          }

          const nextSession = createSessionFromTokenSet({
            accessToken: payload.accessToken,
            idToken: payload.idToken
          });

          persistSession(nextSession);
          setSession(nextSession);
          return nextSession;
        } finally {
          setIsAuthenticating(false);
        }
      },
      signOut: () => {
        clearStoredSession();
        setSession(null);
      }
    }),
    [isAuthenticating, isReady, session]
  );

  return <AppSessionContext.Provider value={value}>{children}</AppSessionContext.Provider>;
}

export function useAppSession() {
  const context = useContext(AppSessionContext);

  if (!context) {
    throw new Error('useAppSession must be used within AppSessionProvider');
  }

  return context;
}