import * as React from 'react';
import { cn } from '@/lib/utils';

export function FormField({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn('space-y-2', className)} {...props} />;
}

export function FormLabel({ className, ...props }: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn('text-sm font-medium text-[color:var(--foreground)]', className)} {...props} />;
}

export function FormHint({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-xs text-[color:var(--muted-foreground)]', className)} {...props} />;
}

export function FormError({ className, ...props }: React.HTMLAttributes<HTMLParagraphElement>) {
  return <p className={cn('text-xs font-semibold text-rose-400', className)} {...props} />;
}
