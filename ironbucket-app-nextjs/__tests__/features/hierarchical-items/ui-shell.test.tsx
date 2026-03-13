/** @jest-environment jsdom */
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { HierarchyHeader, HierarchyTipsCard } from '@/features/hierarchical-items/components/HierarchyShell';

describe('HierarchyShell components', () => {
  it('renders the hierarchy header with title and subtitle', () => {
    render(<HierarchyHeader totalItems={7} />);

    expect(screen.getByRole('heading', { name: /hierarchical items/i })).toBeInTheDocument();
    expect(screen.getByText(/organize items in a hierarchical tree structure/i)).toBeInTheDocument();
    expect(screen.getByText(/7 items/i)).toBeInTheDocument();
  });

  it('renders actionable user tips', () => {
    render(<HierarchyTipsCard />);

    expect(screen.getByText(/tips for faster item management/i)).toBeInTheDocument();
    expect(screen.getByText(/create root items by leaving the parent field empty/i)).toBeInTheDocument();
    expect(screen.getByText(/deleting an item will cascade delete all children/i)).toBeInTheDocument();
  });
});
