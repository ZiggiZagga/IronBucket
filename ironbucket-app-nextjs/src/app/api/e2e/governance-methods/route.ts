import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

type GovernanceRequest = {
  actor?: string;
};

type GovernanceChecks = {
  getPolicy: boolean;
  searchPolicies: boolean;
  createTenant: boolean;
  addTenant: boolean;
  listTenants: boolean;
  getTenant: boolean;
  tenant: boolean;
  getTenantById: boolean;
  updateTenant: boolean;
  createIdentity: boolean;
  addUser: boolean;
  listIdentities: boolean;
  identity: boolean;
  getIdentityById: boolean;
  getIdentity: boolean;
  updateIdentity: boolean;
  updateUser: boolean;
  removeUser: boolean;
  getUserPermissions: boolean;
  createPolicy: boolean;
  addPolicy: boolean;
  listPolicies: boolean;
  getPolicyById: boolean;
  updatePolicy: boolean;
  evaluatePolicy: boolean;
  validatePolicy: boolean;
  deletePolicy: boolean;
  getPolicyStatistics: boolean;
  policyStats: boolean;
  getUserActivitySummary: boolean;
  userActivity: boolean;
  getResourceAccessPatterns: boolean;
  resourceAccess: boolean;
  getAuditTrail: boolean;
  getAuditLogs: boolean;
  auditLogs: boolean;
  filterAuditLogs: boolean;
  getAuditLogById: boolean;
  auditLogSubscription: boolean;
  onAuditLog: boolean;
  deleteIdentity: boolean;
  deleteTenant: boolean;
};

