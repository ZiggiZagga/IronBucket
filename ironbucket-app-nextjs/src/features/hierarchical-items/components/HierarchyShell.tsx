import React from 'react';

interface HierarchyHeaderProps {
  totalItems?: number;
}

export function HierarchyHeader({ totalItems }: HierarchyHeaderProps) {
  return (
    <header className="relative overflow-hidden rounded-2xl border border-slate-200/70 bg-white/80 p-8 shadow-sm backdrop-blur">
      <div className="pointer-events-none absolute -right-10 -top-10 h-40 w-40 rounded-full bg-cyan-200/40 blur-2xl" />
      <div className="pointer-events-none absolute -bottom-16 -left-16 h-48 w-48 rounded-full bg-amber-200/40 blur-2xl" />

      <div className="relative space-y-3">
        <p className="inline-flex items-center rounded-full border border-cyan-300/60 bg-cyan-50 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-cyan-800">
          IronBucket Control Plane
        </p>
        <h1 className="text-3xl font-black tracking-tight text-slate-900 md:text-4xl">Hierarchical Items</h1>
        <p className="max-w-2xl text-sm text-slate-600 md:text-base">
          Organize items in a hierarchical tree structure with faster navigation, clear edit flows, and URL-shareable filters.
        </p>
        {typeof totalItems === 'number' && (
          <p className="text-sm font-semibold text-slate-700">{totalItems} items</p>
        )}
      </div>
    </header>
  );
}

export function HierarchyTipsCard() {
  return (
    <aside className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="text-lg font-bold text-slate-900">Tips for faster item management</h2>
      <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-slate-600">
        <li>Create root items by leaving the parent field empty.</li>
        <li>Create child items by selecting a parent in the creation form.</li>
        <li>Use the URL search filter to share focused views.</li>
        <li>Deleting an item will cascade delete all children.</li>
      </ul>
    </aside>
  );
}
