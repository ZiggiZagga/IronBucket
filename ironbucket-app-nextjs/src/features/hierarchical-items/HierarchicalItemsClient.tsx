'use client';

import { gql, useMutation, useQuery } from '@apollo/client';
import { useMemo, useState } from 'react';
import { parseAsString, useQueryState } from 'nuqs';
import { ItemEditorForm } from './components/ItemEditorForm';

const GET_ROOT_ITEMS = gql`
  query GetRootItems {
    rootItems {
      id
      name
      description
      parentId
    }
  }
`;

const CREATE_ITEM = gql`
  mutation CreateItem($name: String!, $description: String, $parentId: ID) {
    createItem(name: $name, description: $description, parentId: $parentId) {
      id
      name
      description
      parentId
    }
  }
`;

const UPDATE_ITEM = gql`
  mutation UpdateItem($id: String!, $name: String, $description: String) {
    updateItem(id: $id, name: $name, description: $description) {
      id
      name
      description
      parentId
    }
  }
`;

const DELETE_ITEM = gql`
  mutation DeleteItem($id: String!) {
    deleteItem(id: $id)
  }
`;

interface Item {
  id: string;
  name: string;
  description?: string;
  parentId?: string;
}

export function HierarchicalItemsClient() {
  const { data: rootData, loading, error, refetch } = useQuery(GET_ROOT_ITEMS);

  const [createItem] = useMutation(CREATE_ITEM, {
    onCompleted: () => {
      refetch();
      setName('');
      setDescription('');
      setSelectedParentId(null);
    }
  });

  const [updateItem] = useMutation(UPDATE_ITEM, {
    onCompleted: () => {
      refetch();
      cancelEdit();
    }
  });

  const [deleteItem] = useMutation(DELETE_ITEM, {
    onCompleted: () => refetch()
  });

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [selectedParentId, setSelectedParentId] = useState<string | null>(null);
  const [search, setSearch] = useQueryState('q', parseAsString.withDefault(''));

  const rootItems: Item[] = rootData?.rootItems ?? [];

  const filteredItems = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) {
      return rootItems;
    }

    return rootItems.filter((item) => {
      const haystack = `${item.name} ${item.description ?? ''}`.toLowerCase();
      return haystack.includes(query);
    });
  }, [rootItems, search]);

  const handleCreate = async () => {
    if (!name.trim()) return;
    await createItem({
      variables: {
        name,
        description: description || null,
        parentId: selectedParentId
      }
    });
  };

  const handleUpdate = async () => {
    if (!editingId || !name.trim()) return;
    await updateItem({ variables: { id: editingId, name, description: description || null } });
  };

  const handleDelete = async (id: string) => {
    if (confirm('Delete this item and all children?')) {
      await deleteItem({ variables: { id } });
    }
  };

  const startEdit = (item: Item) => {
    setEditingId(item.id);
    setName(item.name);
    setDescription(item.description || '');
    setSelectedParentId(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setName('');
    setDescription('');
    setSelectedParentId(null);
  };

  return (
    <section className="grid gap-6 lg:grid-cols-[1.15fr_0.85fr]">
      <div className="space-y-4">
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <h2 className="text-xl font-bold text-slate-900">Items</h2>
            <div className="flex items-center gap-2">
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value || null)}
                placeholder="Filter by name or description..."
                className="w-full min-w-56 rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-900 outline-none ring-cyan-400 transition focus:ring"
              />
            </div>
          </div>

          {error && (
            <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
              <p className="font-semibold">Error loading items</p>
              <p>{error.message}</p>
            </div>
          )}

          {loading ? (
            <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-6 text-sm text-slate-600">Loading items...</div>
          ) : filteredItems.length === 0 ? (
            <div className="mt-6 rounded-xl border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-600">
              No items match this view. Try adjusting your filter or create a new item.
            </div>
          ) : (
            <ul className="mt-4 space-y-3">
              {filteredItems.map((item) => (
                <li key={item.id} className="rounded-xl border border-slate-200 bg-slate-50/70 p-4 transition hover:border-cyan-200 hover:bg-cyan-50/40">
                  <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                    <div>
                      <h3 className="text-base font-bold text-slate-900">{item.name}</h3>
                      {item.description && <p className="mt-1 text-sm text-slate-600">{item.description}</p>}
                      <p className="mt-2 text-xs text-slate-400">ID: {item.id}</p>
                    </div>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={() => startEdit(item)}
                        disabled={editingId !== null}
                        className="rounded-lg bg-amber-500 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-amber-600 disabled:cursor-not-allowed disabled:bg-slate-300"
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDelete(item.id)}
                        className="rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white transition hover:bg-rose-700"
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <div className="space-y-4">
        <ItemEditorForm
          mode={editingId ? 'edit' : 'create'}
          name={name}
          description={description}
          selectedParentId={selectedParentId}
          parentOptions={rootItems.map((item) => ({ id: item.id, name: item.name }))}
          onNameChange={setName}
          onDescriptionChange={setDescription}
          onParentChange={setSelectedParentId}
          onSubmit={editingId ? handleUpdate : handleCreate}
          onCancel={editingId ? cancelEdit : undefined}
        />
      </div>
    </section>
  );
}
