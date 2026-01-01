# Policy Schema & Evaluation Contract

## 1. Overview

The Policy Engine defines how IronBucket evaluates access decisions. It is the heart of the authorization layer, receiving requests with normalized identity and returning allow/deny verdicts.

**Goal:** Provide a declarative, Git-managed, human-readable policy language that supports role-based (RBAC) and attribute-based (ABAC) access control.

---

## 2. Policy Structure

### 2.1 Policy Document Format (YAML)

```yaml
# File: policies/s3-access-policies.yaml
# Git-managed, versioned, with PR workflows

version: "1.0"
description: "S3 object storage access policies"

policies:
  - name: "dev-read-bucket"
    id: "policy-001"
    description: "Allow dev team to read from dev bucket"
    effect: "Allow"                    # Allow or Deny
    
    principals:
      type: "role"                    # role, user, service-account, group
      values: ["dev", "viewer"]
    
    actions:
      - "s3:GetObject"
      - "s3:ListBucket"
      - "s3:GetObjectVersion"
    
    resources:
      - "arn:aws:s3:::dev-bucket/*"
      - "arn:aws:s3:::dev-bucket"
    
    conditions:
      - type: "StringEquals"
        key: "aws:username"
        value: "alice"
      
      - type: "IpAddress"
        key: "aws:SourceIp"
        values: ["10.0.0.0/8", "192.168.1.0/24"]
        operator: "Any"              # Any or All
      
      - type: "DateGreaterThan"
        key: "aws:CurrentTime"
        value: "2025-01-01T00:00:00Z"
      
      - type: "TimeOfDay"
        key: "aws:CurrentTime"
        value: "09:00-17:00"         # Business hours
    
    audit: true                       # Log this decision

  - name: "admin-all-buckets"
    id: "policy-002"
    description: "Admin has full access"
    effect: "Allow"
    
    principals:
      type: "role"
      values: ["admin"]
    
    actions:
      - "s3:*"
    
    resources:
      - "arn:aws:s3:::*/*"
      - "arn:aws:s3:::*"
    
    conditions: []
    audit: true

  - name: "deny-delete-prod"
    id: "policy-003"
    description: "Prevent accidental deletion from production"
    effect: "Deny"
    
    principals:
      type: "role"
      values: ["dev"]
    
    actions:
      - "s3:DeleteObject"
    
    resources:
      - "arn:aws:s3:::prod-*/*"
    
    conditions: []
    audit: true
```

### 2.2 Compact JSON Format (for API)

```json
{
  "version": "1.0",
  "policies": [
    {
      "name": "dev-read-bucket",
      "id": "policy-001",
      "description": "Allow dev team to read from dev bucket",
      "effect": "Allow",
      "principals": {
        "type": "role",
        "values": ["dev", "viewer"]
      },
      "actions": [
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "resources": [
        "arn:aws:s3:::dev-bucket/*"
      ],
      "conditions": [
        {
          "type": "StringEquals",
          "key": "aws:username",
          "value": "alice"
        }
      ],
      "audit": true
    }
  ]
}
```

---

## 3. Policy Evaluation Engine

### 3.1 Evaluation Request

```json
{
  "action": "s3:GetObject",
  "resource": "arn:aws:s3:::dev-bucket/data.csv",
  "identity": {
    "userId": "alice@acme.com",
    "username": "alice",
    "roles": ["dev", "viewer"],
    "tenant": "acme-corp",
    "ipAddress": "10.0.1.1",
    "email": "alice@acme.com"
  },
  "context": {
    "timestamp": "2025-01-15T14:30:00Z",
    "sourceIp": "10.0.1.1",
    "userAgent": "Mozilla/5.0..."
  },
  "requestId": "req-12345678"
}
```

### 3.2 Evaluation Response

```json
{
  "decision": "Allow",        // Allow, Deny, or Conditional
  "matchedPolicies": [
    "policy-001"
  ],
  "deniedPolicies": [],
  "reason": "Matched dev-read-bucket policy",
  "evaluatedAt": "2025-01-15T14:30:00.123Z",
  "evaluationTime": 45.3,     // milliseconds
  "audit": {
    "requestId": "req-12345678",
    "userId": "alice@acme.com",
    "action": "s3:GetObject",
    "resource": "arn:aws:s3:::dev-bucket/data.csv",
    "decision": "Allow",
    "reason": "Matched dev-read-bucket policy",
    "timestamp": "2025-01-15T14:30:00.123Z"
  }
}
```

