/** @jest-environment jsdom */
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MockedProvider } from '@apollo/client/testing';
import '@testing-library/jest-dom';
import PolicyEditor from '@/components/ironbucket/admin/PolicyEditor';
import { CREATE_POLICY, UPDATE_POLICY, DRY_RUN_POLICY } from '@/graphql/ironbucket-mutations';

describe('PolicyEditor Component', () => {
  const mockPolicy = {
    id: 'policy-1',
    tenant: 'test-tenant',
    roles: ['admin'],
    allowedBuckets: ['*'],
    allowedPrefixes: ['*'],
    operations: ['s3:*']
  };

  it('should render empty policy editor', () => {
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <PolicyEditor />
      </MockedProvider>
    );

    expect(screen.getByLabelText(/tenant/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/roles/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/allowed buckets/i)).toBeInTheDocument();
  });

  it('should render with existing policy data', () => {
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <PolicyEditor policy={mockPolicy} />
      </MockedProvider>
    );

    expect(screen.getByDisplayValue('test-tenant')).toBeInTheDocument();
    expect(screen.getByDisplayValue('admin')).toBeInTheDocument();
  });

  it('should validate required fields', async () => {
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <PolicyEditor />
      </MockedProvider>
    );

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
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <PolicyEditor />
      </MockedProvider>
    );

    const bucketInput = screen.getByLabelText(/allowed buckets/i);
    fireEvent.change(bucketInput, { target: { value: 'INVALID_BUCKET' } });
    fireEvent.click(screen.getByText(/save policy/i));

    await waitFor(() => {
      expect(screen.getByText(/invalid bucket name/i)).toBeInTheDocument();
    });
  });

  it('should validate S3 operation format', async () => {
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <PolicyEditor />
      </MockedProvider>
    );

    const operationInput = screen.getByLabelText(/operations/i);
    fireEvent.change(operationInput, { target: { value: 'invalid:operation' } });
    fireEvent.click(screen.getByText(/save policy/i));

    await waitFor(() => {
      expect(screen.getByText(/invalid operation/i)).toBeInTheDocument();
    });
  });

  it('should test policy before saving', async () => {
    const dryRunMocks = [
      {
        request: {
          query: DRY_RUN_POLICY,
          variables: {
            policy: mockPolicy,
            operation: 's3:GetObject',
            resource: 'arn:aws:s3:::test-bucket/file.txt'
          }
        },
        result: {
          data: {
            dryRunPolicy: {
              decision: 'ALLOW',
              matchedRules: ['policy-1'],
              reason: 'Allowed by policy'
            }
          }
        }
      }
    ];

    render(
      <MockedProvider mocks={dryRunMocks} addTypename={false}>
        <PolicyEditor policy={mockPolicy} />
      </MockedProvider>
    );

    fireEvent.click(screen.getByText(/test policy/i));

    await waitFor(() => {
      expect(screen.getByText(/allowed by policy/i)).toBeInTheDocument();
      expect(screen.getByText(/^ALLOW$/i)).toBeInTheDocument();
    });
  });

  it('should show denial reason in dry-run', async () => {
    const denyMocks = [
      {
        request: {
          query: DRY_RUN_POLICY,
          variables: {
            policy: mockPolicy,
            operation: 's3:GetObject',
            resource: 'arn:aws:s3:::test-bucket/file.txt'
          }
        },
        result: {
          data: {
            dryRunPolicy: {
              decision: 'DENY',
              matchedRules: [],
              reason: 'No matching policy found'
            }
          }
        }
      }
    ];

    render(
      <MockedProvider mocks={denyMocks} addTypename={false}>
        <PolicyEditor policy={mockPolicy} />
      </MockedProvider>
    );

    fireEvent.click(screen.getByText(/test policy/i));

    await waitFor(() => {
      expect(screen.getByText(/no matching policy found/i)).toBeInTheDocument();
      expect(screen.getByText(/DENY/i)).toBeInTheDocument();
    });
  });

  it('should create new policy', async () => {
    const createMocks = [
      {
        request: {
          query: CREATE_POLICY,
          variables: { input: mockPolicy }
        },
        result: {
          data: {
            createPolicy: { ...mockPolicy, id: 'new-policy-id' }
          }
        }
      }
    ];

    render(
      <MockedProvider mocks={createMocks} addTypename={false}>
        <PolicyEditor />
      </MockedProvider>
    );

    fireEvent.change(screen.getByLabelText(/tenant/i), { target: { value: 'test-tenant' } });
    fireEvent.change(screen.getByLabelText(/roles/i), { target: { value: 'admin' } });

    fireEvent.click(screen.getByText(/save policy/i));

    await waitFor(() => {
      expect(screen.getByText(/policy created successfully/i)).toBeInTheDocument();
    });
  });

  it('should update existing policy', async () => {
    const updateMocks = [
      {
        request: {
          query: UPDATE_POLICY,
          variables: {
            id: 'policy-1',
            input: mockPolicy
          }
        },
        result: {
          data: {
            updatePolicy: { ...mockPolicy, version: 2 }
          }
        }
      }
    ];

    render(
      <MockedProvider mocks={updateMocks} addTypename={false}>
        <PolicyEditor policy={mockPolicy} />
      </MockedProvider>
    );

    fireEvent.click(screen.getByText(/save policy/i));

    await waitFor(() => {
      expect(screen.getByText(/policy updated successfully/i)).toBeInTheDocument();
    });
  });

  it('should highlight YAML syntax', () => {
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <PolicyEditor mode="yaml" />
      </MockedProvider>
    );

    const editor = screen.getByLabelText(/policy syntax/i);
    expect(editor).toHaveClass('syntax-highlighted');
  });

  it('should highlight JSON syntax', () => {
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <PolicyEditor mode="json" />
      </MockedProvider>
    );

    const editor = screen.getByLabelText(/policy syntax/i);
    expect(editor).toHaveClass('syntax-highlighted');
  });

  it('should render access notice and admin fields', () => {
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <PolicyEditor />
      </MockedProvider>
    );

    expect(screen.getByText(/access denied/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/tenant/i)).toBeInTheDocument();
  });
});
