import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Camera, MessageCircle, Send, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { useToast } from '@/components/ui/Toast';
import { useSessionStore, useSessionRoles } from '@/stores/session';
import { cn } from '@/lib/utils';
import { compressImageForUpload } from '@/utils/imageCompression';
import {
  executeSupportAction,
  executeSupportActionDraft,
  fetchSupportInsight,
  streamSupportChat,
  type SupportActionButton,
  type SupportActionChip,
  type SupportActionDraft,
  type SupportStreamAction,
} from './supportChatApi';
import { SupportMarkdown } from './supportMarkdown';
import { usePageStateSnapshot } from './usePageStateSnapshot';
import { expandSidebarForPath, spotlightSelector } from './supportSpotlight';
import { usePreferencesStore, type TourId } from '@/stores/preferencesStore';
import {
  TRAINING_SCENARIOS,
  useTrainingSandboxStore,
  type TrainingScenarioId,
} from './trainingSandboxStore';

const TOUR_IDS = new Set<TourId>(['office', 'floor', 'receiving-to-allocation']);

type TranscriptLine = {
  role: 'user' | 'assistant';
  text: string;
  actions?: SupportStreamAction[];
  followUps?: string[];
  actionDraft?: SupportActionDraft | null;
  draftStatus?: 'pending' | 'approved' | 'cancelled' | 'failed';
};

/**
 * Global floating support copilot — Operations Instructor with action chips,
 * drafts, vision, proactive insights, and training entry points.
 */