---

## 4. Evaluation Algorithm

### 4.1 Step-by-Step Decision Logic

```
┌─ START EVALUATION ───────────────────────────────────────┐
│                                                           │
│ 1. LOAD ALL POLICIES for tenant                          │
│    └─ GET policies from git-policies repo                │
│                                                           │
│ 2. INITIALIZE DECISION STATE                             │
│    ├─ allowed = false                                    │
│    ├─ denied = false                                     │
│    ├─ matchedPolicies = []                               │
│    └─ deniedPolicies = []                                │
│                                                           │
│ 3. FOR EACH POLICY:                                      │
│    │                                                     │
│    ├─ 3a. Check: Does principal match?                   │
│    │      └─ identity.roles contains policy.principals   │
│    │         OR identity.userId == policy.principals     │
│    │         OR identity.groups contains policy.principal│
│    │                                                     │
│    │      ├─ NO? Skip to next policy                     │
│    │      └─ YES? Continue to 3b                         │
│    │                                                     │
│    ├─ 3b. Check: Does action match?                      │
│    │      └─ requested action == policy.actions          │
│    │         OR policy.actions contains "*"              │
│    │         OR policy.actions contains "s3:*"           │
│    │                                                     │
│    │      ├─ NO? Skip to next policy                     │
│    │      └─ YES? Continue to 3c                         │
│    │                                                     │
│    ├─ 3c. Check: Does resource match?                    │
│    │      └─ ARN pattern matching (wildcards)            │
│    │         arn:aws:s3:::my-bucket/* matches any        │
│    │         arn:aws:s3:::my-bucket/foo/bar              │
│    │                                                     │
│    │      ├─ NO? Skip to next policy                     │
│    │      └─ YES? Continue to 3d                         │
│    │                                                     │
│    ├─ 3d. Check: All conditions satisfied?               │
│    │      └─ Evaluate each condition with AND logic      │
│    │         (all conditions must be true)               │
│    │                                                     │
│    │      ├─ NO? Skip to next policy                     │
│    │      └─ YES? This policy matched!                   │
│    │                                                     │
│    ├─ 3e. Apply effect                                   │
│    │      ├─ IF effect == "Deny":                        │
│    │      │  ├─ denied = true                            │
│    │      │  ├─ deniedPolicies.add(policy.id)            │
│    │      │  └─ SHORT-CIRCUIT: Go to FINAL DECISION      │
│    │      │                                              │
│    │      └─ IF effect == "Allow":                       │
│    │         ├─ allowed = true                           │
│    │         ├─ matchedPolicies.add(policy.id)           │
│    │         └─ Continue (keep evaluating other policies)│
│                                                          │
│ 4. FINAL DECISION:                                       │
│    ├─ IF denied == true:                                 │
│    │  └─ RETURN: Decision = "Deny"                       │
│    │            Reason = "Matched deny policies: [...]"  │
│    │                                                     │
│    └─ ELSE IF allowed == true:                           │
│       └─ RETURN: Decision = "Allow"                      │
│                 Reason = "Matched allow policies: [...]" │
│                                                          │
│    └─ ELSE (no match):                                   │
│       └─ RETURN: Decision = "Deny"                       │
│                 Reason = "No matching policies"          │
│                 (Default deny / fail-closed)             │
│                                                          │
│ 5. LOG AUDIT EVENT                                       │
│    └─ Include: action, resource, principal, decision     │
│                timestamp, requestId                      │
│                                                          │
└─ END EVALUATION ────────────────────────────────────────┘
```

### 4.2 Deny-Overrides-Allow Semantics

**Critical Rule:** One DENY policy overrides ALL ALLOW policies.

