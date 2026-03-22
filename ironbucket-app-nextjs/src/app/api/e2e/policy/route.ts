import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

/**
 * E2E flow: full Policy lifecycle via GraphQL.
 * Tests: createPolicy / addPolicy, listPolicies, searchPolicies,
 *        getPolicy / getPolicyById, evaluatePolicy, validatePolicy,
 *        updatePolicy, deletePolicy
 */
export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/policy';
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

    // createPolicy
    const policyInput = { tenantId, principal: actor, resource: `${tenantId}-files`, action: 's3:PutObject', effect: 'ALLOW' };
    const createRes = await gql(`
      mutation CreatePolicy($input: PolicyInput!) {
        createPolicy(input: $input) {
          id tenantId principal resource action effect version enabled
        }
      }`, { input: policyInput });
    const created = createRes?.data?.createPolicy;
    const policyId: string = created?.id ?? '';

    // addPolicy (alias)
    const addRes = await gql(`
      mutation AddPolicy($input: PolicyInput!) {
        addPolicy(input: $input) {
          id tenantId principal resource action effect version enabled
        }
      }`, { input: { tenantId, principal: `${actor}-readonly`, resource: `${tenantId}-archive`, action: 's3:GetObject', effect: 'ALLOW' } });
    const added = addRes?.data?.addPolicy;
    const addedId: string = added?.id ?? '';

    // listPolicies
    const listRes = await gql(`query { listPolicies { id tenantId principal resource action effect version enabled } }`, {});
    const listed = listRes?.data?.listPolicies ?? [];

    // searchPolicies
    const searchRes = await gql(`
      query SearchPolicies($query: String) {
        searchPolicies(query: $query) { id tenantId principal }
      }`, { query: tenantId });
    const searched = searchRes?.data?.searchPolicies ?? [];

    // getPolicy
    const getRes = policyId ? await gql(`
      query GetPolicy($id: ID!) {
        getPolicy(id: $id) { id tenantId principal resource action effect version enabled }
      }`, { id: policyId }) : null;
    const fetched = getRes?.data?.getPolicy;

    // getPolicyById
    const getPolicyByIdRes = policyId ? await gql(`
      query GetPolicyById($id: ID!) {
        getPolicyById(id: $id) { id tenantId principal resource action effect version enabled }
      }`, { id: policyId }) : null;
    const fetchedById = getPolicyByIdRes?.data?.getPolicyById;

    // validatePolicy
    const validateRes = await gql(`
      mutation ValidatePolicy($input: PolicyInput!) {
        validatePolicy(input: $input) { valid errors }
      }`, { input: policyInput });
    const validated = validateRes?.data?.validatePolicy;

    // evaluatePolicy
    const evalRes = await gql(`
      query EvaluatePolicy($input: PolicyEvaluationInput!) {
        evaluatePolicy(input: $input) { allow reason }
      }`, { input: { tenantId, principal: actor, resource: `${tenantId}-files`, action: 's3:PutObject' } });
    const evaluated = evalRes?.data?.evaluatePolicy;

    // updatePolicy
    const updateRes = policyId ? await gql(`
      mutation UpdatePolicy($id: ID!, $input: PolicyInput!) {
        updatePolicy(id: $id, input: $input) { id version enabled }
      }`, { id: policyId, input: { ...policyInput, effect: 'ALLOW' } }) : null;
    const updated = updateRes?.data?.updatePolicy;

    // deletePolicy
    const deleteRes = policyId ? await gql(`
      mutation DeletePolicy($id: ID!) {
        deletePolicy(id: $id)
      }`, { id: policyId }) : null;
    const deleted = deleteRes?.data?.deletePolicy;

    // deletePolicy for addPolicy alias
    if (addedId) {
      await gql(`mutation DeletePolicy($id: ID!) { deletePolicy(id: $id) }`, { id: addedId });
    }

    const checks = {
      createPolicy:   Boolean(created?.id),
      addPolicy:      Boolean(added?.id),
      listPolicies:   Array.isArray(listed),
      searchPolicies: Array.isArray(searched),
      getPolicy:      Boolean(fetched?.id),
      getPolicyById:  Boolean(fetchedById?.id),
      validatePolicy: validated?.valid === true,
      evaluatePolicy: typeof evaluated?.allow === 'boolean',
      updatePolicy:   Boolean(updated?.id),
      deletePolicy:   deleted === true,
    };

    const allVerified = Object.values(checks).every(Boolean);
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('Policy e2e flow completed.', { route, actor, traceId, allVerified, durationMs });

    return withCorrelationHeaders(NextResponse.json({
      actor, tenantId, traceId, traceparent, checks, allVerified,
      timestamp: new Date().toISOString()
    }), correlationId);
  } catch (err) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Policy e2e flow failed.', { route, actor, traceId, durationMs, error: err instanceof Error ? err.message : String(err) });
    return withCorrelationHeaders(NextResponse.json(
      { error: 'Policy e2e flow failed', details: err instanceof Error ? err.message : String(err), traceId, traceparent },
      { status: 500 }
    ), correlationId);
  }
}
