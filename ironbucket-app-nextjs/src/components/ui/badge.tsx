import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const badgeVariants = cva('inline-flex items-center rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.18em]', {
  variants: {
    variant: {
      default: 'bg-[color:var(--accent-soft)] text-[color:var(--accent-foreground)]',
      neutral: 'bg-[color:var(--panel-strong)] text-[color:var(--muted-foreground)] ring-1 ring-[color:var(--border)]',
      success: 'bg-emerald-500/15 text-emerald-200 ring-1 ring-emerald-400/30',
      warning: 'bg-amber-500/15 text-amber-200 ring-1 ring-amber-400/30'
    }
  },
  defaultVariants: {
    variant: 'default'
  }
});

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof badgeVariants> {}

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}