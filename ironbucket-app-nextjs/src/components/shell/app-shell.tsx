'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Activity, FolderKanban, Gauge, Home, KeyRound, LibraryBig, ShieldCheck, Sparkles, Users } from 'lucide-react';
import * as DropdownMenu from '@radix-ui/react-dropdown-menu';
import { useMemo, useState } from 'react';
import { useAppSession } from '@/components/auth/session-provider';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { ThemeToggle } from '@/components/ui/theme-toggle';
import { useAppToast } from '@/components/ui/toast';
import { cn, getInitials } from '@/lib/utils';

type NavItem = {
  href: string;
  label: string;
  description: string;
  icon: typeof Home;
};

const navItems: NavItem[] = [
  { href: '/', label: 'Overview', description: 'Product surface and live proofs', icon: Home },
  { href: '/e2e-object-browser', label: 'Object Browser', description: 'Browse, upload, download, delete', icon: FolderKanban },
  { href: '/e2e-policy', label: 'Policy Studio', description: 'Evaluate and manage policy flows', icon: ShieldCheck },
  { href: '/e2e-audit', label: 'Audit Lens', description: 'Trace logs and audit windows', icon: Activity },
  { href: '/e2e-s3-methods', label: 'S3 Methods', description: 'Full method coverage and proof', icon: Gauge },
  { href: '/e2e-governance-methods', label: 'Governance', description: 'GraphQL governance scenarios', icon: LibraryBig }
];

function buildBreadcrumbs(pathname: string) {
  const parts = pathname.split('/').filter(Boolean);
  if (parts.length === 0) {
    return [{ label: 'Overview', href: '/' }];
  }

  return parts.map((part, index) => ({
    label: part
      .replace(/^e2e-/, '')
      .split('-')
      .map((segment) => segment.charAt(0).toUpperCase() + segment.slice(1))
      .join(' '),
    href: `/${parts.slice(0, index + 1).join('/')}`
  }));
}

