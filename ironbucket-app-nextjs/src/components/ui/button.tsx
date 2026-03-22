import * as React from 'react';
import { Slot } from '@radix-ui/react-slot';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-2xl text-sm font-semibold transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--ring)] disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        default: 'bg-[color:var(--accent)] px-4 py-2.5 text-[color:var(--accent-foreground)] shadow-[0_18px_40px_-22px_rgba(16,185,129,0.7)] hover:-translate-y-0.5 hover:shadow-[0_24px_50px_-24px_rgba(16,185,129,0.8)]',
        secondary: 'bg-[color:var(--panel)] px-4 py-2.5 text-[color:var(--foreground)] ring-1 ring-[color:var(--border-strong)] hover:bg-[color:var(--panel-strong)]',
        ghost: 'px-3 py-2 text-[color:var(--muted-foreground)] hover:bg-[color:var(--panel)] hover:text-[color:var(--foreground)]',
        danger: 'bg-[color:var(--danger)] px-4 py-2.5 text-white shadow-[0_18px_40px_-22px_rgba(239,68,68,0.8)] hover:-translate-y-0.5'
      },
      size: {
        default: 'h-11',
        sm: 'h-9 rounded-xl px-3 text-xs',
        lg: 'h-12 px-5 text-sm'
      }
    },
    defaultVariants: {
      variant: 'default',
      size: 'default'
    }
  }
);

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { className, variant, size, asChild = false, ...props },
  ref
) {
  const Comp = asChild ? Slot : 'button';
  return <Comp className={cn(buttonVariants({ variant, size }), className)} ref={ref} {...props} />;
});

export { Button, buttonVariants };