```
Scenario 1: One Allow + One Deny
  │
  ├─ Policy A: Allow ("dev" role, s3:GetObject on dev-bucket)
  ├─ Policy B: Deny ("dev" role, s3:GetObject on prod-bucket)
  │
  ├─ User "alice" has role "dev"
  ├─ Action: s3:GetObject on prod-bucket
  │
  ├─ Matches Policy A? YES (role matches, action matches, but resource doesn't)
  ├─ Matches Policy B? YES (role matches, action matches, resource matches)
  │
  └─ DECISION: DENY (because one Deny matched)
```

---

## 5. Condition Types

### 5.1 String Conditions

```yaml
- type: "StringEquals"
  key: "aws:username"
  value: "alice"

- type: "StringNotEquals"
  key: "aws:username"
  value: "bob"

- type: "StringLike"
  key: "aws:username"
  value: "alice*"  # Wildcard suffix matching
```

### 5.2 Numeric Conditions

```yaml
- type: "NumericEquals"
  key: "custom:user_id"
  value: 12345

- type: "NumericGreaterThan"
  key: "custom:access_level"
  value: 5

- type: "NumericLessThanOrEquals"
  key: "custom:request_size_mb"
  value: 100
```

### 5.3 IP Address Conditions

```yaml
- type: "IpAddress"
  key: "aws:SourceIp"
  values:
    - "10.0.0.0/8"
    - "192.168.1.0/24"
  operator: "Any"  # Match any of the IPs

- type: "NotIpAddress"
  key: "aws:SourceIp"
  value: "203.0.113.0/24"  # Blacklist range
```

### 5.4 Date/Time Conditions

```yaml
- type: "DateGreaterThan"
  key: "aws:CurrentTime"
  value: "2025-01-01T00:00:00Z"

- type: "DateLessThan"
  key: "aws:CurrentTime"
  value: "2025-12-31T23:59:59Z"

- type: "TimeOfDay"
  key: "aws:CurrentTime"
  value: "09:00-17:00"  # Business hours only
```

### 5.5 Custom Attribute Conditions

```yaml
- type: "StringEquals"
  key: "custom:department"
  value: "engineering"

- type: "StringLike"
  key: "custom:project_id"
  value: "proj-*"

- type: "StringContains"
  key: "custom:clearance"
  value: "SECRET"  # Search within comma-separated values
```

### 5.6 Tag-Based Conditions

```yaml
- type: "StringEquals"
  key: "aws:PrincipalTag/Environment"
  value: "production"

- type: "StringEquals"
  key: "aws:ResourceTag/Owner"
  value: "alice@acme.com"
```

### 5.7 Boolean Conditions

```yaml
- type: "Bool"
  key: "custom:mfa_enabled"
  value: true

- type: "Bool"
  key: "aws:MultiFactorAuthPresent"
  value: true  # Require MFA for sensitive operations
```

---

## 6. Action Taxonomy

### 6.1 S3 Object Operations

```
s3:GetObject                # Read object
s3:GetObjectVersion         # Read specific version
s3:GetObjectAcl             # Read object ACL
s3:PutObject                # Upload/overwrite object
s3:PutObjectAcl             # Modify object ACL
s3:DeleteObject             # Delete object
s3:DeleteObjectVersion      # Delete specific version
s3:CopyObject               # Copy from one location to another
```

### 6.2 S3 Bucket Operations

```
s3:ListBucket               # List bucket contents
s3:GetBucketLocation        # Read bucket location
s3:GetBucketVersioning      # Read versioning status
s3:GetBucketAcl             # Read bucket ACL
s3:GetBucketPolicy          # Read bucket policy
s3:PutBucketPolicy          # Modify bucket policy
s3:DeleteBucketPolicy       # Remove policy
```

### 6.3 Wildcard Actions

```
s3:*                        # All S3 actions
s3:Get*                     # All GET operations
s3:Put*                     # All PUT operations
s3:Delete*                  # All DELETE operations
s3:*Object*                 # All object operations
s3:*Bucket*                 # All bucket operations
```

---

## 7. Resource ARN Patterns

### 7.1 ARN Syntax

```
arn:aws:s3:::bucket-name
arn:aws:s3:::bucket-name/key
arn:aws:s3:::bucket-name/prefix/*
arn:aws:s3:::bucket-name/prefix/**  # Recursive wildcard (TBD)
```