function AuthMenu() {
  const { session, signIn, signOut, isAuthenticating } = useAppSession();
  const { pushToast } = useAppToast();
  const [open, setOpen] = useState(false);
  const [username, setUsername] = useState('alice');
  const [password, setPassword] = useState('aliceP@ss');
  const [error, setError] = useState('');

  if (session) {
    return (
      <DropdownMenu.Root>
        <DropdownMenu.Trigger asChild>
          <button className="flex items-center gap-3 rounded-full border border-[color:var(--border)] bg-[color:var(--panel)] px-3 py-2 text-left shadow-[var(--shadow-card)] outline-none transition hover:border-[color:var(--border-strong)]">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[color:var(--accent-soft)] text-sm font-bold text-[color:var(--accent-foreground)]">
              {getInitials(session.user.displayName)}
            </span>
            <span className="hidden min-w-0 md:block">
              <span className="block truncate text-sm font-semibold text-[color:var(--foreground)]">{session.user.displayName}</span>
              <span className="block truncate text-xs text-[color:var(--muted-foreground)]">{session.user.username}</span>
            </span>
          </button>
        </DropdownMenu.Trigger>
        <DropdownMenu.Portal>
          <DropdownMenu.Content sideOffset={10} className="z-50 w-72 rounded-[24px] border border-[color:var(--border)] bg-[color:var(--panel)] p-3 shadow-[var(--shadow-card)] backdrop-blur-xl">
            <div className="space-y-3 p-2">
              <div>
                <p className="text-sm font-semibold text-[color:var(--foreground)]">{session.user.displayName}</p>
                <p className="text-xs text-[color:var(--muted-foreground)]">{session.user.email ?? session.user.username}</p>
              </div>
              <div className="flex flex-wrap gap-2">
                {session.user.roles.slice(0, 4).map((role) => (
                  <Badge key={role} variant="neutral">{role}</Badge>
                ))}
              </div>
              <div className="rounded-2xl bg-[color:var(--panel-strong)] p-3 text-xs text-[color:var(--muted-foreground)]">
                Keycloak-backed operator session with local GraphQL token propagation.
              </div>
              <Button
                variant="secondary"
                className="w-full"
                type="button"
                onClick={() => {
                  signOut();
                  pushToast({
                    title: 'Session cleared',
                    description: 'Local operator session removed from this browser.',
                    variant: 'info'
                  });
                }}
              >
                Sign out
              </Button>
            </div>
          </DropdownMenu.Content>
        </DropdownMenu.Portal>
      </DropdownMenu.Root>
    );
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="secondary" type="button">
          <KeyRound className="h-4 w-4" />
          Sign in
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Operator Sign In</DialogTitle>
          <DialogDescription>
            Uses the existing Keycloak password-grant bridge so the UI stays compatible with the live runtime stack.
          </DialogDescription>
        </DialogHeader>
        <form
          className="mt-6 space-y-4"
          onSubmit={async (event) => {
            event.preventDefault();
            setError('');
            try {
              const nextSession = await signIn({ username, password });
              pushToast({
                title: 'Session ready',
                description: `Authenticated as ${nextSession.user.displayName}.`,
                variant: 'success'
              });
              setOpen(false);
            } catch (signInError) {
              const message = signInError instanceof Error ? signInError.message : String(signInError);
              setError(message);
              pushToast({
                title: 'Authentication failed',
                description: message,
                variant: 'error'
              });
            }
          }}
        >
          <div className="grid gap-4 md:grid-cols-2">
            <label className="space-y-2 text-sm font-medium text-[color:var(--foreground)]">
              Username
              <Input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="alice" />
            </label>
            <label className="space-y-2 text-sm font-medium text-[color:var(--foreground)]">
              Password
              <Input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="aliceP@ss" />
            </label>
          </div>
          <div className="rounded-2xl bg-[color:var(--panel-strong)] p-4 text-xs text-[color:var(--muted-foreground)]">
            Demo realm accounts already used by the runtime proofs: alice, bob, charlie, dana, eve.
          </div>
          {error ? <p className="text-sm font-medium text-rose-300">{error}</p> : null}
          <Button className="w-full" type="submit" disabled={isAuthenticating}>
            {isAuthenticating ? 'Signing in...' : 'Start operator session'}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const breadcrumbs = useMemo(() => buildBreadcrumbs(pathname), [pathname]);

  return (
    <div className="relative min-h-screen overflow-hidden bg-[color:var(--background)] text-[color:var(--foreground)]">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top_left,rgba(16,185,129,0.18),transparent_22%),radial-gradient(circle_at_top_right,rgba(56,189,248,0.16),transparent_25%),radial-gradient(circle_at_bottom,rgba(251,191,36,0.14),transparent_30%)]" />
      <div className="relative mx-auto flex min-h-screen max-w-[1680px] flex-col gap-4 p-4 lg:flex-row lg:p-6">
        <aside className="w-full shrink-0 lg:sticky lg:top-6 lg:h-[calc(100vh-3rem)] lg:w-[320px]">
          <Card className="flex h-full flex-col overflow-hidden border-white/10 bg-[linear-gradient(180deg,rgba(15,23,42,0.9),rgba(15,23,42,0.7))] text-white">
            <CardHeader className="border-b border-white/10 pb-5">
              <div className="flex items-center gap-3">
                <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-emerald-400/15 text-emerald-200">
                  <Sparkles className="h-6 w-6" />
                </div>
                <div>
                  <CardTitle className="text-white">IronBucket</CardTitle>
                  <CardDescription className="text-slate-300">Open-source storage operations cockpit</CardDescription>
                </div>
              </div>
            </CardHeader>
            <CardContent className="flex flex-1 flex-col gap-6 pt-6">
              <div className="space-y-2">
                <Badge variant="success">Live runtime aligned</Badge>
                <p className="text-sm text-slate-300">
                  Graphite-aligned UI with Keycloak session bootstrap, shared primitives, and observability proof routes.
                </p>
              </div>
              <nav className="space-y-2">
                {navItems.map((item) => {
                  const Icon = item.icon;
                  const active = pathname === item.href;
                  return (
                    <Link
                      key={item.href}
                      href={item.href}
                      className={cn(
                        'group flex items-start gap-3 rounded-[24px] px-4 py-3 transition',
                        active ? 'bg-white text-slate-950 shadow-[0_22px_55px_-35px_rgba(255,255,255,1)]' : 'text-slate-300 hover:bg-white/8 hover:text-white'
                      )}
                    >
                      <div className={cn('mt-0.5 rounded-2xl p-2', active ? 'bg-slate-100 text-slate-900' : 'bg-white/10 text-slate-200')}>
                        <Icon className="h-4 w-4" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-semibold">{item.label}</p>
                        <p className={cn('text-xs', active ? 'text-slate-600' : 'text-slate-400')}>{item.description}</p>
                      </div>
                    </Link>
                  );
                })}
              </nav>
              <div className="mt-auto rounded-[24px] border border-white/10 bg-white/5 p-4">
                <p className="text-xs uppercase tracking-[0.2em] text-slate-400">Proof focus</p>
                <p className="mt-2 text-sm font-semibold text-white">Alice and Bob mixed-user observability flow is already wired.</p>
                <p className="mt-2 text-sm text-slate-300">Use the scenario pages as both operator tools and proof capture surfaces.</p>
              </div>
            </CardContent>
          </Card>
        </aside>
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <header className="flex flex-col gap-4 rounded-[30px] border border-[color:var(--border)] bg-[color:var(--panel)] px-5 py-4 shadow-[var(--shadow-card)] backdrop-blur-xl md:flex-row md:items-center md:justify-between md:px-6">
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2 text-xs uppercase tracking-[0.18em] text-[color:var(--muted-foreground)]">
                {breadcrumbs.map((crumb, index) => (
                  <span key={crumb.href} className="flex items-center gap-2">
                    {index > 0 ? <span className="opacity-50">/</span> : null}
                    <Link href={crumb.href} className="transition hover:text-[color:var(--foreground)]">
                      {crumb.label}
                    </Link>
                  </span>
                ))}
              </div>
              <div>
                <h1 className="text-2xl font-semibold tracking-tight text-[color:var(--foreground)]">
                  {breadcrumbs[breadcrumbs.length - 1]?.label ?? 'Overview'}
                </h1>
                <p className="text-sm text-[color:var(--muted-foreground)]">
                  Unified operator shell with preserved Playwright-compatible route behavior.
                </p>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-3">
              <ThemeToggle />
              <AuthMenu />
            </div>
          </header>
          <main className="flex-1">{children}</main>
        </div>
      </div>
    </div>
  );
}