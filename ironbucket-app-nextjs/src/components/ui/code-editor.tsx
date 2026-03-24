import React from 'react';
import { Textarea } from '@/components/ui/textarea';
import { cn } from '@/lib/utils';

type CodeEditorProps = React.TextareaHTMLAttributes<HTMLTextAreaElement> & {
  title?: string;
  language?: string;
  suggestions?: string[];
  onInsertSuggestion?: (value: string) => void;
};

export function CodeEditor({
  className,
  title = 'Policy source',
  language = 'json',
  value,
  suggestions,
  onInsertSuggestion,
  ...props
}: CodeEditorProps) {
  const content = String(value ?? '');
  const lines = Math.max(content.split('\n').length, 8);

  return (
    <div className="overflow-hidden rounded-[28px] border border-[color:var(--border)] bg-slate-950/85 shadow-[var(--shadow-card)]">
      <div className="flex items-center justify-between border-b border-white/10 px-4 py-3">
        <div>
          <p className="text-sm font-semibold text-white">{title}</p>
          <p className="text-xs uppercase tracking-[0.18em] text-slate-400">{language}</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex gap-2">
            <span className="h-3 w-3 rounded-full bg-rose-400" />
            <span className="h-3 w-3 rounded-full bg-amber-300" />
            <span className="h-3 w-3 rounded-full bg-emerald-400" />
          </div>
          {suggestions && suggestions.length > 0 ? (
            <div className="hidden max-w-[420px] flex-wrap gap-2 xl:flex">
              {suggestions.slice(0, 7).map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  className="rounded-full border border-white/20 bg-white/5 px-2 py-1 text-[10px] uppercase tracking-[0.12em] text-slate-300 transition hover:bg-white/10"
                  onClick={() => onInsertSuggestion?.(suggestion)}
                >
                  {suggestion}
                </button>
              ))}
            </div>
          ) : null}
        </div>
      </div>
      <div className="grid grid-cols-[auto_1fr]">
        <div className="border-r border-white/10 bg-white/5 px-3 py-4 text-right font-[family:var(--font-plex-mono)] text-xs leading-6 text-slate-500">
          {Array.from({ length: lines }, (_, index) => (
            <div key={index}>{index + 1}</div>
          ))}
        </div>
        <Textarea
          className={cn(
            'min-h-[260px] rounded-none border-0 bg-transparent font-[family:var(--font-plex-mono)] text-xs leading-6 text-slate-100 shadow-none focus:ring-0',
            className
          )}
          value={content}
          {...props}
        />
      </div>
    </div>
  );
}