export function SupportAssistantWidget() {
  const location = useLocation();
  const navigate = useNavigate();
  const roles = useSessionRoles();
  const pageState = usePageStateSnapshot();
  const authenticated = useSessionStore((s) => s.authenticated);
  const { toast } = useToast();
  const startScenario = useTrainingSandboxStore((s) => s.startScenario);
  const trainingActive = useTrainingSandboxStore((s) => s.isActive);
  const trainingRole = useTrainingSandboxStore((s) => s.activeRole);
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [executing, setExecuting] = useState<string | null>(null);
  const [transcript, setTranscript] = useState<TranscriptLine[]>([]);
  const [proactiveInsight, setProactiveInsight] = useState<string | null>(null);
  const [insightCollapsed, setInsightCollapsed] = useState(false);
  const [pendingImage, setPendingImage] = useState<{
    base64: string;
    mime: string;
    name: string;
    previewUrl: string;
  } | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [transcript, open]);

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    if (!authenticated) return;
    let cancelled = false;
    const route = location.pathname + location.search;
    void fetchSupportInsight(route)
      .then((insight) => {
        if (!cancelled) {
          setProactiveInsight(insight);
          setInsightCollapsed(false);
        }
      })
      .catch(() => {
        if (!cancelled) setProactiveInsight(null);
      });
    return () => {
      cancelled = true;
    };
  }, [authenticated, location.pathname, location.search]);

  if (!authenticated) return null;

  const onShowroom = location.pathname.startsWith('/showroom');

  const send = async (rawMessage?: string) => {
    const message = (rawMessage ?? input).trim();
    if ((!message && !pendingImage) || busy) return;
    if (!rawMessage) setInput('');
    const image = pendingImage;
    setPendingImage(null);
    const userText = message || (image ? `(Attached photo: ${image.name})` : '');
    setTranscript((t) => [
      ...t,
      { role: 'user', text: userText },
      { role: 'assistant', text: '', actions: [], followUps: [] },
    ]);
    setBusy(true);
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;
    try {
      await streamSupportChat(
        message || 'Please inspect this warehouse photo and tell me the safest next steps.',
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
          onDone: (payload) => {
            if (!payload) return;
            if (payload.proactiveInsight) {
              setProactiveInsight(payload.proactiveInsight);
            }
            setTranscript((t) => {
              const copy = [...t];
              const last = copy[copy.length - 1];
              if (last?.role !== 'assistant') return t;
              const followUps = payload.followUpQuestions ?? last.followUps ?? [];
              let actions = last.actions ?? [];
              if (payload.actionChips?.length) {
                const keys = new Set(actions.map((a) => `${a.type}:${a.action}:${a.label}`));
                for (const chip of payload.actionChips) {
                  const key = `${chip.type}:${chip.action}:${chip.label}`;
                  if (!keys.has(key)) {
                    actions = [...actions, chip];
                    keys.add(key);
                  }
                }
              }
              const actionDraft = payload.actionDraft ?? last.actionDraft ?? null;
              if (payload.replyMarkdown && !last.text.trim()) {
                copy[copy.length - 1] = {
                  ...last,
                  text: payload.replyMarkdown,
                  actions,
                  followUps,
                  actionDraft,
                  draftStatus: actionDraft ? 'pending' : undefined,
                };
              } else {
                copy[copy.length - 1] = {
                  ...last,
                  actions,
                  followUps,
                  actionDraft,
                  draftStatus: actionDraft ? 'pending' : last.draftStatus,
                };
              }
              return copy;
            });
          },
        },
        ac.signal,
        {
          // Includes recentBreadcrumbs (last 5 UI actions) for temporal coaching.
          pageState: {
            ...pageState,
            recentBreadcrumbs: pageState.recentBreadcrumbs ?? [],
          },
          userRoles: roles,
          imageBase64: image?.base64 ?? null,
          base64Image: image?.base64 ?? null,
          imageMimeType: image?.mime ?? null,
        },
      );
    } catch (err) {
      if ((err as Error).name !== 'AbortError') {
        setTranscript((t) => {
          const copy = [...t];
          const last = copy[copy.length - 1];
          const msg =
            'Could not reach the support assistant. Check your connection and try again.';
          if (last?.role === 'assistant') {
            copy[copy.length - 1] = { ...last, text: msg };
            return copy;
          }
          return [...t, { role: 'assistant', text: msg }];
        });
      }
    } finally {
      setBusy(false);
    }
  };

  const onPickImage = async (file: File | null) => {
    if (!file) return;
    try {
      const compressed = await compressImageForUpload(file, { maxBytes: 180_000 });
      const dataUrl = await readAsDataUrl(compressed);
      const comma = dataUrl.indexOf(',');
      const meta = dataUrl.slice(0, comma);
      const mimeMatch = /data:([^;]+)/.exec(meta);
      const mime = mimeMatch?.[1] ?? compressed.type ?? 'image/jpeg';
      setPendingImage({
        base64: dataUrl.slice(comma + 1),
        mime,
        name: file.name || 'capture.jpg',
        previewUrl: dataUrl,
      });
    } catch {
      toast('Could not prepare that photo. Try a smaller image.', { tone: 'danger' });
    }
  };

  const runPlatformAction = async (action: SupportActionButton, lineIndex: number) => {
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
      if (ok) toast(`${action.label} completed.`, { tone: 'success' });
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

  const approveDraft = async (lineIndex: number, draft: SupportActionDraft) => {
    setExecuting(`draft:${lineIndex}`);
    // Flight simulator: validate Action Drafts without touching the live ledger.
    if (useTrainingSandboxStore.getState().isTrainingMode()) {
      const role = useTrainingSandboxStore.getState().activeRole ?? 'TRAINING';
      useTrainingSandboxStore.getState().recordBlockedMutation(
        draft.httpMethod || 'POST',
        draft.targetEndpoint || '/simulator',
      );
      setTranscript((t) => {
        const copy = [...t];
        const line = copy[lineIndex];
        if (line?.role === 'assistant') {
          copy[lineIndex] = { ...line, draftStatus: 'approved' };
        }
        copy.push({
          role: 'assistant',
          text: '✅ Training scenario completed successfully. Ledger untouched.',
        });
        return copy;
      });
      toast(
        `[SIMULATOR: ${role}] Action validated and executed! Ledger untouched.`,
        { tone: 'success' },
      );
      setExecuting(null);
      return;
    }
    try {
      const result = await executeSupportActionDraft(draft);
      const ok = result.ok !== false;
      setTranscript((t) => {
        const copy = [...t];
        const line = copy[lineIndex];
        if (line?.role === 'assistant') {
          copy[lineIndex] = { ...line, draftStatus: ok ? 'approved' : 'failed' };
        }
        if (ok) {
          copy.push({
            role: 'assistant',
            text: `✓ Executed: ${draft.title}. ${String(result.message ?? summarizeResult(result))}`.trim(),
          });
        } else {
          copy.push({
            role: 'assistant',
            text: `Could not execute draft: ${String(result.error ?? 'unknown error')}`,
          });
        }
        return copy;
      });
      if (ok) {
        toast(
          result.navigational
            ? `Approved: ${draft.title}. Finish with the highlighted on-screen button.`
            : `✓ Executed: ${draft.title}`,
          { tone: 'success' },
        );
      } else {
        toast(String(result.error ?? 'Draft could not be executed.'), { tone: 'danger' });
      }
    } catch (err) {
      const message = (err as Error).message;
      setTranscript((t) => {
        const copy = [...t];
        const line = copy[lineIndex];
        if (line?.role === 'assistant') {
          copy[lineIndex] = { ...line, draftStatus: 'failed' };
        }
        copy.push({ role: 'assistant', text: `Could not execute draft: ${message}` });
        return copy;
      });
      toast(message, { tone: 'danger' });
    } finally {
      setExecuting(null);
    }
  };

  const runChip = (chip: SupportActionChip) => {
    const target = chip.target || chip.params?.target || '';
    if (chip.action === 'NAVIGATE' && target) {
      expandSidebarForPath(target);
      navigate(target);
      return;
    }
    if (chip.action === 'SPOTLIGHT' && target) {
      spotlightSelector(target);
      return;
    }
    if (chip.action === 'START_TOUR' && target) {
      if (TOUR_IDS.has(target as TourId)) {
        usePreferencesStore.getState().startTour(target as TourId);
        setTranscript((t) => [
          ...t,
          {
            role: 'assistant',
            text: `Starting the **${target}** walkthrough. Follow the highlighted steps on screen.`,
          },
        ]);
      } else {
        setTranscript((t) => [
          ...t,
          {
            role: 'assistant',
            text: `I do not recognize tour "${target}". Available tours: office, floor, receiving-to-allocation.`,
          },
        ]);
      }
    }
  };

  const askAboutInsight = () => {
    setInsightCollapsed(true);
    void send('How do I resolve these holds?');
  };

  const openInsight = () => {
    setOpen(true);
    if (proactiveInsight?.toLowerCase().includes('credit') || proactiveInsight?.toLowerCase().includes('backorder')) {
      navigate('/sales-orders');
    } else if (proactiveInsight?.toLowerCase().includes('picking') || proactiveInsight?.toLowerCase().includes('wave')) {
      navigate('/fulfillment');
    }
    // Defer so the panel mounts before the automatic resolve query streams.
    window.setTimeout(() => {
      setInsightCollapsed(true);
      void send('How do I resolve these holds?');
    }, 0);
  };

  return (
    <>
      {!open && proactiveInsight && !insightCollapsed ? (
        <button
          type="button"
          data-testid="support-proactive-insight"
          onClick={openInsight}
          className={cn(
            'fixed z-[61] max-w-[min(20rem,calc(100vw-5.5rem))] rounded-full border border-amber-500/50',
            'bg-amber-500/15 px-3 py-2 text-left text-xs text-text shadow-elevated',
            'bottom-[max(5.25rem,calc(env(safe-area-inset-bottom)+4.25rem))]',
            onShowroom
              ? 'right-[max(5.75rem,calc(env(safe-area-inset-right)+4.5rem))]'
              : 'right-[max(1.25rem,env(safe-area-inset-right))]',
          )}
        >
          {proactiveInsight.startsWith('💡') ? proactiveInsight : `💡 ${proactiveInsight}`}
        </button>
      ) : null}

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
          {trainingActive ? (
            <div
              className={cn(
                'px-3 py-2 text-center text-xs font-semibold tracking-wide text-black',
                'bg-[repeating-linear-gradient(45deg,#f59e0b_0px,#f59e0b_12px,#111827_12px,#111827_24px)]',
              )}
              data-testid="support-training-simulator-header"
              role="status"
            >
              <span className="inline-block rounded bg-amber-400/95 px-2 py-0.5">
                ⚠️ TRAINING SIMULATOR ACTIVE
                {trainingRole ? `: ${trainingRole}` : ''} — NO DATA WILL BE SAVED
              </span>
            </div>
          ) : null}

          <div className="flex items-center justify-between border-b border-border px-4 py-3">
            <div>
              <p className="text-sm font-semibold text-text">Operations copilot</p>
              <p className="text-xs text-text-muted">
                {roles.join(', ') || 'Signed in'} · {location.pathname}
                {pageState.networkPhase !== 'online' ? ` · ${pageState.networkPhase}` : ''}
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
              <div className="space-y-2 text-sm text-text-muted">
                <p>
                  Ask how to receive, allocate, handle damage, or reverse a mistake. Attach a photo
                  of a label or damaged item for visual help.
                </p>
                <div className="flex flex-wrap gap-2">
                  {(Object.keys(TRAINING_SCENARIOS) as TrainingScenarioId[]).map((id) => (
                    <Button
                      key={id}
                      type="button"
                      size="sm"
                      variant="secondary"
                      className="rounded-full"
                      data-testid={`support-training-${id}`}
                      onClick={() => {
                        startScenario(id);
                        setTranscript((t) => [
                          ...t,
                          {
                            role: 'assistant',
                            text: `Training mode: **${TRAINING_SCENARIOS[id].title}**. Live stock will not change.`,
                          },
                        ]);
                      }}
                    >
                      Practice: {TRAINING_SCENARIOS[id].title}
                    </Button>
                  ))}
                </div>
              </div>
            )}
            {transcript.map((line, i) => (
              <div key={`${line.role}-${i}`} className="space-y-2">
                <div
                  className={cn(
                    'rounded-lg px-3 py-2',
                    line.role === 'user'
                      ? 'ml-6 bg-accent/15 text-text text-sm whitespace-pre-wrap'
                      : 'mr-4 bg-surface-overlay text-text',
                  )}
                  data-testid={line.role === 'assistant' ? 'support-assistant-reply' : undefined}
                >
                  {line.role === 'assistant' ? (
                    line.text ? (
                      <SupportMarkdown text={line.text} />
                    ) : busy && i === transcript.length - 1 ? (
                      <span className="text-sm text-text-muted">…</span>
                    ) : null
                  ) : (
                    line.text
                  )}
                </div>

                {line.actionDraft && line.draftStatus !== 'cancelled' ? (
                  <div
                    className={cn(
                      'ml-2 rounded-lg border border-accent/30 bg-accent/10 p-3',
                      line.draftStatus === 'approved' && 'border-success/40 bg-success/10',
                    )}
                    data-testid="support-action-draft"
                  >
                    <p className="text-sm font-semibold text-text">{line.actionDraft.title}</p>
                    <p className="mt-1 text-sm text-text-muted">{line.actionDraft.description}</p>
                    {line.draftStatus === 'approved' ? (
                      <p className="mt-2 text-xs font-semibold text-success" data-testid="support-draft-approved">
                        ✓ Executed
                      </p>
                    ) : line.draftStatus === 'failed' ? (
                      <p className="mt-2 text-xs font-semibold text-danger" data-testid="support-draft-failed">
                        Could not execute — check the zone or try the on-screen button.
                      </p>
                    ) : (
                      <div className="mt-3 flex flex-wrap gap-2">
                        <Button
                          type="button"
                          size="sm"
                          data-testid="support-draft-approve"
                          disabled={executing != null}
                          onClick={() => void approveDraft(i, line.actionDraft!)}
                        >
                          {executing === `draft:${i}` ? 'Working…' : 'Approve & Execute'}
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="secondary"
                          data-testid="support-draft-cancel"
                          onClick={() =>
                            setTranscript((t) => {
                              const copy = [...t];
                              const cur = copy[i];
                              if (cur?.role === 'assistant') {
                                copy[i] = { ...cur, draftStatus: 'cancelled', actionDraft: null };
                              }
                              return copy;
                            })
                          }
                        >
                          Dismiss
                        </Button>
                      </div>
                    )}
                  </div>
                ) : null}

                {line.role === 'assistant' && line.text.startsWith('✓ Executed') ? (
                  <span
                    className="ml-2 inline-flex rounded-full border border-success/40 bg-success/15 px-2.5 py-1 text-xs font-semibold text-success"
                    data-testid="support-draft-executed-badge"
                  >
                    ✓ Executed
                  </span>
                ) : null}

                {line.actions?.map((action) =>
                  action.type === 'action_chip' ? (
                    <Button
                      key={`${i}-chip-${action.action}-${action.label}`}
                      type="button"
                      size="sm"
                      variant="secondary"
                      className="ml-2 min-h-11 rounded-full px-4 touch-manipulation"
                      data-testid="support-action-chip"
                      data-action={action.action}
                      data-target={action.target}
                      disabled={busy}
                      onClick={() => runChip(action)}
                    >
                      {action.label}
                    </Button>
                  ) : (
                    <Button
                      key={`${i}-${action.action}-${action.label}`}
                      type="button"
                      size="sm"
                      className="ml-2 min-h-11 touch-manipulation"
                      data-testid="support-action-button"
                      data-action={action.action}
                      disabled={executing != null}
                      onClick={() => void runPlatformAction(action, i)}
                    >
                      {executing === `${i}:${action.action}` ? 'Running…' : action.label}
                    </Button>
                  ),
                )}
                {line.followUps && line.followUps.length > 0 && (
                  <div className="ml-2 flex flex-wrap gap-2" data-testid="support-follow-ups">
                    {line.followUps.map((q) => (
                      <button
                        key={q}
                        type="button"
                        data-testid="support-follow-up"
                        disabled={busy}
                        className={cn(
                          'min-h-10 rounded-full border border-border bg-surface px-3 py-1.5',
                          'text-left text-xs text-text touch-manipulation',
                          'hover:border-accent/40 hover:bg-accent/5',
                          'disabled:opacity-50',
                        )}
                        onClick={() => void send(q)}
                      >
                        {q}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
            <div ref={bottomRef} />
          </div>

          {pendingImage ? (
            <div
              className="flex items-center justify-between gap-2 border-t border-border px-3 py-2 text-xs text-text-muted"
              data-testid="support-image-pending"
            >
              <div className="flex min-w-0 items-center gap-2">
                <img
                  src={pendingImage.previewUrl}
                  alt=""
                  className="h-12 w-12 shrink-0 rounded-md border border-border object-cover"
                  data-testid="support-image-thumbnail"
                />
                <span className="truncate">Photo ready: {pendingImage.name}</span>
              </div>
              <Button type="button" size="sm" variant="ghost" onClick={() => setPendingImage(null)}>
                Remove
              </Button>
            </div>
          ) : null}

          {proactiveInsight && !insightCollapsed ? (
            <div className="border-t border-border px-3 pt-2">
              <button
                type="button"
                data-testid="support-proactive-insight-panel"
                disabled={busy}
                onClick={askAboutInsight}
                className={cn(
                  'w-full rounded-full border border-amber-500/50 bg-amber-500/15 px-3 py-2',
                  'text-left text-xs text-text shadow-sm',
                  'hover:bg-amber-500/25 disabled:opacity-50',
                )}
              >
                {proactiveInsight.startsWith('💡') ? proactiveInsight : `💡 ${proactiveInsight}`}
              </button>
            </div>
          ) : null}

          <form
            className="flex gap-2 border-t border-border p-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
            onSubmit={(e) => {
              e.preventDefault();
              void send();
            }}
          >
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              capture="environment"
              className="hidden"
              data-testid="support-camera-input"
              onChange={(e) => {
                const file = e.target.files?.[0] ?? null;
                void onPickImage(file);
                e.target.value = '';
              }}
            />
            <Button
              type="button"
              variant="secondary"
              className="min-h-12 min-w-12"
              aria-label="Attach photo"
              data-testid="support-camera-button"
              disabled={busy}
              onClick={() => fileRef.current?.click()}
            >
              <Camera className="h-4 w-4" />
            </Button>
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
              disabled={busy || (!input.trim() && !pendingImage)}
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

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error ?? new Error('read failed'));
    reader.readAsDataURL(file);
  });
}
