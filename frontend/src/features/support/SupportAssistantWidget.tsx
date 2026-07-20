import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { MessageCircle, Send, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useSessionStore, useSessionRoles } from '@/stores/session';
import { cn } from '@/lib/utils';
import {
  executeSupportAction,
  streamSupportChat,
  type SupportActionButton,
} from './supportChatApi';

type TranscriptLine = {
  role: 'user' | 'assistant';
  text: string;
  actions?: SupportActionButton[];
};

/**
 * Global floating support copilot — agentic action buttons + scanner-safe layout.
 * Chat submissions go through {@link streamSupportChat}, which injects the active
 * route's {@link RouteKnowledgeRegistry} playbook as hidden system context.
 */
export function SupportAssistantWidget() {
  const location = useLocation();
  const roles = useSessionRoles();
  const authenticated = useSessionStore((s) => s.authenticated);
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [executing, setExecuting] = useState<string | null>(null);
  const [transcript, setTranscript] = useState<TranscriptLine[]>([]);
  const bottomRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [transcript, open]);

  useEffect(() => () => abortRef.current?.abort(), []);

  if (!authenticated) return null;

  // Showroom cart FAB uses the same corner (z-40); keep the copilot clear of it.
  const onShowroom = location.pathname.startsWith('/showroom');

  const send = async () => {
    const message = input.trim();
    if (!message || busy) return;
    setInput('');
    setTranscript((t) => [...t, { role: 'user', text: message }, { role: 'assistant', text: '', actions: [] }]);
    setBusy(true);
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;
    try {
      await streamSupportChat(
        message,
        roles,
        location.pathname + location.search,
        {
          onToken: (token) => {
            setTranscript((t) => {
              const copy = [...t];
              const last = copy[copy.length - 1];
              if (last?.role === 'assistant') {
                copy[copy.length - 1] = { ...last, text: last.text + token };
              }
              return copy;
            });
          },
          onAction: (action) => {
            setTranscript((t) => {
              const copy = [...t];
              const last = copy[copy.length - 1];
              if (last?.role === 'assistant') {
                const actions = [...(last.actions ?? []), action];
                copy[copy.length - 1] = { ...last, actions };
              }
              return copy;
            });
          },
        },
        ac.signal,
      );
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        setTranscript((t) => [
          ...t,
          {
            role: 'assistant',
            text: 'Could not reach the support assistant. Check your connection and try again.',
          },
        ]);
      }
    } finally {
      setBusy(false);
    }
  };

  const runAction = async (action: SupportActionButton, lineIndex: number) => {
    const key = `${lineIndex}:${action.action}`;
    setExecuting(key);
    try {
      const result = await executeSupportAction(action.action, action.params);
      const ok = result.ok !== false;
      setTranscript((t) => [
        ...t,
        {
          role: 'assistant',
          text: ok
            ? `Executed ${action.label}. ${summarizeResult(result)}`
            : `Could not execute ${action.action}: ${String(result.error ?? 'unknown error')}`,
        },
      ]);
    } catch (err) {
      setTranscript((t) => [
        ...t,
        {
          role: 'assistant',
          text: `Action failed: ${(err as Error).message}`,
        },
      ]);
    } finally {
      setExecuting(null);
    }
  };

  return (
    <>
      <button
        type="button"
        data-testid="support-assistant-fab"
        data-tour="support-assistant"
        aria-label="Open support assistant"
        onClick={() => setOpen(true)}
        className={cn(
          'fixed z-[60] flex h-14 w-14 items-center justify-center rounded-full',
          'bg-accent text-white shadow-elevated',
          'bottom-[max(1.25rem,env(safe-area-inset-bottom))]',
          onShowroom
            ? 'right-[max(5.75rem,calc(env(safe-area-inset-right)+4.5rem))]'
            : 'right-[max(1.25rem,env(safe-area-inset-right))]',
          'min-h-12 min-w-12 touch-manipulation',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/50',
          open && 'invisible',
        )}
      >
        <MessageCircle className="h-6 w-6" aria-hidden />
      </button>

      {open && (
        <div
          className={cn(
            'fixed z-[70] flex flex-col overflow-hidden border border-border bg-surface-raised shadow-elevated',
            'inset-x-0 bottom-0 max-h-[min(70dvh,32rem)] rounded-t-2xl',
            'sm:inset-auto sm:bottom-[max(1.25rem,env(safe-area-inset-bottom))] sm:right-[max(1.25rem,env(safe-area-inset-right))]',
            'sm:h-[28rem] sm:w-[22rem] sm:max-h-none sm:rounded-2xl',
          )}
          data-testid="support-assistant-panel"
          role="dialog"
          aria-label="Support assistant"
        >
          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <div>
              <p className="text-sm font-semibold text-text">Operations copilot</p>
              <p className="text-xs text-text-muted">
                {roles.join(', ') || 'Signed in'} · {location.pathname}
              </p>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              aria-label="Close assistant"
              data-testid="support-assistant-close"
              onClick={() => setOpen(false)}
            >
              <X className="h-4 w-4" />
            </Button>
          </div>

          <div className="min-h-0 flex-1 space-y-3 overflow-y-auto overscroll-contain px-4 py-3">
            {transcript.length === 0 && (
              <p className="text-sm text-text-muted">
                Ask how to receive, allocate, handle damage, or start a cycle count — answers stay
                within your role. Confirmable actions appear as buttons.
              </p>
            )}
            {transcript.map((line, i) => (
              <div key={`${line.role}-${i}`} className="space-y-2">
                <div
                  className={cn(
                    'rounded-lg px-3 py-2 text-sm whitespace-pre-wrap',
                    line.role === 'user'
                      ? 'ml-6 bg-accent/15 text-text'
                      : 'mr-4 bg-surface-overlay text-text',
                  )}
                  data-testid={line.role === 'assistant' ? 'support-assistant-reply' : undefined}
                >
                  {line.text || (busy && i === transcript.length - 1 ? '…' : '')}
                </div>
                {line.actions?.map((action) => (
                  <Button
                    key={`${i}-${action.action}-${action.label}`}
                    type="button"
                    size="sm"
                    className="ml-2"
                    data-testid="support-action-button"
                    data-action={action.action}
                    disabled={executing != null}
                    onClick={() => void runAction(action, i)}
                  >
                    {executing === `${i}:${action.action}` ? 'Running…' : action.label}
                  </Button>
                ))}
              </div>
            ))}
            <div ref={bottomRef} />
          </div>

          <form
            className="flex gap-2 border-t border-border p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
            onSubmit={(e) => {
              e.preventDefault();
              void send();
            }}
          >
            <input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Ask a question…"
              data-testid="support-assistant-input"
              className="min-h-12 flex-1 rounded-md border border-border bg-background px-3 text-base text-text sm:text-sm"
              enterKeyHint="send"
              autoComplete="off"
            />
            <Button
              type="submit"
              disabled={busy || !input.trim()}
              data-testid="support-assistant-send"
              className="min-h-12 min-w-12"
              aria-label="Send"
            >
              <Send className="h-4 w-4" />
            </Button>
          </form>
        </div>
      )}
    </>
  );
}

function summarizeResult(result: Record<string, unknown>): string {
  if (typeof result.cycleCountId === 'string') {
    return `Cycle count ${result.cycleCountId} is ready.`;
  }
  if (typeof result.waveId === 'string') {
    return `Wave ${result.waveId} released (${String(result.taskCount ?? 0)} tasks).`;
  }
  return '';
}
