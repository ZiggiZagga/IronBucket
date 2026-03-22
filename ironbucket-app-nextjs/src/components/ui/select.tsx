import * as React from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';

const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { className, children, ...props },
  ref
) {
  return (
    <div className="relative">
      <select
        ref={ref}
        className={cn(
          'flex h-11 w-full appearance-none rounded-2xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] px-4 py-2 pr-10 text-sm text-[color:var(--foreground)] outline-none transition focus:border-[color:var(--border-strong)] focus:ring-2 focus:ring-[color:var(--ring)]',
          className
        )}
        {...props}
      >
        {children}
      </select>
      <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--muted-foreground)]" />
    </div>
  );
});

export { Select };