import Link from 'next/link';
import { Suspense } from 'react';
import { ArrowRight, Gauge, ShieldCheck, Sparkles, Users } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { HierarchicalItemsClient } from '@/features/hierarchical-items/HierarchicalItemsClient';
import { HierarchyHeader, HierarchyTipsCard } from '@/features/hierarchical-items/components/HierarchyShell';

const focusCards = [
  {
    title: 'Operator-first shell',
    description: 'Unified navigation, top bar, theme system, and Keycloak-backed local session bootstrap.',
    icon: Sparkles
  },
  {
    title: 'Runtime evidence',
    description: 'Mixed-user observability and performance proof routes are promoted as first-class product surfaces.',
    icon: Gauge
  },
  {
    title: 'Governance workflows',
    description: 'Policy, audit, and identity surfaces stay aligned with the existing GraphQL and Playwright coverage.',
    icon: ShieldCheck
  }
];

const routeCards = [
  {
    href: '/tenants',
    title: 'Tenant Management',
    copy: 'Create, edit, and remove tenants while assigning users and roles in a shared admin workflow.'
  },
  {
    href: '/policies/alice',
    title: 'Policy Engine',
    copy: 'Manage tenant policies with editor, validation, dry-run simulation, version history and GitOps actions.'
  },
  {
    href: '/e2e-object-browser',
    title: 'Object Browser',
    copy: 'Browse buckets, upload files, and verify delete/download flows with live runtime data.'
  },
  {
    href: '/e2e-policy',
    title: 'Policy Studio',
    copy: 'Run policy CRUD coverage and validate evaluation results in a more operator-grade surface.'
  },
  {
    href: '/e2e-audit',
    title: 'Audit Lens',
    copy: 'Inspect audit and stats flows backed by Loki ingestion and observability checks.'
  }
];

export default function Page() {
  return (
    <section className="space-y-8">
      <Card className="overflow-hidden border-white/10 bg-[linear-gradient(135deg,rgba(15,23,42,0.96),rgba(10,67,73,0.82)_48%,rgba(11,18,32,0.96))] text-white">
        <CardContent className="grid gap-8 px-6 py-8 md:px-8 lg:grid-cols-[1.2fr_0.8fr] lg:py-10">
          <div className="space-y-5">
            <Badge variant="success">Last sprint platform pass</Badge>
            <div className="space-y-4">
              <h1 className="max-w-3xl text-4xl font-semibold tracking-tight md:text-5xl">
                IronBucket now looks like a control plane, not a demo page.
              </h1>
              <p className="max-w-2xl text-base text-slate-300 md:text-lg">
                The shell, theming, auth entry point, and evidence routes are aligned so the UI can present real storage workflows and runtime proof in one place.
              </p>
            </div>
            <div className="flex flex-wrap gap-3">
              <Button asChild className="min-w-[180px]">
                <Link href="/e2e-object-browser">
                  Open Object Browser
                  <ArrowRight className="h-4 w-4" />
                </Link>
              </Button>
              <Button asChild variant="secondary">
                <Link href="/e2e-policy">Enter Policy Studio</Link>
              </Button>
            </div>
          </div>
          <div className="grid gap-4 md:grid-cols-3 lg:grid-cols-1">
            <div className="rounded-[26px] border border-white/10 bg-white/8 p-5 backdrop-blur">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-400">Proof status</p>
              <p className="mt-3 text-3xl font-semibold">Green</p>
              <p className="mt-2 text-sm text-slate-300">Observability infra gate, phase-2 performance proof, and mixed-user scenario are already validated.</p>
            </div>
            <div className="rounded-[26px] border border-white/10 bg-white/8 p-5 backdrop-blur">
              <p className="text-xs uppercase tracking-[0.2em] text-slate-400">Actors</p>
              <p className="mt-3 flex items-center gap-2 text-3xl font-semibold"><Users className="h-6 w-6" /> Alice + Bob</p>
              <p className="mt-2 text-sm text-slate-300">Concurrent scenario coverage remains the baseline for UI evidence and performance assertions.</p>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-3">
        {focusCards.map((card) => {
          const Icon = card.icon;
          return (
            <Card key={card.title}>
              <CardHeader>
                <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[color:var(--accent-soft)] text-[color:var(--accent)]">
                  <Icon className="h-5 w-5" />
                </div>
                <CardTitle>{card.title}</CardTitle>
                <CardDescription>{card.description}</CardDescription>
              </CardHeader>
            </Card>
          );
        })}
      </div>

      <div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
        <div className="space-y-6">
          <HierarchyHeader />
          <div className="grid gap-4 md:grid-cols-3">
            {routeCards.map((route) => (
              <Card key={route.href} className="transition hover:-translate-y-1">
                <CardHeader>
                  <CardTitle>{route.title}</CardTitle>
                  <CardDescription>{route.copy}</CardDescription>
                </CardHeader>
                <CardContent>
                  <Link href={route.href} className="inline-flex items-center gap-2 text-sm font-semibold text-[color:var(--accent)]">
                    Open route
                    <ArrowRight className="h-4 w-4" />
                  </Link>
                </CardContent>
              </Card>
            ))}
          </div>
          <Suspense fallback={<div className="rounded-[28px] border border-[color:var(--border)] bg-[color:var(--panel)] p-6 text-sm text-[color:var(--muted-foreground)]">Loading item workspace...</div>}>
            <HierarchicalItemsClient />
          </Suspense>
        </div>
        <div className="space-y-4">
          <HierarchyTipsCard />
          <Card className="overflow-hidden border-white/10 bg-[linear-gradient(180deg,rgba(8,47,73,0.95),rgba(3,7,18,0.95))] text-white">
            <CardHeader>
              <CardTitle className="text-white">Evidence-driven UI</CardTitle>
              <CardDescription className="text-slate-300">
                The goal is not just a prettier screen. The product surface should help prove runtime behavior, capture screenshots, and guide scenario execution.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-sm text-slate-200">
              <p>Object flows stay selector-compatible for Playwright.</p>
              <p>Keycloak login remains aligned with the current backend path.</p>
              <p>Policy and audit routes are ready to adopt the same shared primitives.</p>
            </CardContent>
          </Card>
        </div>
      </div>
    </section>
  );
}
