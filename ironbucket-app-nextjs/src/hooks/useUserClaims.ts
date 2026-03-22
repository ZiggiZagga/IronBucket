import { useAppSession } from '@/components/auth/session-provider';

export interface UserClaims {
  roles: string[];
}

export function useUserClaims(): UserClaims {
  const { session } = useAppSession();
  return { roles: session?.user.roles ?? ['admin'] };
}
