import '../styles/globals.css';
import React from 'react';
import { IBM_Plex_Mono, Space_Grotesk } from 'next/font/google';
import { AppShell } from '@/components/shell/app-shell';
import { Providers } from './providers';

const spaceGrotesk = Space_Grotesk({
  subsets: ['latin'],
  variable: '--font-space-grotesk'
});

const ibmPlexMono = IBM_Plex_Mono({
  subsets: ['latin'],
  weight: ['400', '500'],
  variable: '--font-plex-mono'
});

export const metadata = {
  title: 'IronBucket Control Plane',
  description: 'Operator-grade UI for IronBucket runtime proofs, governance, and storage workflows.'
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className={`${spaceGrotesk.variable} ${ibmPlexMono.variable} font-sans`}>
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
