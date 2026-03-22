'use client';

import React, { createContext, useContext, useMemo, useState } from 'react';
import * as ToastPrimitive from '@radix-ui/react-toast';
import { CheckCircle2, CircleAlert, Info } from 'lucide-react';
import { cn } from '@/lib/utils';

type ToastVariant = 'info' | 'success' | 'error';

type ToastRecord = {
  id: string;
  title: string;
  description?: string;
  variant: ToastVariant;
};

type ToastContextValue = {
  pushToast: (toast: Omit<ToastRecord, 'id'>) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

function getToastIcon(variant: ToastVariant) {
  if (variant === 'success') {
    return CheckCircle2;
  }
  if (variant === 'error') {
    return CircleAlert;
  }
  return Info;
}

export function AppToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<ToastRecord[]>([]);

  const value = useMemo<ToastContextValue>(
    () => ({
      pushToast: (toast) => {
        const id = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
        setToasts((current) => [...current, { ...toast, id }]);
      }
    }),
    []
  );

  return (
    <ToastContext.Provider value={value}>
      <ToastPrimitive.Provider swipeDirection="right">
        {children}
        {toasts.map((toast) => {
          const Icon = getToastIcon(toast.variant);
          return (
            <ToastPrimitive.Root
              key={toast.id}
              open
              duration={4000}
              onOpenChange={(open) => {
                if (!open) {
                  setToasts((current) => current.filter((item) => item.id !== toast.id));
                }
              }}
              className={cn(
                'grid w-[360px] grid-cols-[auto_1fr] gap-3 rounded-3xl border p-4 shadow-[var(--shadow-card)] backdrop-blur-xl',
                toast.variant === 'error'
                  ? 'border-rose-400/30 bg-rose-950/70 text-rose-50'
                  : toast.variant === 'success'
                    ? 'border-emerald-400/30 bg-emerald-950/70 text-emerald-50'
                    : 'border-[color:var(--border)] bg-[color:var(--panel)] text-[color:var(--foreground)]'
              )}
            >
              <div className="mt-0.5 rounded-2xl bg-white/10 p-2">
                <Icon className="h-4 w-4" />
              </div>
              <div className="space-y-1">
                <ToastPrimitive.Title className="text-sm font-semibold">{toast.title}</ToastPrimitive.Title>
                {toast.description ? (
                  <ToastPrimitive.Description className="text-xs opacity-80">{toast.description}</ToastPrimitive.Description>
                ) : null}
              </div>
            </ToastPrimitive.Root>
          );
        })}
        <ToastPrimitive.Viewport className="fixed bottom-4 right-4 z-[60] flex max-w-[100vw] flex-col gap-3 outline-none" />
      </ToastPrimitive.Provider>
    </ToastContext.Provider>
  );
}

export function useAppToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useAppToast must be used within AppToastProvider');
  }

  return context;
}