### 7.2 Example Patterns

```
arn:aws:s3:::my-bucket/*           # All objects in bucket
arn:aws:s3:::my-bucket             # The bucket itself
arn:aws:s3:::my-bucket/logs/*      # All objects in /logs prefix
arn:aws:s3:::my-bucket/logs/**     # Recursive: /logs and subdirs
arn:aws:s3:::*                      # All buckets
arn:aws:s3:::*/*                    # All objects everywhere
arn:aws:s3:::dev-*/*                # All objects in dev-* buckets
arn:aws:s3:::*/2025/*               # Year-based partitioning
```

---

## 8. Policy Composition

### 8.1 Multiple Policies (OR Logic)

When multiple policies match, they are evaluated with OR logic within their effect group:

```yaml
policies:
  # This policy allows
  - name: "dev-read-dev-bucket"
    effect: "Allow"
    principals:
      type: "role"
      values: ["dev"]
    actions: ["s3:GetObject"]
    resources: ["arn:aws:s3:::dev-bucket/*"]
  
  # This policy also allows (different resource)
  - name: "viewer-read-all"
    effect: "Allow"
    principals:
      type: "role"
      values: ["viewer"]
    actions: ["s3:GetObject"]
    resources: ["arn:aws:s3:::*/*"]

# Result: "dev" role can read dev-bucket OR viewer role can read anywhere
```

### 8.2 Nested Conditions (AND Logic)

All conditions within a policy must be true (AND):

```yaml
- name: "restricted-access"
  effect: "Allow"
  principals:
    type: "role"
    values: ["admin"]
  actions: ["s3:*"]
  resources: ["arn:aws:s3:::prod-*/*"]
  conditions:
    - type: "IpAddress"
      key: "aws:SourceIp"
      value: "10.0.0.0/8"
    
    - type: "TimeOfDay"
      key: "aws:CurrentTime"
      value: "09:00-17:00"
    
    - type: "Bool"
      key: "aws:MultiFactorAuthPresent"
      value: true

# Result: Admin can access prod buckets ONLY IF:
#   1. Source IP is in 10.0.0.0/8 AND
#   2. Current time is 09:00-17:00 AND
#   3. MFA is enabled
```

---

## 9. Dry-Run Mode

### 9.1 Dry-Run Request

```json
{
  "action": "s3:DeleteObject",
  "resource": "arn:aws:s3:::prod-bucket/important-data.csv",
  "identity": { ... },
  "context": { ... },
  "dryRun": true
}
```

### 9.2 Dry-Run Response

```json
{
  "decision": "Deny",
  "dryRun": true,
  "deniedPolicies": [
    "policy-003"
  ],
  "reason": "Matched deny-delete-prod policy (dry-run simulation)",
  "whatIf": {
    "ifApplied": "Your request would be denied because:",
    "details": [
      "Deny policy 'deny-delete-prod' prevents deletion on prod-* buckets"
    ],
    "remediation": "Contact admin if you need to delete this object",
    "suggestedAlternative": "Consider archiving instead"
  }
}
```

---

## 10. Policy Validation Rules

Every policy document must pass validation before deployment:

### 10.1 Syntax Validation

```
✅ VALID:
- All required fields present (name, effect, principals, actions, resources)
- Actions are valid S3 actions or wildcards
- Resources are valid ARNs or wildcards
- Conditions use known types
- Effect is "Allow" or "Deny"

❌ INVALID:
- Missing "effect" field
- Invalid action like "s3:ReadObject" (should be GetObject)
- Malformed ARN
- Unknown condition type
- Empty principals list
```

### 10.2 Semantic Validation

```
⚠️ WARNINGS (valid but risky):
- Overly broad resources: arn:aws:s3:::*/*
- Overly broad principals: type: "role", values: ["*"]
- Missing conditions on sensitive actions (DeleteObject)
- Deny without documented reason
- Allow without audit flag

🚫 ERRORS:
- Circular policy definitions (A depends on B which depends on A)
- Undefined role reference
- Conflicting policies in same file
```

### 10.3 Deployment Validation

Before a policy is deployed to production:

