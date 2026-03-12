/** @jest-environment jsdom */
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MockedProvider } from '@apollo/client/testing';
import '@testing-library/jest-dom';
import BucketList from '@/components/ironbucket/BucketList';
import { GET_BUCKETS } from '@/graphql/ironbucket-queries';

describe('BucketList Component', () => {
  const mockBuckets = [
    { name: 'bucket-1', creationDate: '2026-01-01T00:00:00Z', ownerTenant: 'test-tenant' },
    { name: 'bucket-2', creationDate: '2026-01-02T00:00:00Z', ownerTenant: 'test-tenant' }
  ];

  const mocks = [
    {
      request: { query: GET_BUCKETS },
      result: { data: { listBuckets: mockBuckets } }
    }
  ];

  it('should render loading state initially', () => {
    render(
      <MockedProvider mocks={[]} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('should render list of buckets', async () => {
    render(
      <MockedProvider mocks={mocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('bucket-1')).toBeInTheDocument();
      expect(screen.getByText('bucket-2')).toBeInTheDocument();
    });
  });

  it('should render empty state when no buckets exist', async () => {
    const emptyMocks = [{ request: { query: GET_BUCKETS }, result: { data: { listBuckets: [] } } }];

    render(
      <MockedProvider mocks={emptyMocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/no buckets found/i)).toBeInTheDocument();
    });
  });

  it('should render error state when query fails', async () => {
    const errorMocks = [{ request: { query: GET_BUCKETS }, error: new Error('Failed to load buckets') }];

    render(
      <MockedProvider mocks={errorMocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/error loading buckets/i)).toBeInTheDocument();
    });
  });

  it('should show create bucket dialog when clicking create button', async () => {
    render(
      <MockedProvider mocks={mocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      expect(screen.getByText(/create bucket/i)).toBeInTheDocument();
    });

    const createButton = screen.getByText(/create bucket/i);
    fireEvent.click(createButton);

    await waitFor(() => {
      expect(screen.getByText(/bucket name/i)).toBeInTheDocument();
    });
  });

  it('should filter buckets by search query', async () => {
    render(
      <MockedProvider mocks={mocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('bucket-1')).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText(/search buckets/i);
    fireEvent.change(searchInput, { target: { value: 'bucket-2' } });

    await waitFor(() => {
      expect(screen.queryByText('bucket-1')).not.toBeInTheDocument();
      expect(screen.getByText('bucket-2')).toBeInTheDocument();
    });
  });

  it('should show access level indicator for each bucket', async () => {
    render(
      <MockedProvider mocks={mocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      expect(screen.getAllByText(/read-write|read-only/i).length).toBeGreaterThan(0);
    });
  });

  it('should refetch buckets when refresh button is clicked', async () => {
    render(
      <MockedProvider mocks={mocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      const refreshButton = screen.getByLabelText(/refresh/i);
      fireEvent.click(refreshButton);
    });
  });

  it('should use cached data on component remount', async () => {
    const { unmount } = render(
      <MockedProvider mocks={mocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('bucket-1')).toBeInTheDocument();
    });

    unmount();

    render(
      <MockedProvider mocks={mocks} addTypename={false}>
        <BucketList />
      </MockedProvider>
    );

    await waitFor(() => {
      expect(screen.getByText('bucket-1')).toBeInTheDocument();
    });
  });
});
