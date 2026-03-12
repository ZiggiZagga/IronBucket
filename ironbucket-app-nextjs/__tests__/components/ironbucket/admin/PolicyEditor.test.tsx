/** @jest-environment jsdom */
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useMutation } from '@apollo/client';
import '@testing-library/jest-dom';
import PolicyEditor from '@/components/ironbucket/admin/PolicyEditor';
import { CREATE_POLICY, UPDATE_POLICY, DRY_RUN_POLICY } from '@/graphql/ironbucket-mutations';

jest.mock('@apollo/client', () => ({
  useMutation: jest.fn(),
  gql: (literals: TemplateStringsArray, ...placeholders: unknown[]) =>
    literals.reduce((acc, part, index) => acc + part + (placeholders[index] ?? ''), '')
}));

const mockedUseMutation = useMutation as jest.Mock;

describe('PolicyEditor Component', () => {
  let createPolicyMutation: jest.Mock;
  let updatePolicyMutation: jest.Mock;
  let dryRunPolicyMutation: jest.Mock;

  const mockPolicy = {
    id: 'policy-1',
    tenant: 'test-tenant',
    roles: ['admin'],
    allowedBuckets: ['*'],
    allowedPrefixes: ['*'],
    operations: ['s3:*']
  };

  beforeEach(() => {
    createPolicyMutation = jest.fn().mockResolvedValue({
      data: { createPolicy: { ...mockPolicy, id: 'new-policy-id' } }
    });
    updatePolicyMutation = jest.fn().mockResolvedValue({
      data: { updatePolicy: { ...mockPolicy, version: 2 } }
    });
    dryRunPolicyMutation = jest.fn().mockResolvedValue({
      data: {
        dryRunPolicy: {
          decision: 'ALLOW',
          matchedRules: ['policy-1'],
          reason: 'Allowed by policy'
        }
      }
    });

    mockedUseMutation.mockImplementation((doc: unknown) => {
      if (doc === CREATE_POLICY) {
        return [createPolicyMutation];
      }
      if (doc === UPDATE_POLICY) {
        return [updatePolicyMutation];
      }
      if (doc === DRY_RUN_POLICY) {
        return [dryRunPolicyMutation];
      }
      return [jest.fn()];
    });
  });

  it('should render empty policy editor', () => {
    render(<PolicyEditor />);

    expect(screen.getByLabelText(/tenant/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/roles/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/allowed buckets/i)).toBeInTheDocument();
  });

  it('should render with existing policy data', () => {
    render(<PolicyEditor policy={mockPolicy} />);

    expect(screen.getByDisplayValue('test-tenant')).toBeInTheDocument();
    expect(screen.getByDisplayValue('admin')).toBeInTheDocument();
  });

  it('should validate required fields', async () => {
    render(<PolicyEditor />);

    fireEvent.change(screen.getByLabelText(/tenant/i), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText(/roles/i), { target: { value: '' } });

    const saveButton = screen.getByText(/save policy/i);
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(screen.getByText(/tenant is required/i)).toBeInTheDocument();
      expect(screen.getByText(/at least one role is required/i)).toBeInTheDocument();
    });
  });

  it('should validate bucket name format', async () => {
    render(<PolicyEditor />);

    const bucketInput = screen.getByLabelText(/allowed buckets/i);
    fireEvent.change(bucketInput, { target: { value: 'INVALID_BUCKET' } });
    fireEvent.click(screen.getByText(/save policy/i));

    await waitFor(() => {
      expect(screen.getByText(/invalid bucket name/i)).toBeInTheDocument();
    });
  });

  it('should validate S3 operation format', async () => {
    render(<PolicyEditor />);

    const operationInput = screen.getByLabelText(/operations/i);
    fireEvent.change(operationInput, { target: { value: 'invalid:operation' } });
    fireEvent.click(screen.getByText(/save policy/i));

    await waitFor(() => {
      expect(screen.getByText(/invalid operation/i)).toBeInTheDocument();
    });
  });

  it('should test policy before saving', async () => {
    render(<PolicyEditor policy={mockPolicy} />);

    fireEvent.click(screen.getByText(/test policy/i));

    await waitFor(() => {
      expect(dryRunPolicyMutation).toHaveBeenCalledWith({
        variables: {
          policy: mockPolicy,
          operation: 's3:GetObject',
          resource: 'arn:aws:s3:::test-bucket/file.txt'
        }
      });
    });

    await waitFor(() => {
      expect(screen.getByText(/allowed by policy/i)).toBeInTheDocument();
      expect(screen.getByText(/^ALLOW$/i)).toBeInTheDocument();
    });
  });

  it('should show denial reason in dry-run', async () => {
    dryRunPolicyMutation.mockResolvedValueOnce({
      data: {
        dryRunPolicy: {
          decision: 'DENY',
          matchedRules: [],
          reason: 'No matching policy found'
        }
      }
    });

    render(<PolicyEditor policy={mockPolicy} />);

    fireEvent.click(screen.getByText(/test policy/i));

    await waitFor(() => {
      expect(screen.getByText(/no matching policy found/i)).toBeInTheDocument();
      expect(screen.getByText(/DENY/i)).toBeInTheDocument();
    });
  });

  it('should create new policy', async () => {
    render(<PolicyEditor />);

    fireEvent.change(screen.getByLabelText(/tenant/i), { target: { value: 'test-tenant' } });
    fireEvent.change(screen.getByLabelText(/roles/i), { target: { value: 'admin' } });

    fireEvent.click(screen.getByText(/save policy/i));

    await waitFor(() => {
      expect(createPolicyMutation).toHaveBeenCalledWith({
        variables: {
          input: {
            id: 'policy-1',
            tenant: 'test-tenant',
            roles: ['admin'],
            allowedBuckets: ['*'],
            allowedPrefixes: ['*'],
            operations: ['s3:*']
          }
        }
      });
    });

    await waitFor(() => {
      expect(screen.getByText(/policy created successfully/i)).toBeInTheDocument();
    });
  });

  it('should update existing policy', async () => {
    render(<PolicyEditor policy={mockPolicy} />);

    fireEvent.click(screen.getByText(/save policy/i));

    await waitFor(() => {
      expect(updatePolicyMutation).toHaveBeenCalledWith({
        variables: {
          id: 'policy-1',
          input: mockPolicy
        }
      });
    });

    await waitFor(() => {
      expect(screen.getByText(/policy updated successfully/i)).toBeInTheDocument();
    });
  });

  it('should highlight YAML syntax', () => {
    render(<PolicyEditor mode="yaml" />);

    const editor = screen.getByLabelText(/policy syntax/i);
    expect(editor).toHaveClass('syntax-highlighted');
  });

  it('should highlight JSON syntax', () => {
    render(<PolicyEditor mode="json" />);

    const editor = screen.getByLabelText(/policy syntax/i);
    expect(editor).toHaveClass('syntax-highlighted');
  });

  it('should render access notice and admin fields', () => {
    render(<PolicyEditor />);

    expect(screen.getByText(/access denied/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/tenant/i)).toBeInTheDocument();
  });
});
