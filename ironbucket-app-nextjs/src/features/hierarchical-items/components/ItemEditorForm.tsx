import React from 'react';

interface ParentOption {
  id: string;
  name: string;
}

interface ItemEditorFormProps {
  mode: 'create' | 'edit';
  name: string;
  description: string;
  selectedParentId: string | null;
  parentOptions: ParentOption[];
  onNameChange: (value: string) => void;
  onDescriptionChange: (value: string) => void;
  onParentChange: (value: string | null) => void;
  onSubmit: () => void;
  onCancel?: () => void;
}

export function ItemEditorForm({
  mode,
  name,
  description,
  selectedParentId,
  parentOptions,
  onNameChange,
  onDescriptionChange,
  onParentChange,
  onSubmit,
  onCancel
}: ItemEditorFormProps) {
  const isEdit = mode === 'edit';

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-xl font-bold text-slate-900">{isEdit ? 'Edit Item' : 'Create New Item'}</h2>
      <div className="mt-4 space-y-3">
        <input
          type="text"
          value={name}
          onChange={(e) => onNameChange(e.target.value)}
          placeholder="Item name (required)"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 shadow-sm outline-none ring-cyan-400 transition focus:ring"
        />

        <textarea
          value={description}
          onChange={(e) => onDescriptionChange(e.target.value)}
          placeholder="Item description (optional)"
          rows={3}
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 shadow-sm outline-none ring-cyan-400 transition focus:ring"
        />

        {!isEdit && (
          <select
            value={selectedParentId ?? ''}
            onChange={(e) => onParentChange(e.target.value || null)}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 shadow-sm outline-none ring-cyan-400 transition focus:ring"
          >
            <option value="">No parent (create root item)</option>
            {parentOptions.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        )}

        <div className="flex gap-3">
          <button
            type="button"
            onClick={onSubmit}
            disabled={!name.trim()}
            className="rounded-lg bg-cyan-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-cyan-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {isEdit ? 'Update Item' : 'Create Item'}
          </button>

          {isEdit && onCancel && (
            <button
              type="button"
              onClick={onCancel}
              className="rounded-lg bg-slate-200 px-4 py-2 text-sm font-semibold text-slate-700 transition hover:bg-slate-300"
            >
              Cancel
            </button>
          )}
        </div>
      </div>
    </section>
  );
}
