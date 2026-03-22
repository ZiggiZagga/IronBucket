import * as React from 'react';
import { cn } from '@/lib/utils';

const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, ...props },
  ref
) {
  return (
    <input
      ref={ref}
      className={cn(
        'flex h-11 w-full rounded-2xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] px-4 py-2 text-sm text-[color:var(--foreground)] shadow-inner outline-none transition placeholder:text-[color:var(--muted-foreground)] focus:border-[color:var(--border-strong)] focus:ring-2 focus:ring-[color:var(--ring)]',
        className
      )}
      {...props}
    />
  );
});

export { Input };