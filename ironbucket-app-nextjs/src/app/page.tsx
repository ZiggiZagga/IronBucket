import { Suspense } from 'react';
import { HierarchicalItemsClient } from '@/features/hierarchical-items/HierarchicalItemsClient';
import { HierarchyHeader, HierarchyTipsCard } from '@/features/hierarchical-items/components/HierarchyShell';

export default function Page() {
  return (
    <section className="space-y-6">
      <HierarchyHeader />
      <Suspense fallback={<div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-600">Loading item workspace...</div>}>
        <HierarchicalItemsClient />
      </Suspense>
      <HierarchyTipsCard />
    </section>
  );
}
