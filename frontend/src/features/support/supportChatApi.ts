import { apiClient } from '@/api/client';
import {
  formatRouteKnowledgeForChat,
  resolveRouteKnowledge,
  type RouteKnowledge,
} from './RouteKnowledgeRegistry';

export interface SupportActionButton {
  type: 'action_button';
  action: string;
  label: string;
  params: Record<string, string>;
}

export interface SupportChatStreamHandlers {
  onToken: (token: string) => void;
  onAction?: (action: SupportActionButton) => void;
  onDone?: () => void;
  onError?: (err: Error) => void;
}

export type SupportPageContext = {
  pathname: string;
  title: string;
  purpose: string;
  reversals: string[];
  correlations: string[];
  flow: string[];
};

/** Build the structured page context sent alongside the user message. */
export function buildSupportPageContext(pathname: string): SupportPageContext | null {
  const knowledge = resolveRouteKnowledge(pathname);
  if (!knowledge) return null;
  return toPageContext(pathname, knowledge);
}

function toPageContext(pathname: string, knowledge: RouteKnowledge): SupportPageContext {
  return {
    pathname,
    title: knowledge.title,
    purpose: knowledge.purpose,
    reversals: knowledge.reversals,
    correlations: knowledge.correlations,
    flow: knowledge.flow,
  };
}

/**
 * Prefix the typed question with a hidden system-context block derived from the
 * active route's {@link RouteKnowledgeRegistry} entry.
 */
export function injectRouteContextIntoMessage(userMessage: string, pathname: string): string {
  const knowledge = resolveRouteKnowledge(pathname);
  const prefix = formatRouteKnowledgeForChat(pathname, knowledge);
  return `${prefix} ${userMessage.trim()}`;
}

/**
 * Streams POST /api/v1/support/chat with role + route context headers and
 * structured pageContext for RAG/LLM grounding.
 */
export async function streamSupportChat(
  message: string,
  roles: readonly string[],
  route: string,
  handlers: SupportChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  const base = (apiClient.defaults.baseURL ?? '').replace(/\/$/, '');
  const url = base ? `${base}/api/v1/support/chat` : '/api/v1/support/chat';
  const pathname = route.split('?')[0] || route;
  const pageContext = buildSupportPageContext(pathname);
  const enrichedMessage = injectRouteContextIntoMessage(message, pathname);

  const res = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'X-User-Roles': roles.join(','),
      'X-Current-Route': route,
    },
    body: JSON.stringify({
      message: enrichedMessage,
      pageContext,
    }),
    signal,
  });

  if (!res.ok || !res.body) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `Support chat failed (${res.status})`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parts = buffer.split('\n\n');
    buffer = parts.pop() ?? '';
    for (const block of parts) {
      const lines = block.split('\n');
      let event = 'message';
      let data = '';
      for (const line of lines) {
        if (line.startsWith('event:')) event = line.slice(6).trim();
        if (line.startsWith('data:')) {
          data += line.startsWith('data: ') ? line.slice(6) : line.slice(5);
        }
      }
      if (event === 'token' && data) {
        handlers.onToken(data);
      }
      if (event === 'action' && data) {
        const parsed = parseActionPayload(data);
        if (parsed) handlers.onAction?.(parsed);
      }
      if (event === 'done') {
        handlers.onDone?.();
      }
    }
  }
  handlers.onDone?.();
}

export async function executeSupportAction(
  action: string,
  params: Record<string, string>,
): Promise<Record<string, unknown>> {
  const { data } = await apiClient.post<Record<string, unknown>>('/api/v1/support/actions/execute', {
    action,
    params,
  });
  return data;
}

function parseActionPayload(raw: string): SupportActionButton | null {
  try {
    const json = JSON.parse(raw) as Partial<SupportActionButton>;
    if (json.type !== 'action_button' || !json.action) return null;
    return {
      type: 'action_button',
      action: json.action,
      label: json.label ?? json.action,
      params: (json.params as Record<string, string>) ?? {},
    };
  } catch {
    return null;
  }
}
