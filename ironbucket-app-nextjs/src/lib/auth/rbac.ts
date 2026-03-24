const ADMIN_ROLES = ['admin', 'adminrole'] as const;

export function hasAnyRole(roles: string[] | undefined, expected: readonly string[]) {
  if (!roles || roles.length === 0) {
    return false;
  }

  const roleSet = new Set(roles.map((role) => role.toLowerCase()));
  return expected.some((role) => roleSet.has(role.toLowerCase()));
}

export function canManageAdminResources(roles: string[] | undefined) {
  return hasAnyRole(roles, ADMIN_ROLES);
}

export function canReadControlPlane(roles: string[] | undefined) {
  return Boolean(roles && roles.length > 0);
}
