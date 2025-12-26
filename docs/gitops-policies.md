# GitOps Policies Contract

## 1. Overview

IronBucket's policies are stored in Git and managed via GitOps workflows. This document defines the repository structure, validation rules, and promotion workflows.

**Goal:** Policies are code — version controlled, reviewed, tested, and deployed like any other change.

---

## 2. Repository Structure

### 2.1 Recommended Layout

```
ironbucket-policies/
├── README.md                           # Documentation
├── CONTRIBUTING.md                     # Contribution guidelines
├── CODEOWNERS                          # Policy ownership
│
├── policies/                           # All policy definitions
│   ├── development/
│   │   ├── rbac.yaml                  # Role-based policies
│   │   ├── tenant-isolation.yaml       # Tenant enforcement
│   │   └── README.md
│   │
│   ├── staging/
│   │   ├── rbac.yaml
│   │   ├── rbac-staging.yaml           # Staging-only relaxed rules
│   │   └── README.md
│   │
│   ├── production/
│   │   ├── rbac.yaml                   # Production policies (strict)
│   │   ├── deny-rules.yaml             # Deny-first policies
│   │   ├── emergency-access.yaml       # Break-glass policies
│   │   └── README.md
│   │
│   └── common/
│       ├── service-accounts.yaml       # Service account roles
│       ├── team-roles.yaml             # Team-based roles
│       └── README.md
│
├── schemas/                            # JSON Schema for validation
│   ├── policy-schema.json              # Policy structure validation
│   └── README.md
│
├── tests/                              # Policy tests
│   ├── unit/
│   │   ├── test-rbac.yaml
│   │   ├── test-tenant-isolation.yaml
│   │   └── test-deny-rules.yaml
│   │
│   ├── integration/
│   │   ├── s3-proxy-tests.yaml
│   │   └── policy-engine-tests.yaml
│   │
│   └── fixtures/
│       ├── users.json                  # Test identities
│       ├── s3-requests.json            # Test requests
│       └── expected-decisions.json     # Expected outcomes
│
├── environments/                       # Environment-specific config
│   ├── dev.yaml
│   ├── staging.yaml
│   ├── production.yaml
│   └── dr.yaml
│
├── .github/                            # GitHub automation
│   ├── workflows/
│   │   ├── validate-pr.yml            # PR validation
│   │   ├── test-policies.yml          # Run policy tests
│   │   ├── deploy-staging.yml         # Deploy to staging
│   │   └── deploy-production.yml      # Deploy to prod (manual)
│   │
│   ├── pull-request-template.md        # PR template
│   ├── ISSUE_TEMPLATE/                 # Issue templates
│   │   ├── bug-report.md
│   │   ├── policy-change.md
│   │   └── access-request.md
│   │
│   └── CODEOWNERS                      # Auto-reviewers
│
├── docs/                               # Detailed documentation
│   ├── examples.md                     # Policy examples
│   ├── best-practices.md               # Writing good policies
│   ├── troubleshooting.md              # Common issues
│   └── migration-guide.md              # From old systems
│
├── .gitignore
├── CHANGELOG.md                        # Policy changes log
└── VERSION                             # Policy schema version
```

---

## 2.2 File Naming Convention

```
├── {environment}/
│   ├── {function}-{type}.yaml
│   └── Examples:
│       ├── rbac-dev.yaml               # RBAC for dev environment
│       ├── abac-team-access.yaml       # ABAC by team
│       ├── deny-production.yaml        # Deny rules for prod
│       ├── service-accounts.yaml       # Service account policies
│       └── emergency-access.yaml       # Break-glass
```

---

## 3. Validation Rules

### 3.1 YAML Schema Validation

