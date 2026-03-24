import { NextRequest, NextResponse } from 'next/server';
import { validatePolicy } from '@/lib/policy/gateway';
import type { PolicyDraft, PolicyValidationResult } from '@/features/policy-management/types';

function semanticValidate(draft: PolicyDraft) {
  const errors: string[] = [];
  const warnings: string[] = [];

  if (draft.effect === 'ALLOW' && draft.action === 's3:*' && draft.resource === '*') {
    warnings.push('Policy is very broad: ALLOW + s3:* + * resource. Beginner tip: scope action to one verb and resource to a tenant prefix like tenant-a-*');
  }

  if (!draft.tenantId.trim()) {
    errors.push('tenantId cannot be empty. Beginner tip: set tenantId to the exact tenant key used in routing, for example tenant-a.');
  }

  if (!draft.principal.trim()) {
    errors.push('principal cannot be empty. Beginner tip: use the user, group, or service identity that appears in your auth tokens.');
  }

  if (!draft.action.trim()) {
    errors.push('action cannot be empty. Beginner tip: use a concrete S3 action such as s3:GetObject or s3:PutObject.');
  }

  if (!draft.resource.trim()) {
    errors.push('resource cannot be empty. Beginner tip: use a bucket/object pattern such as tenant-a-logs/*');
  }

  if (draft.effect === 'DENY' && draft.enabled === false) {
    warnings.push('DENY policy is disabled. Beginner tip: a disabled deny rule has no effect during evaluation.');
  }

  if (!draft.resource.includes('*') && !draft.resource.includes('/')) {
    warnings.push('Resource pattern looks very specific. Beginner tip: add * or a path segment if you expect multiple objects.');
  }

  return { errors, warnings };
}

export async function POST(req: NextRequest) {
  const actor = req.headers.get('x-ironbucket-actor');
  const draft = (await req.json()) as PolicyDraft;

  try {
    const schemaValidation = await validatePolicy(actor, draft);
    const semanticValidation = semanticValidate(draft);

    const result: PolicyValidationResult = {
      valid: schemaValidation.valid && semanticValidation.errors.length === 0,
      schemaErrors: schemaValidation.errors,
      semanticErrors: semanticValidation.errors,
      warnings: semanticValidation.warnings
    };

    return NextResponse.json(result);
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to validate policy', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}
