/** @jest-environment jsdom */
import { fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { ItemEditorForm } from '@/features/hierarchical-items/components/ItemEditorForm';

describe('ItemEditorForm', () => {
  it('renders create state with parent select', () => {
    render(
      <ItemEditorForm
        mode="create"
        name=""
        description=""
        selectedParentId={null}
        parentOptions={[{ id: 'p1', name: 'Parent 1' }]}
        onNameChange={() => {}}
        onDescriptionChange={() => {}}
        onParentChange={() => {}}
        onSubmit={() => {}}
      />
    );

    expect(screen.getByRole('heading', { name: /create new item/i })).toBeInTheDocument();
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create item/i })).toBeDisabled();
  });

  it('renders edit state and triggers cancel', () => {
    const onCancel = jest.fn();

    render(
      <ItemEditorForm
        mode="edit"
        name="Bucket A"
        description="desc"
        selectedParentId={null}
        parentOptions={[]}
        onNameChange={() => {}}
        onDescriptionChange={() => {}}
        onParentChange={() => {}}
        onSubmit={() => {}}
        onCancel={onCancel}
      />
    );

    expect(screen.getByRole('heading', { name: /edit item/i })).toBeInTheDocument();
    expect(screen.queryByRole('combobox')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /cancel/i }));
    expect(onCancel).toHaveBeenCalledTimes(1);
  });
});
