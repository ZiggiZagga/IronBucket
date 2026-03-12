/** @jest-environment jsdom */
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { useQuery } from '@apollo/client';
import '@testing-library/jest-dom';
import BucketList from '@/components/ironbucket/BucketList';

jest.mock('@apollo/client', () => ({
  useQuery: jest.fn(),
  gql: (literals: TemplateStringsArray, ...placeholders: unknown[]) =>
    literals.reduce((acc, part, index) => acc + part + (placeholders[index] ?? ''), '')
}));

const mockedUseQuery = useQuery as jest.Mock;

describe('BucketList Component', () => {
  const refetch = jest.fn();

  const mockBuckets = [
    { name: 'bucket-1', creationDate: '2026-01-01T00:00:00Z', ownerTenant: 'test-tenant' },
    { name: 'bucket-2', creationDate: '2026-01-02T00:00:00Z', ownerTenant: 'test-tenant' }
  ];

  beforeEach(() => {
    refetch.mockReset();
    mockedUseQuery.mockReturnValue({
      data: { listBuckets: mockBuckets },
      loading: false,
      error: undefined,
      refetch
    });
  });

  it('should render loading state initially', () => {
    mockedUseQuery.mockReturnValue({ data: undefined, loading: true, error: undefined, refetch });
    render(<BucketList />);

    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it('should render list of buckets', async () => {
    render(<BucketList />);

    await waitFor(() => {
      expect(screen.getByText('bucket-1')).toBeInTheDocument();
      expect(screen.getByText('bucket-2')).toBeInTheDocument();
      expect(screen.getAllByText('read-write').length).toBe(2);
    });
  });

  it('should render empty state when no buckets exist', async () => {
    mockedUseQuery.mockReturnValue({ data: { listBuckets: [] }, loading: false, error: undefined, refetch });
    render(<BucketList />);

    await waitFor(() => {
      expect(screen.getByText(/no buckets found/i)).toBeInTheDocument();
    });
  });

  it('should render error state when query fails', async () => {
    mockedUseQuery.mockReturnValue({ data: undefined, loading: false, error: new Error('boom'), refetch });
    render(<BucketList />);

    await waitFor(() => {
      expect(screen.getByText(/error loading buckets/i)).toBeInTheDocument();
    });
  });

  it('should show create bucket dialog when clicking create button', async () => {
    render(<BucketList />);

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
    render(<BucketList />);

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

  it('should refetch buckets when refresh button is clicked', async () => {
    render(<BucketList />);
    fireEvent.click(screen.getByLabelText(/refresh/i));
    expect(refetch).toHaveBeenCalledTimes(1);
  });
});
