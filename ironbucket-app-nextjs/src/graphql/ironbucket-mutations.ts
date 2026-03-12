import { gql } from '@apollo/client';

export const UPLOAD_OBJECT = gql`
  mutation UploadObject($bucket: String!, $key: String!, $content: Upload!, $contentType: String) {
    uploadObject(bucket: $bucket, key: $key, content: $content, contentType: $contentType) {
      key
      bucket
      size
    }
  }
`;

export const CREATE_POLICY = gql`
  mutation CreatePolicy($input: PolicyInput!) {
    createPolicy(input: $input) {
      id
      tenant
      roles
      allowedBuckets
      allowedPrefixes
      operations
    }
  }
`;

export const UPDATE_POLICY = gql`
  mutation UpdatePolicy($id: String!, $input: PolicyInput!) {
    updatePolicy(id: $id, input: $input) {
      id
      tenant
      roles
      allowedBuckets
      allowedPrefixes
      operations
      version
    }
  }
`;

export const DRY_RUN_POLICY = gql`
  mutation DryRunPolicy($policy: PolicyInput!, $operation: String!, $resource: String!) {
    dryRunPolicy(policy: $policy, operation: $operation, resource: $resource) {
      decision
      matchedRules
      reason
    }
  }
`;
