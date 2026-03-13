import '../styles/globals.css';
import React from 'react';
import { Providers } from './providers';

export const metadata = {
  title: 'Graphite UI',
  description: 'Next.js UI for Graphite-Forge'
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <div className="min-h-screen flex flex-col bg-[radial-gradient(circle_at_top,_rgba(8,145,178,0.15),_transparent_60%),linear-gradient(180deg,#f8fafc_0%,#f1f5f9_100%)]">
            <header className="border-b border-slate-200/70 bg-white/80 shadow-sm backdrop-blur">
              <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-4">
                <p className="text-lg font-black tracking-tight text-slate-900">Graphite UI</p>
                <p className="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-semibold text-slate-600">UX Refresh</p>
              </div>
            </header>
            <main className="flex-1 max-w-6xl mx-auto p-6 w-full">{children}</main>
            <footer className="mt-8 border-t border-slate-200/70 bg-white/80 py-4 backdrop-blur">
              <div className="max-w-6xl mx-auto px-4 text-sm text-slate-600">© Graphite Forge</div>
            </footer>
          </div>
        </Providers>
      </body>
    </html>
  );
}
