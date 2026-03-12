'use client';

import { useMutation, useQuery } from '@apollo/client';
import { useState } from 'react';
import { gql } from '@apollo/client';

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

const GET_CHILDREN = gql`
  query GetChildren($parentId: ID!) {
    childrenByParent(parentId: $parentId) {
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

const MOVE_ITEM = gql`
  mutation MoveItem($id: String!, $parentId: ID!) {
    moveItem(id: $id, parentId: $parentId) {
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

interface ItemNode extends Item {
  children?: ItemNode[];
}

export default function Page() {
  const { data: rootData, loading, error, refetch } = useQuery(GET_ROOT_ITEMS);
  const { data: childrenData } = useQuery(GET_CHILDREN, {
    skip: true
  });

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
      setEditingId(null);
      setName('');
      setDescription('');
    }
  });

  const [moveItem] = useMutation(MOVE_ITEM, {
    onCompleted: () => refetch()
  });

  const [deleteItem] = useMutation(DELETE_ITEM, {
    onCompleted: () => refetch()
  });

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [selectedParentId, setSelectedParentId] = useState<string | null>(null);
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const [childrenCache] = useState<Record<string, ItemNode[]>>({});

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

  const toggleExpand = async (parentId: string) => {
    const newExpanded = new Set(expandedIds);
    if (newExpanded.has(parentId)) {
      newExpanded.delete(parentId);
    } else {
      newExpanded.add(parentId);
      if (!childrenCache[parentId] && childrenData) {
      }
    }
    setExpandedIds(newExpanded);
  };

  const renderItemTree = (item: Item, level: number = 0) => {
    const children = childrenCache[item.id] || [];
    const isExpanded = expandedIds.has(item.id);

    return (
      <div key={item.id} style={{ marginLeft: `${level * 16}px` }}>
        <div className="flex items-center gap-2 p-3 border border-gray-200 rounded-lg mb-2 hover:bg-gray-50">
          {children.length > 0 && (
            <button
              onClick={() => toggleExpand(item.id)}
              className="text-blue-600 hover:text-blue-800 font-bold w-6"
            >
              {isExpanded ? '▼' : '▶'}
            </button>
          )}
          <div className="flex-1">
            <h3 className="font-bold text-lg text-gray-900">{item.name}</h3>
            {item.description && (
              <p className="text-gray-600 text-sm">{item.description}</p>
            )}
            <p className="text-xs text-gray-400 mt-1">ID: {item.id}</p>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => startEdit(item)}
              disabled={editingId !== null}
              className="px-3 py-1 bg-amber-500 text-white rounded text-sm hover:bg-amber-600 disabled:bg-gray-400"
            >
              Edit
            </button>
            <button
              onClick={() => handleDelete(item.id)}
              className="px-3 py-1 bg-red-600 text-white rounded text-sm hover:bg-red-700"
            >
              Delete
            </button>
          </div>
        </div>

        {isExpanded && children.length > 0 && (
          <div className="ml-4 space-y-2">
            {children.map((child) => renderItemTree(child, level + 1))}
          </div>
        )}
      </div>
    );
  };

  return (
    <section className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold mb-2">Hierarchical Items</h1>
        <p className="text-gray-600">Organize items in a hierarchical tree structure</p>
      </div>

      <div className="bg-blue-50 border border-blue-200 rounded-lg p-6">
        <h2 className="text-xl font-semibold mb-4">
          {editingId ? 'Edit Item' : 'Create New Item'}
        </h2>
        <div className="space-y-3">
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Item name (required)"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Item description (optional)"
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            rows={3}
          />
          {!editingId && (
            <select
              value={selectedParentId || ''}
              onChange={(e) => setSelectedParentId(e.target.value || null)}
              className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="">No parent (create root item)</option>
              {rootData?.rootItems?.map((item: Item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          )}
          <div className="flex gap-2">
            <button
              onClick={editingId ? handleUpdate : handleCreate}
              disabled={!name.trim()}
              className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 font-medium"
            >
              {editingId ? 'Update' : 'Create'}
            </button>
            {editingId && (
              <button
                onClick={cancelEdit}
                className="px-4 py-2 bg-gray-300 text-gray-800 rounded-md hover:bg-gray-400 font-medium"
              >
                Cancel
              </button>
            )}
          </div>
        </div>
      </div>

      <div>
        <h2 className="text-2xl font-semibold mb-4">Items</h2>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-md mb-4">
            <p className="font-semibold">Error loading items</p>
            <p className="text-sm">{error.message}</p>
          </div>
        )}

        {loading ? (
          <div className="text-center py-8">
            <div className="inline-block animate-spin">⏳</div>
            <p className="mt-2 text-gray-600">Loading items...</p>
          </div>
        ) : rootData?.rootItems?.length === 0 ? (
          <div className="text-center py-8 bg-gray-50 rounded-md">
            <p className="text-gray-500">No items yet. Create one to get started!</p>
          </div>
        ) : (
          <div className="space-y-2">
            {rootData?.rootItems?.map((item: Item) => renderItemTree(item))}
          </div>
        )}
      </div>

      <div className="bg-gray-50 border border-gray-200 rounded-lg p-4 text-sm text-gray-600">
        <p className="font-semibold mb-2">💡 Tips:</p>
        <ul className="list-disc list-inside space-y-1">
          <li>Create root items by leaving the parent field empty</li>
          <li>Create child items by selecting a parent</li>
          <li>Click the arrow (▶) to expand/collapse item children</li>
          <li>Edit items to change their name or description</li>
          <li>Deleting an item will cascade delete all children</li>
        </ul>
      </div>
    </section>
  );
}