1. Schema validation (syntax check)
2. Semantic analysis (warnings)
3. Conflict detection (overlapping policies)
4. Impact analysis (what would this policy affect?)
5. Review gate (human approval)
6. Staged rollout (test environment first)

---

## 11. Policy Versioning & Evolution

### 11.1 Version Format

```yaml
version: "1.0"     # Major version
meta:
  author: "alice@acme.com"
  created: "2025-01-15T10:00:00Z"
  modified: "2025-01-15T14:30:00Z"
  change_log:
    - version: "1.0"
      date: "2025-01-15"
      change: "Initial version"
      author: "alice"
    - version: "0.9"
      date: "2025-01-10"
      change: "Draft policy"
      author: "bob"
```

### 11.2 Breaking Changes

A new version is required if:
- Principal type changes
- Resource scope changes
- Action is removed
- Deny policy added for previously allowed action

Migration path:
1. Deploy new version in parallel (canary)
2. Monitor impact (audit logs)
3. Gradually shift traffic
4. Deprecate old version

---

## 12. Audit & Compliance

### 12.1 Audit Logging Format

```json
{
  "timestamp": "2025-01-15T14:30:00.123Z",
  "requestId": "req-12345678",
  "auditType": "PolicyDecision",
  "userId": "alice@acme.com",
  "action": "s3:GetObject",
  "resource": "arn:aws:s3:::dev-bucket/data.csv",
  "decision": "Allow",
  "matchedPolicies": ["policy-001"],
  "deniedPolicies": [],
  "reason": "Matched dev-read-bucket policy",
  "sourceIp": "10.0.1.1",
  "userAgent": "Mozilla/5.0...",
  "responseTime": 45.3,
  "tenant": "acme-corp",
  "environment": "production"
}
```

### 12.2 Retention Policy

- Keep audit logs for minimum 90 days
- Longer retention for security-sensitive events (Deny, suspicious IP)
- Compress and archive after 30 days
- Enable tamper-evident logging

---

## 13. Testing Requirements

Every policy implementation must pass:

1. ✅ Policy loads without errors
2. ✅ Valid request matches expected policies
3. ✅ Deny overrides allow
4. ✅ All condition types evaluate correctly
5. ✅ Wildcard matching works for resources
6. ✅ Multiple policies evaluated correctly
7. ✅ Dry-run mode doesn't mutate state
8. ✅ Audit logs generated correctly
9. ✅ Performance < 100ms for typical request
10. ✅ Backward compatibility with v0.9 policies

---

## 14. Performance Expectations

| Operation | Latency | Notes |
|-----------|---------|-------|
| Load policies from Git | 500-2000ms | Cached, updated hourly |
| Evaluate single policy | 5-10ms | Memory operation |
| Full decision (10 policies) | 50-100ms | Worst case |
| Dry-run evaluation | 50-100ms | Same as production |
| Audit log write | < 1ms | Async queue |
| Cache hit (same user) | < 0.1ms | In-memory lookup |

---

## 15. Configuration Reference

```yaml
ironbucket:
  policy:
    # Policy Source
    policy_source: "git"
    policy_repo_url: "https://github.com/acme/ironbucket-policies.git"
    policy_repo_branch: "main"
    policy_cache_ttl_minutes: 60
    
    # Evaluation
    default_decision: "Deny"        # Fail-closed
    deny_overrides_allow: true
    condition_cache_enabled: true
    
    # Dry-Run
    dry_run_enabled: true
    dry_run_audit_log: true
    
    # Audit
    audit_log_format: "json"
    audit_log_destination: "syslog"
    audit_retention_days: 90
    
    # Performance
    policy_reload_interval_seconds: 300
    max_policies_per_tenant: 1000
    max_conditions_per_policy: 20
```

---

## 16. Future Enhancements

- [ ] Attribute-based access control (ABAC) - more conditions
- [ ] Policy templates - reduce duplication
- [ ] Conflict resolution strategies - explicit precedence
- [ ] Temporal policies - time-based access windows
- [ ] Revocation lists - quick deny-all
- [ ] Policy encryption - GitOps security
- [ ] Multi-signature approval for sensitive changes