export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/governance-methods';
  const inboundTraceparent = req.headers.get('traceparent') ?? undefined;
  const correlationId = resolveCorrelationId(req.headers);
  const requestBody = (await req.json()) as GovernanceRequest;
  const actor = resolveActor(requestBody.actor);

  if (!actor) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }

  const traceId = randomBytes(16).toString('hex');
  const parentSpanId = randomBytes(8).toString('hex');
  const traceparent = `00-${traceId}-${parentSpanId}-01`;
  const gatewayOptions = { traceparent, actor, correlationId };

  const suffix = `${Date.now()}-${randomBytes(3).toString('hex')}`;
  const tenantName = `tenant-${actor}-${suffix}`;
  const tenantStatusInitial = 'ACTIVE';
  const tenantStatusUpdated = 'MAINTENANCE';
  const username = `${actor}-identity-${suffix}`;
  const email = `${actor}+${suffix}@ironbucket.dev`;
  const updatedEmail = `${actor}+${suffix}-updated@ironbucket.dev`;
  const principal = `${actor}@ironbucket.dev`;
  const resource = `arn:aws:s3:::default-${actor}-files/*`;
  const action = 's3:GetObject';
  const effect = 'ALLOW';

  try {
    const token = await fetchActorAccessToken(actor);
    const operationLatenciesMs: Record<string, number> = {};

    const timedOperation = async <T>(name: string, fn: () => Promise<T>): Promise<T> => {
      const operationStarted = performance.now();
      try {
        return await fn();
      } finally {
        operationLatenciesMs[name] = Number((performance.now() - operationStarted).toFixed(2));
      }
    };

    const createTenantResponse = await timedOperation('createTenant', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation CreateTenant($input: TenantInput!) {
              createTenant(input: $input) {
                id
                name
                status
              }
            }
          `,
          variables: {
            input: {
              name: tenantName,
              status: tenantStatusInitial
            }
          }
        },
        gatewayOptions
      )
    );

    const tenantId = createTenantResponse?.data?.createTenant?.id as string | undefined;
    const createTenantWorked =
      Boolean(tenantId)
      && createTenantResponse?.data?.createTenant?.name === tenantName
      && createTenantResponse?.data?.createTenant?.status === tenantStatusInitial;

    const listTenantsResponse = await timedOperation('listTenants', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query ListTenants {
              listTenants {
                id
                name
                status
              }
            }
          `
        },
        gatewayOptions
      )
    );

    const tenants = listTenantsResponse?.data?.listTenants ?? [];
    const listTenantsWorked = Array.isArray(tenants) && Boolean(tenantId)
      && tenants.some((item: { id?: string }) => item?.id === tenantId);

    const getTenantResponse = await timedOperation('getTenantById', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetTenantById($id: ID!) {
              getTenantById(id: $id) {
                id
                name
                status
              }
            }
          `,
          variables: {
            id: tenantId
          }
        },
        gatewayOptions
      )
    );

    const getTenantWorked = Boolean(tenantId)
      && getTenantResponse?.data?.getTenantById?.id === tenantId;

    const updateTenantResponse = await timedOperation('updateTenant', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation UpdateTenant($id: ID!, $input: TenantInput!) {
              updateTenant(id: $id, input: $input) {
                id
                status
              }
            }
          `,
          variables: {
            id: tenantId,
            input: {
              name: tenantName,
              status: tenantStatusUpdated
            }
          }
        },
        gatewayOptions
      )
    );

    const updateTenantWorked = Boolean(tenantId)
      && updateTenantResponse?.data?.updateTenant?.id === tenantId
      && updateTenantResponse?.data?.updateTenant?.status === tenantStatusUpdated;

    const addTenantResponse = await timedOperation('addTenant', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation AddTenant($input: TenantInput!) {
              addTenant(input: $input) {
                id
                name
              }
            }
          `,
          variables: {
            input: {
              name: `${tenantName}-alias`,
              status: tenantStatusInitial
            }
          }
        },
        gatewayOptions
      )
    );
    const addTenantWorked = Boolean(addTenantResponse?.data?.addTenant?.id);

    const getTenantAliasResponse = await timedOperation('getTenant', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetTenant($id: ID!) {
              getTenant(id: $id) {
                id
              }
            }
          `,
          variables: {
            id: tenantId
          }
        },
        gatewayOptions
      )
    );
    const getTenantAliasWorked = Boolean(tenantId) && getTenantAliasResponse?.data?.getTenant?.id === tenantId;

    const tenantAliasResponse = await timedOperation('tenant', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query TenantAlias($id: ID!) {
              tenant(id: $id) {
                id
              }
            }
          `,
          variables: {
            id: tenantId
          }
        },
        gatewayOptions
      )
    );
    const tenantAliasWorked = Boolean(tenantId) && tenantAliasResponse?.data?.tenant?.id === tenantId;

    const createIdentityResponse = await timedOperation('createIdentity', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation CreateIdentity($input: IdentityInput!) {
              createIdentity(input: $input) {
                id
                sub
                tenantId
                username
                email
              }
            }
          `,
          variables: {
            input: {
              tenantId,
              username,
              email
            }
          }
        },
        gatewayOptions
      )
    );

    const identityId = createIdentityResponse?.data?.createIdentity?.id as string | undefined;
    const identitySub = createIdentityResponse?.data?.createIdentity?.sub as string | undefined;
    const createIdentityWorked = Boolean(identityId)
      && createIdentityResponse?.data?.createIdentity?.tenantId === tenantId;

    const listIdentitiesResponse = await timedOperation('listIdentities', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query ListIdentities($tenantId: String!) {
              listIdentities(tenantId: $tenantId) {
                id
                tenantId
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );

    const identities = listIdentitiesResponse?.data?.listIdentities ?? [];
    const listIdentitiesWorked = Array.isArray(identities) && Boolean(identityId)
      && identities.some((item: { id?: string }) => item?.id === identityId);

    const getIdentityByIdResponse = await timedOperation('getIdentityById', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetIdentityById($id: ID!) {
              getIdentityById(id: $id) {
                id
                tenantId
              }
            }
          `,
          variables: {
            id: identityId
          }
        },
        gatewayOptions
      )
    );

    const getIdentityByIdWorked = Boolean(identityId)
      && getIdentityByIdResponse?.data?.getIdentityById?.id === identityId;

    const getIdentityResponse = await timedOperation('getIdentity', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetIdentity($sub: String!) {
              getIdentity(sub: $sub) {
                id
                sub
              }
            }
          `,
          variables: {
            sub: identitySub
          }
        },
        gatewayOptions
      )
    );

    const getIdentityWorked = Boolean(identitySub)
      && Boolean(getIdentityResponse?.data?.getIdentity?.id);

    const updateIdentityResponse = await timedOperation('updateIdentity', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation UpdateIdentity($id: ID!, $input: IdentityInput!) {
              updateIdentity(id: $id, input: $input) {
                id
                email
              }
            }
          `,
          variables: {
            id: identityId,
            input: {
              tenantId,
              username,
              email: updatedEmail
            }
          }
        },
        gatewayOptions
      )
    );

    const updateIdentityWorked = Boolean(identityId)
      && updateIdentityResponse?.data?.updateIdentity?.id === identityId
      && updateIdentityResponse?.data?.updateIdentity?.email === updatedEmail;

    const addUserResponse = await timedOperation('addUser', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation AddUser($input: IdentityInput!) {
              addUser(input: $input) {
                id
                tenantId
              }
            }
          `,
          variables: {
            input: {
              tenantId,
              username: `${username}-alias`,
              email: `${actor}+${suffix}-alias@ironbucket.dev`
            }
          }
        },
        gatewayOptions
      )
    );
    const aliasIdentityId = addUserResponse?.data?.addUser?.id as string | undefined;
    const addUserWorked = Boolean(aliasIdentityId) && addUserResponse?.data?.addUser?.tenantId === tenantId;

    const identityAliasResponse = await timedOperation('identity', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query IdentityAlias($id: ID!) {
              identity(id: $id) {
                id
              }
            }
          `,
          variables: {
            id: identityId
          }
        },
        gatewayOptions
      )
    );
    const identityAliasWorked = Boolean(identityId) && identityAliasResponse?.data?.identity?.id === identityId;

    const updateUserResponse = await timedOperation('updateUser', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation UpdateUser($id: ID!, $input: IdentityInput!) {
              updateUser(id: $id, input: $input) {
                id
                email
              }
            }
          `,
          variables: {
            id: aliasIdentityId,
            input: {
              tenantId,
              username: `${username}-alias`,
              email: `${actor}+${suffix}-alias-updated@ironbucket.dev`
            }
          }
        },
        gatewayOptions
      )
    );
    const updateUserWorked = Boolean(aliasIdentityId)
      && updateUserResponse?.data?.updateUser?.id === aliasIdentityId;

    const getUserPermissionsResponse = await timedOperation('getUserPermissions', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetUserPermissions($identityId: ID!) {
              getUserPermissions(identityId: $identityId)
            }
          `,
          variables: {
            identityId
          }
        },
        gatewayOptions
      )
    );

    const getUserPermissionsWorked = Array.isArray(getUserPermissionsResponse?.data?.getUserPermissions);

    const createPolicyResponse = await timedOperation('createPolicy', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation CreatePolicy($input: PolicyInput!) {
              createPolicy(input: $input) {
                id
                tenantId
                principal
                resource
                action
                effect
              }
            }
          `,
          variables: {
            input: {
              tenantId,
              principal,
              resource,
              action,
              effect
            }
          }
        },
        gatewayOptions
      )
    );

    const policyId = createPolicyResponse?.data?.createPolicy?.id as string | undefined;
    const createPolicyWorked = Boolean(policyId)
      && createPolicyResponse?.data?.createPolicy?.tenantId === tenantId;

    const getPolicyResponse = await timedOperation('getPolicy', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetPolicy($id: ID!) {
              getPolicy(id: $id) {
                id
              }
            }
          `,
          variables: {
            id: policyId
          }
        },
        gatewayOptions
      )
    );
    const getPolicyWorked = Boolean(policyId) && getPolicyResponse?.data?.getPolicy?.id === policyId;

    const addPolicyResponse = await timedOperation('addPolicy', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation AddPolicy($input: PolicyInput!) {
              addPolicy(input: $input) {
                id
                tenantId
              }
            }
          `,
          variables: {
            input: {
              tenantId,
              principal,
              resource,
              action,
              effect
            }
          }
        },
        gatewayOptions
      )
    );
    const aliasPolicyId = addPolicyResponse?.data?.addPolicy?.id as string | undefined;
    const addPolicyWorked = Boolean(aliasPolicyId) && addPolicyResponse?.data?.addPolicy?.tenantId === tenantId;

    const listPoliciesResponse = await timedOperation('listPolicies', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query ListPolicies {
              listPolicies {
                id
              }
            }
          `
        },
        gatewayOptions
      )
    );

    const policies = listPoliciesResponse?.data?.listPolicies ?? [];
    const listPoliciesWorked = Array.isArray(policies) && Boolean(policyId)
      && policies.some((item: { id?: string }) => item?.id === policyId);

    const getPolicyByIdResponse = await timedOperation('getPolicyById', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetPolicyById($id: ID!) {
              getPolicyById(id: $id) {
                id
                effect
              }
            }
          `,
          variables: {
            id: policyId
          }
        },
        gatewayOptions
      )
    );

    const getPolicyByIdWorked = Boolean(policyId)
      && getPolicyByIdResponse?.data?.getPolicyById?.id === policyId;

    const searchPoliciesResponse = await timedOperation('searchPolicies', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query SearchPolicies($query: String) {
              searchPolicies(query: $query) {
                id
              }
            }
          `,
          variables: {
            query: principal
          }
        },
        gatewayOptions
      )
    );
    const searchPolicies = searchPoliciesResponse?.data?.searchPolicies ?? [];
    const searchPoliciesWorked = Array.isArray(searchPolicies)
      && searchPolicies.some((item: { id?: string }) => item?.id === policyId);

    const updatePolicyResponse = await timedOperation('updatePolicy', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation UpdatePolicy($id: ID!, $input: PolicyInput!) {
              updatePolicy(id: $id, input: $input) {
                id
                effect
              }
            }
          `,
          variables: {
            id: aliasPolicyId,
            input: {
              tenantId,
              principal,
              resource,
              action,
              effect: 'DENY'
            }
          }
        },
        gatewayOptions
      )
    );
    const updatePolicyWorked = Boolean(aliasPolicyId)
      && updatePolicyResponse?.data?.updatePolicy?.id === aliasPolicyId;

    const evaluatePolicyResponse = await timedOperation('evaluatePolicy', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query EvaluatePolicy($input: PolicyEvaluationInput!) {
              evaluatePolicy(input: $input) {
                allow
                reason
              }
            }
          `,
          variables: {
            input: {
              tenantId,
              principal,
              resource,
              action
            }
          }
        },
        gatewayOptions
      )
    );

    const evaluatePolicyWorked = typeof evaluatePolicyResponse?.data?.evaluatePolicy?.allow === 'boolean';

    const validatePolicyResponse = await timedOperation('validatePolicy', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation ValidatePolicy($input: PolicyInput!) {
              validatePolicy(input: $input) {
                valid
                errors
              }
            }
          `,
          variables: {
            input: {
              tenantId,
              principal,
              resource,
              action,
              effect
            }
          }
        },
        gatewayOptions
      )
    );

    const validatePolicyWorked = typeof validatePolicyResponse?.data?.validatePolicy?.valid === 'boolean';

    const deletePolicyResponse = await timedOperation('deletePolicy', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation DeletePolicy($id: ID!) {
              deletePolicy(id: $id)
            }
          `,
          variables: {
            id: policyId
          }
        },
        gatewayOptions
      )
    );

    const deletePolicyWorked = deletePolicyResponse?.data?.deletePolicy === true;

    const getAuditTrailResponse = await timedOperation('getAuditTrail', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetAuditTrail($tenantId: String!) {
              getAuditTrail(tenantId: $tenantId) {
                id
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );

    const getAuditTrailWorked = Array.isArray(getAuditTrailResponse?.data?.getAuditTrail);

    const getAuditLogsResponse = await timedOperation('getAuditLogs', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetAuditLogs($tenantId: String!) {
              getAuditLogs(tenantId: $tenantId) {
                id
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );

    const getAuditLogsWorked = Array.isArray(getAuditLogsResponse?.data?.getAuditLogs);

    const auditLogsResponse = await timedOperation('auditLogs', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query AuditLogs($tenantId: String!) {
              auditLogs(tenantId: $tenantId) {
                id
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );

    const auditLogsWorked = Array.isArray(auditLogsResponse?.data?.auditLogs);

    const filterAuditLogsResponse = await timedOperation('filterAuditLogs', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query FilterAuditLogs($filter: AuditLogFilterInput!) {
              filterAuditLogs(filter: $filter) {
                id
                actor
              }
            }
          `,
          variables: {
            filter: {
              tenantId,
              actor
            }
          }
        },
        gatewayOptions
      )
    );

    const filterAuditLogs = filterAuditLogsResponse?.data?.filterAuditLogs ?? [];
    const filterAuditLogsWorked = Array.isArray(filterAuditLogs);

    const candidateAuditId = filterAuditLogs[0]?.id ?? getAuditTrailResponse?.data?.getAuditTrail?.[0]?.id ?? `audit-${suffix}`;
    const getAuditLogByIdResponse = await timedOperation('getAuditLogById', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetAuditLogById($id: ID!) {
              getAuditLogById(id: $id) {
                id
              }
            }
          `,
          variables: {
            id: candidateAuditId
          }
        },
        gatewayOptions
      )
    );

    const getAuditLogByIdWorked = typeof getAuditLogByIdResponse?.data?.getAuditLogById?.id === 'string';

    const getPolicyStatisticsResponse = await timedOperation('getPolicyStatistics', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetPolicyStatistics($tenantId: String!) {
              getPolicyStatistics(tenantId: $tenantId) {
                tenantId
                totalPolicies
                evaluationCount
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );
    const getPolicyStatisticsWorked = getPolicyStatisticsResponse?.data?.getPolicyStatistics?.tenantId === tenantId;

    const policyStatsResponse = await timedOperation('policyStats', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query PolicyStats($tenantId: String!) {
              policyStats(tenantId: $tenantId) {
                tenantId
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );
    const policyStatsWorked = policyStatsResponse?.data?.policyStats?.tenantId === tenantId;

    const getUserActivitySummaryResponse = await timedOperation('getUserActivitySummary', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetUserActivitySummary($tenantId: String!) {
              getUserActivitySummary(tenantId: $tenantId) {
                identityId
                operations
                lastSeen
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );
    const getUserActivitySummaryWorked = Array.isArray(getUserActivitySummaryResponse?.data?.getUserActivitySummary);

    const userActivityResponse = await timedOperation('userActivity', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query UserActivity($tenantId: String!) {
              userActivity(tenantId: $tenantId) {
                identityId
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );
    const userActivityWorked = Array.isArray(userActivityResponse?.data?.userActivity);

    const getResourceAccessPatternsResponse = await timedOperation('getResourceAccessPatterns', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetResourceAccessPatterns($tenantId: String!) {
              getResourceAccessPatterns(tenantId: $tenantId) {
                resource
                accesses
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );
    const getResourceAccessPatternsWorked = Array.isArray(getResourceAccessPatternsResponse?.data?.getResourceAccessPatterns);

    const resourceAccessResponse = await timedOperation('resourceAccess', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query ResourceAccess($tenantId: String!) {
              resourceAccess(tenantId: $tenantId) {
                resource
              }
            }
          `,
          variables: {
            tenantId
          }
        },
        gatewayOptions
      )
    );
    const resourceAccessWorked = Array.isArray(resourceAccessResponse?.data?.resourceAccess);

    const subscriptionIntrospectionResponse = await timedOperation('subscriptionContract', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query IntrospectSubscriptionType {
              __schema {
                subscriptionType {
                  fields {
                    name
                  }
                }
              }
            }
          `
        },
        gatewayOptions
      )
    );
    const subscriptionFields = subscriptionIntrospectionResponse?.data?.__schema?.subscriptionType?.fields ?? [];
    const auditLogSubscriptionWorked = Array.isArray(subscriptionFields)
      && subscriptionFields.some((field: { name?: string }) => field?.name === 'auditLogSubscription');
    const onAuditLogWorked = Array.isArray(subscriptionFields)
      && subscriptionFields.some((field: { name?: string }) => field?.name === 'onAuditLog');

    const deleteIdentityResponse = await timedOperation('deleteIdentity', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation DeleteIdentity($id: ID!) {
              deleteIdentity(id: $id)
            }
          `,
          variables: {
            id: identityId
          }
        },
        gatewayOptions
      )
    );

    const deleteIdentityWorked = deleteIdentityResponse?.data?.deleteIdentity === true;

    const removeUserResponse = await timedOperation('removeUser', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation RemoveUser($id: ID!) {
              removeUser(id: $id)
            }
          `,
          variables: {
            id: aliasIdentityId
          }
        },
        gatewayOptions
      )
    );
    const removeUserWorked = removeUserResponse?.data?.removeUser === true;

    const deleteTenantResponse = await timedOperation('deleteTenant', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation DeleteTenant($id: ID!) {
              deleteTenant(id: $id)
            }
          `,
          variables: {
            id: tenantId
          }
        },
        gatewayOptions
      )
    );

    const deleteTenantWorked = deleteTenantResponse?.data?.deleteTenant === true;

    const checks: GovernanceChecks = {
      getPolicy: getPolicyWorked,
      searchPolicies: searchPoliciesWorked,
      createTenant: createTenantWorked,
      addTenant: addTenantWorked,
      listTenants: listTenantsWorked,
      getTenant: getTenantAliasWorked,
      tenant: tenantAliasWorked,
      getTenantById: getTenantWorked,
      updateTenant: updateTenantWorked,
      createIdentity: createIdentityWorked,
      addUser: addUserWorked,
      listIdentities: listIdentitiesWorked,
      identity: identityAliasWorked,
      getIdentityById: getIdentityByIdWorked,
      getIdentity: getIdentityWorked,
      updateIdentity: updateIdentityWorked,
      updateUser: updateUserWorked,
      removeUser: removeUserWorked,
      getUserPermissions: getUserPermissionsWorked,
      createPolicy: createPolicyWorked,
      addPolicy: addPolicyWorked,
      listPolicies: listPoliciesWorked,
      getPolicyById: getPolicyByIdWorked,
      updatePolicy: updatePolicyWorked,
      evaluatePolicy: evaluatePolicyWorked,
      validatePolicy: validatePolicyWorked,
      deletePolicy: deletePolicyWorked,
      getPolicyStatistics: getPolicyStatisticsWorked,
      policyStats: policyStatsWorked,
      getUserActivitySummary: getUserActivitySummaryWorked,
      userActivity: userActivityWorked,
      getResourceAccessPatterns: getResourceAccessPatternsWorked,
      resourceAccess: resourceAccessWorked,
      getAuditTrail: getAuditTrailWorked,
      getAuditLogs: getAuditLogsWorked,
      auditLogs: auditLogsWorked,
      filterAuditLogs: filterAuditLogsWorked,
      getAuditLogById: getAuditLogByIdWorked,
      auditLogSubscription: auditLogSubscriptionWorked,
      onAuditLog: onAuditLogWorked,
      deleteIdentity: deleteIdentityWorked,
      deleteTenant: deleteTenantWorked
    };

    const allMethodsVerified = Object.values(checks).every(Boolean);

    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('Governance methods E2E flow completed.', {
      route,
      status: 200,
      actor,
      traceparent,
      inboundTraceparent,
      correlationId,
      durationMs,
      allMethodsVerified
    });

    return withCorrelationHeaders(
      NextResponse.json({
        actor,
        tenantId,
        identityId,
        policyId,
        traceId,
        traceparent,
        checks,
        operationLatenciesMs,
        allMethodsVerified,
        timestamp: new Date().toISOString()
      }),
      correlationId
    );
  } catch (error) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Governance methods E2E flow failed.', {
      route,
      status: 500,
      actor,
      traceparent,
      inboundTraceparent,
      correlationId,
      durationMs,
      error: error instanceof Error ? error.message : String(error)
    });
    return withCorrelationHeaders(
      NextResponse.json(
        {
          error: 'Governance methods e2e flow failed on gateway GraphQL path',
          details: error instanceof Error ? error.message : String(error),
          traceId,
          traceparent
        },
        { status: 500 }
      ),
      correlationId
    );
  }
}