Every policy file MUST conform to JSON Schema:

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "IronBucket Policy",
  "type": "object",
  "required": ["version", "policies"],
  
  "properties": {
    "version": {
      "type": "string",
      "pattern": "^\\d+\\.\\d+$",
      "examples": ["1.0", "2.1"]
    },
    
    "policies": {
      "type": "array",
      "minItems": 1,
      "items": {
        "type": "object",
        "required": ["name", "id", "effect", "principals", "actions", "resources"],
        
        "properties": {
          "name": {
            "type": "string",
            "pattern": "^[a-z0-9-]{3,50}$",
            "description": "Unique policy name"
          },
          
          "id": {
            "type": "string",
            "pattern": "^policy-[a-z0-9]{3,}$",
            "description": "Stable policy ID"
          },
          
          "description": {
            "type": "string",
            "minLength": 10,
            "description": "Explain why this policy exists"
          },
          
          "effect": {
            "type": "string",
            "enum": ["Allow", "Deny"]
          },
          
          "principals": {
            "type": "object",
            "required": ["type", "values"],
            "properties": {
              "type": {
                "type": "string",
                "enum": ["role", "user", "service-account", "group"]
              },
              "values": {
                "type": "array",
                "items": {"type": "string"},
                "minItems": 1
              }
            }
          },
          
          "actions": {
            "type": "array",
            "items": {
              "type": "string",
              "pattern": "^s3:[A-Z][a-zA-Z]*$|^s3:\\*$"
            },
            "minItems": 1
          },
          
          "resources": {
            "type": "array",
            "items": {
              "type": "string",
              "pattern": "^arn:aws:s3:::[^\\s]+$"
            },
            "minItems": 1
          },
          
          "conditions": {
            "type": "array",
            "items": {
              "type": "object",
              "required": ["type", "key"]
            }
          },
          
          "audit": {
            "type": "boolean",
            "default": true
          }
        }
      }
    }
  }
}
```

### 3.2 Semantic Validation

Beyond syntax, validate:

```
✅ VALID:
- All role references exist in team structure
- All user references are valid (email format)
- No circular dependencies
- No duplicate policy IDs
- Resources follow ARN pattern
- At least one Allow policy path to success

