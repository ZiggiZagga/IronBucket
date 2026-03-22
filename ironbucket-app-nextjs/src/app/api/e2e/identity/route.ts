import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

/**
 * E2E flow: full Identity lifecycle via GraphQL.
 * Tests: createIdentity / addUser, getIdentity / getIdentityById / identity,
 *        listIdentities, getUserPermissions, updateIdentity / updateUser,
 *        deleteIdentity / removeUser
 */
export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/identity';
  const correlationId = resolveCorrelationId(req.headers);
  const traceId = randomBytes(16).toString('hex');
  const traceparent = `00-${traceId}-${randomBytes(8).toString('hex')}-01`;
  const gatewayOptions = { traceparent, correlationId };

  const body = (await req.json()) as { actor?: string; tenantId?: string };
  const actor = resolveActor(body.actor);
  if (!actor) {
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${body.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }
  const tenantId = body.tenantId ?? actor;

  try {
    const token = await fetchActorAccessToken(actor);
    const gql = (q: string, vars: Record<string, unknown>) =>
      callGatewayGraphql(token, { query: q, variables: vars }, gatewayOptions);

    const stamp = Date.now();
    const identityInput = { tenantId, username: `e2e-${actor}-${stamp}`, email: `e2e-${actor}-${stamp}@example.com` };

    // createIdentity
    const createRes = await gql(`
      mutation CreateIdentity($input: IdentityInput!) {
        createIdentity(input: $input) { id sub tenantId username email permissions }
      }`, { input: identityInput });
    const created = createRes?.data?.createIdentity;
    const identityId: string = created?.id ?? '';

    // addUser (alias)
    const addUserRes = await gql(`
      mutation AddUser($input: IdentityInput!) {
        addUser(input: $input) { id sub tenantId username email permissions }
      }`, { input: { tenantId, username: `e2e-${actor}-alt-${stamp}`, email: `e2e-${actor}-alt-${stamp}@example.com` } });
    const addedUser = addUserRes?.data?.addUser;
    const addedUserId: string = addedUser?.id ?? '';

    // getIdentityById
    const getByIdRes = identityId ? await gql(`
      query GetIdentityById($id: ID!) {
        getIdentityById(id: $id) { id sub tenantId username email permissions }
      }`, { id: identityId }) : null;
    const fetchedById = getByIdRes?.data?.getIdentityById;

    // identity alias
    const identityAliasRes = identityId ? await gql(`
      query Identity($id: ID!) {
        identity(id: $id) { id sub tenantId username email permissions }
      }`, { id: identityId }) : null;
    const fetchedAlias = identityAliasRes?.data?.identity;

    // getIdentity (by sub)
    const sub: string = created?.sub ?? identityId;
    const getBySubRes = sub ? await gql(`
      query GetIdentity($sub: String!) {
        getIdentity(sub: $sub) { id sub tenantId username email permissions }
      }`, { sub }) : null;
    const fetchedBySub = getBySubRes?.data?.getIdentity;

    // listIdentities
    const listRes = await gql(`
      query ListIdentities($tenantId: String!) {
        listIdentities(tenantId: $tenantId) { id sub tenantId username }
      }`, { tenantId });
    const listed = listRes?.data?.listIdentities ?? [];

    // getUserPermissions
    const permRes = identityId ? await gql(`
      query GetUserPermissions($identityId: ID!) {
        getUserPermissions(identityId: $identityId)
      }`, { identityId }) : null;
    const permissions = permRes?.data?.getUserPermissions;

    // updateIdentity
    const updateRes = identityId ? await gql(`
      mutation UpdateIdentity($id: ID!, $input: IdentityInput!) {
        updateIdentity(id: $id, input: $input) { id username tenantId }
      }`, { id: identityId, input: { tenantId, username: `e2e-${actor}-updated-${stamp}` } }) : null;
    const updated = updateRes?.data?.updateIdentity;

    // updateUser alias
    const updateUserRes = addedUserId ? await gql(`
      mutation UpdateUser($id: ID!, $input: IdentityInput!) {
        updateUser(id: $id, input: $input) { id username tenantId }
      }`, { id: addedUserId, input: { tenantId, username: `e2e-${actor}-alt-updated-${stamp}` } }) : null;
    const updatedUser = updateUserRes?.data?.updateUser;

    // deleteIdentity
    const deleteRes = identityId ? await gql(`
      mutation DeleteIdentity($id: ID!) {
        deleteIdentity(id: $id)
      }`, { id: identityId }) : null;
    const deleted = deleteRes?.data?.deleteIdentity;

    // removeUser alias
    const removeUserRes = addedUserId ? await gql(`
      mutation RemoveUser($id: ID!) {
        removeUser(id: $id)
      }`, { id: addedUserId }) : null;
    const removedUser = removeUserRes?.data?.removeUser;

    const checks = {
      createIdentity:   Boolean(created?.id),
      addUser:          Boolean(addedUser?.id),
      getIdentityById:  Boolean(fetchedById?.id),
      identityAlias:    Boolean(fetchedAlias?.id),
      getIdentity:      Boolean(fetchedBySub?.id),
      listIdentities:   Array.isArray(listed),
      getUserPermissions: Array.isArray(permissions),
      updateIdentity:   Boolean(updated?.id),
      updateUser:       Boolean(updatedUser?.id),
      deleteIdentity:   deleted === true,
      removeUser:       removedUser === true,
    };

    const allVerified = Object.values(checks).every(Boolean);
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('Identity e2e flow completed.', { route, actor, traceId, allVerified, durationMs });

    return withCorrelationHeaders(NextResponse.json({
      actor, tenantId, traceId, traceparent, checks, allVerified,
      timestamp: new Date().toISOString()
    }), correlationId);
  } catch (err) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Identity e2e flow failed.', { route, actor, traceId, durationMs, error: err instanceof Error ? err.message : String(err) });
    return withCorrelationHeaders(NextResponse.json(
      { error: 'Identity e2e flow failed', details: err instanceof Error ? err.message : String(err), traceId, traceparent },
      { status: 500 }
    ), correlationId);
  }
}
