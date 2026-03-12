export interface UserClaims {
  roles: string[];
}

export function useUserClaims(): UserClaims {
  return { roles: ['admin'] };
}