⚠️ WARNINGS:
- Overly broad resources (arn:aws:s3:::*/*)
- Missing audit flag
- No conditions on sensitive actions
- Deny without explanation
- Service account with human roles

❌ ERRORS:
- Undefined role
- Invalid ARN pattern
- Malformed condition
- Conflicting policies in same commit
- Service account accessing user-only resources
```

### 3.3 Conflict Detection

```
Example: Two policies with conflicting effects

Policy A (Allow):
  principals: ["dev"]
  actions: ["s3:DeleteObject"]
  resources: ["arn:aws:s3:::*/*"]

Policy B (Deny):
  principals: ["dev"]
  actions: ["s3:DeleteObject"]
  resources: ["arn:aws:s3:::prod-*/*"]

Validation result:
  ✓ No conflict (Deny is more specific, correctly overrides)
  ✓ Audit both to understand intent

---

Policy A (Allow):
  principals: ["dev"]
  actions: ["s3:GetObject"]
  resources: ["arn:aws:s3:::dev-bucket/*"]

Policy B (Deny):
  principals: ["dev"]
  actions: ["s3:GetObject"]
  resources: ["arn:aws:s3:::dev-bucket/*"]

Validation result:
  ❌ CONFLICT: Same scope, opposing effects
  ❌ MUST: Add condition to Policy A OR restructure
```

---

## 4. Promotion Workflow

### 4.1 Development → Staging → Production

```
┌─────────────────────────────┐
│  1. CREATE FEATURE BRANCH   │
│  git checkout -b            │
│    feat/allow-dev-team      │
└──────────────┬──────────────┘
               │
               ↓
     ┌─────────────────────────┐
     │ 2. EDIT POLICIES        │
     │ vi policies/dev/rbac.yaml
     └──────────────┬──────────┘
                    │
                    ↓
     ┌──────────────────────────┐
     │ 3. LOCAL VALIDATION      │
     │ $ make validate          │
     │   ├─ Schema check        │
     │   ├─ Semantic check      │
     │   ├─ Conflict detection  │
     │   └─ Unit tests          │
     └──────────────┬───────────┘
                    │
                    ├─ FAILED? Fix & retry
                    │
                    ↓
     ┌──────────────────────────┐
     │ 4. GIT COMMIT            │
     │ git add .                │
     │ git commit -m            │
     │  "feat: allow dev team..." │
     └──────────────┬───────────┘
                    │
                    ↓
     ┌──────────────────────────┐
     │ 5. PUSH & CREATE PR      │
     │ git push origin           │
     │ gh pr create              │
     └──────────────┬───────────┘
                    │
                    ├─ NO: Fix & push again
                    ↓
    ┌───────────────────────────┐
    │ 6. CI/CD PIPELINE STARTS  │
    │ .github/workflows/        │
    │  validate-pr.yml:         │
    │  ├─ YAML lint             │
    │  ├─ Schema validation      │
    │  ├─ Policy unit tests      │
    │  ├─ Security scan          │
    │  ├─ Impact analysis        │
    │  └─ Staging dry-run        │
    └───────────────┬────────────┘
                    │
                    ├─ FAILED? Review & discuss
                    │
                    ↓
    ┌───────────────────────────┐
    │ 7. HUMAN REVIEW           │
    │ Reviewers:                │
    │  - CODEOWNERS             │
    │  - Security team          │
    │  - Team leads             │
    │ Review:                   │
    │  - Intent of change       │
    │  - Security implications  │
    │  - Performance impact     │
    │  - Compliance audit       │
    └───────────────┬────────────┘
                    │
                    ├─ APPROVED? Proceed
                    │ NOT APPROVED? Fix & retry
                    │
                    ↓
    ┌───────────────────────────┐
    │ 8. MERGE TO MAIN          │
    │ (squash commits)          │
    │ Triggers:                 │
    │  - Tag commit with SHA    │
    │  - deploy-staging.yml     │
    └───────────────┬────────────┘
                    │
                    ↓
    ┌───────────────────────────┐
    │ 9. DEPLOY TO STAGING      │
    │ .github/workflows/        │
    │  deploy-staging.yml:      │
    │  ├─ Load policies to staging
    │  ├─ Run integration tests │
    │  ├─ Smoke tests           │
    │  ├─ Verify in staging     │
    │  └─ Report status         │
    └───────────────┬────────────┘
                    │
                    ├─ FAILED? Rollback
                    │
                    ↓
    ┌───────────────────────────┐
    │ 10. MANUAL APPROVAL       │
    │ Deploy to Production?     │
    │ (requires explicit manual │
    │  trigger or button click) │
    └───────────────┬────────────┘
                    │
                    ├─ YES: Proceed to prod
                    │ NO: Keep in staging
                    │
                    ↓
    ┌───────────────────────────┐
    │ 11. DEPLOY TO PRODUCTION  │
    │ .github/workflows/        │
    │  deploy-production.yml:   │
    │  ├─ Load policies to prod │
    │  ├─ Gradual rollout (5%)  │
    │  ├─ Monitor (5 min)       │
    │  ├─ If OK: Increase to 50%│
    │  ├─ Monitor (10 min)      │
    │  └─ If OK: 100% rollout   │
    └───────────────┬────────────┘
                    │
                    ├─ FAILED? Automatic rollback
                    │
                    ↓
    ┌───────────────────────────┐
    │ 12. COMPLETE              │
    │ Policies live in prod     │
    │ Tag release on GitHub     │
    │ Update CHANGELOG.md       │
    └───────────────────────────┘
```

### 4.2 Rollback Procedure

```
Detected an issue in production?

1. Automatic Rollback (if deployment failed)
   └─ Failed health check → revert instantly

2. Manual Rollback (if issue discovered later)
   ├─ $ git revert <commit-hash>
   ├─ $ git push origin main
   ├─ Automatic CI triggers rollback deployment
   └─ Monitor for success

3. Emergency Break-Glass (if policy blocks critical access)
   ├─ Create emergency-access.yaml
   ├─ Temporary Allow policy (manual review)
   ├─ Push to main with flagged commit
   ├─ Deploy immediately (skip tests)
   ├─ Notify security team
   ├─ Post-incident review within 24h
   └─ Remove emergency access
```

---

## 5. PR Template

```markdown
# Policy Change

## Type
- [ ] Feature (new policy)
- [ ] Bug fix (incorrect policy)
- [ ] Improvement (optimize existing)
- [ ] Chore (refactor, docs)

## Description
Explain the **why**, not the what.

## Affected Principals
- [ ] Users: (list or pattern)
- [ ] Service accounts: (list)
- [ ] Teams: (list)

## Affected Resources
- [ ] Bucket: (name or pattern)
- [ ] Actions: (list, e.g., s3:GetObject)
- [ ] Environments: (dev, staging, prod)

## Security Considerations
- Principle of least privilege: (Y/N)
- Data sensitivity level: (public, internal, confidential, secret)
- Compliance requirements: (GDPR, HIPAA, SOC2, etc.)
- Audit enabled: (Y/N)

## Testing
- [ ] Dry-run in policy engine
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual verification in staging

## Rollout Plan
- [ ] Immediate
- [ ] Canary (5% → 50% → 100%)
- [ ] Gradual schedule: (date)

## Approval Checklist
- [ ] Security team reviewed
- [ ] Team lead approved
- [ ] No policy conflicts
- [ ] Documentation updated
```

---

## 6. CI/CD Workflows

### 6.1 validate-pr.yml

```yaml
name: Validate Policy PR

on: [pull_request]

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: YAML Lint
        run: yamllint policies/
      
      - name: Schema Validation
        run: |
          for file in policies/**/*.yaml; do
            jsonschema -i "$file" schemas/policy-schema.json || exit 1
          done
      
      - name: Conflict Detection
        run: make validate-conflicts
      
      - name: Security Scan
        run: make security-scan
      
      - name: Unit Tests
        run: npm test tests/unit/
      
      - name: Impact Analysis
        run: make analyze-impact
        
      - name: Dry-Run in Staging
        run: make dry-run-staging
```

### 6.2 deploy-staging.yml

```yaml
name: Deploy to Staging

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Load Policies
        run: |
          curl -X POST \
            -H "Authorization: Bearer ${{ secrets.POLICY_ENGINE_TOKEN }}" \
            -H "Content-Type: application/yaml" \
            -d @policies/staging/rbac.yaml \
            https://policy-engine-staging/api/policies/load
      
      - name: Integration Tests
        run: npm test tests/integration/
      
      - name: Smoke Tests
        run: make smoke-tests ENVIRONMENT=staging
      
      - name: Slack Notification
        if: always()
        uses: slackapi/slack-github-action@v1
        with:
          webhook-url: ${{ secrets.SLACK_WEBHOOK }}
          payload: |
            {
              "text": "Staging deployment: ${{ job.status }}",
              "blocks": [...]
            }
```

### 6.3 deploy-production.yml

```yaml
name: Deploy to Production

on:
  workflow_dispatch:  # Manual trigger only
    inputs:
      approval_reason:
        description: "Why are you deploying?"
        required: true

jobs:
  deploy:
    runs-on: ubuntu-latest
    environment:
      name: production
      required_reviewers: ["security-team", "ops-team"]
    steps:
      - uses: actions/checkout@v3
      
      - name: Canary Deployment (5%)
        run: |
          kubectl set env deployment/policy-engine \
            POLICY_ROLLOUT_PERCENTAGE=5
      
      - name: Monitor Canary (5 min)
        run: make monitor ENVIRONMENT=production TIMEOUT=300
      
      - name: If OK, Increase to 50%
        run: |
          kubectl set env deployment/policy-engine \
            POLICY_ROLLOUT_PERCENTAGE=50
      
      - name: Monitor (10 min)
        run: make monitor ENVIRONMENT=production TIMEOUT=600
      
      - name: If OK, Full Rollout (100%)
        run: |
          kubectl set env deployment/policy-engine \
            POLICY_ROLLOUT_PERCENTAGE=100
      
      - name: Final Smoke Tests
        run: make smoke-tests ENVIRONMENT=production
      
      - name: Create Release Tag
        run: |
          git tag "policies-$(date +%Y%m%d-%H%M%S)"
          git push origin --tags
      
      - name: Update CHANGELOG
        run: |
          echo "$(date): Policies deployed to production" >> CHANGELOG.md
          git add CHANGELOG.md
          git commit -m "chore: Update changelog"
          git push
```

---

## 7. Examples

### 7.1 Simple RBAC Policy

```yaml
version: "1.0"

policies:
  - name: "dev-read-dev-bucket"
    id: "policy-001"
    description: "Allow dev team to read from development bucket"
    effect: "Allow"
    
    principals:
      type: "role"
      values: ["dev"]
    
    actions:
      - "s3:GetObject"
      - "s3:ListBucket"
    
    resources:
      - "arn:aws:s3:::dev-bucket/*"
      - "arn:aws:s3:::dev-bucket"
    
    conditions: []
    audit: true
```

### 7.2 ABAC with Conditions

```yaml
version: "1.0"

policies:
  - name: "team-lead-all-team-buckets"
    id: "policy-002"
    description: "Team leads can access all buckets owned by their team"
    effect: "Allow"
    
    principals:
      type: "group"
      values: ["team-leads"]
    
    actions:
      - "s3:GetObject"
      - "s3:PutObject"
      - "s3:DeleteObject"
      - "s3:ListBucket"
    
    resources:
      - "arn:aws:s3:::*/*"
    
    conditions:
      - type: "StringEquals"
        key: "aws:ResourceTag/Owner"
        value: "${aws:PrincipalTag/Team}"
      
      - type: "TimeOfDay"
        key: "aws:CurrentTime"
        value: "09:00-17:00"
      
      - type: "Bool"
        key: "aws:MultiFactorAuthPresent"
        value: true
    
    audit: true
```

### 7.3 Deny Rules

```yaml
version: "1.0"

policies:
  - name: "deny-delete-prod-without-approval"
    id: "policy-003"
    description: "Prevent deletion from production without special approval"
    effect: "Deny"
    
    principals:
      type: "role"
      values: ["dev", "admin"]  # Even admins
    
    actions:
      - "s3:DeleteObject"
      - "s3:DeleteObjectVersion"
    
    resources:
      - "arn:aws:s3:::prod-*/*"
    
    conditions:
      - type: "StringNotEquals"
        key: "aws:PrincipalTag/DeleteApproval"
        value: "2025-01-15"  # Specific approval date
    
    audit: true
```

---

## 8. Best Practices

1. **Policy per concern** — One policy file per logical concern
2. **Descriptive names** — Use business language in policy names
3. **Always explain why** — Comments on complex conditions
4. **Audit everything** — Set audit=true for all production policies
5. **Test before deploy** — Use dry-run, unit tests, integration tests
6. **Small commits** — One policy change per PR
7. **Review thoroughly** — Security + business review
8. **Monitor deployments** — Watch for denied requests after change
9. **Document exceptions** — If a policy doesn't follow pattern, explain
10. **Regular audits** — Review policies quarterly for needed updates

---

## 9. Troubleshooting

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| Policy not matching | Wrong principal/action/resource | Check exact values, use dry-run |
| Unexpected deny | Conflicting deny rule | Review all Deny policies |
| Missing audit logs | audit: false | Set audit: true |
| Slow evaluation | Too many conditions | Simplify with wildcards |
| Circular dependency | Policy A depends on B, B on A | Restructure policies |

---

## 10. Compliance & Audit

### 10.1 Audit Trail

Git provides full audit trail:
```bash
git log --oneline --all --graph
git blame policies/prod/rbac.yaml
git show <commit-hash>:policies/prod/rbac.yaml
```

### 10.2 Approval Chain

```
PR created → CI runs → Auto-reviews → Manual approval → Deploy
```

### 10.3 Retention

- Keep git history indefinitely
- Audit logs: 90+ days
- Compliance reports: Annual review

---

## 11. Configuration Reference

```yaml
ironbucket:
  policies:
    # Source
    source: "git"
    repo_url: "https://github.com/org/ironbucket-policies.git"
    repo_branch: "main"
    poll_interval_seconds: 300
    
    # Validation
    enforce_schema: true
    enforce_semantic_rules: true
    detect_conflicts: true
    
    # Caching
    cache_policies: true
    cache_ttl_minutes: 60
    
    # Deployment
    staging_delay_minutes: 5
    production_requires_approval: true
    canary_percentage: 5
    canary_duration_minutes: 5
    
    # Audit
    audit_all_changes: true
    audit_retention_days: 90
```

---

## 12. Future Enhancements

- [ ] Policy templates to reduce duplication
- [ ] Multi-signature approval for sensitive changes
- [ ] Encrypted policy storage
- [ ] Policy versioning and rollback UI
- [ ] Policy impact simulator
- [ ] Access request flow integration
- [ ] Temporal access (time-limited permissions)
