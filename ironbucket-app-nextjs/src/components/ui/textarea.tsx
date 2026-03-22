import * as React from 'react';
import { cn } from '@/lib/utils';

const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(function Textarea(
  { className, ...props },
  ref
) {
  return (
    <textarea
      ref={ref}
      className={cn(
        'flex min-h-[140px] w-full rounded-3xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] px-4 py-3 text-sm text-[color:var(--foreground)] shadow-inner outline-none transition placeholder:text-[color:var(--muted-foreground)] focus:border-[color:var(--border-strong)] focus:ring-2 focus:ring-[color:var(--ring)]',
        className
      )}
      {...props}
    />
  );
});

export { Textarea };