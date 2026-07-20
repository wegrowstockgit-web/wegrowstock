import { apiClient } from '@/api/client';
import {
  formatRouteKnowledgeForChat,
  knowledgeContextKey,
  resolveKnowledgeContext,
  type RouteKnowledge,
  type RouteKnowledgeComponent,
} from './RouteKnowledgeRegistry';
import type { PageStateSnapshot } from './usePageStateSnapshot';

export interface SupportActionButton {
  type: 'action_button';
  action: string;
  label: string;
  params: Record<string, string>;
  target?: string;
}

export interface SupportActionChip {
  type: 'action_chip';
  action: 'NAVIGATE' | 'SPOTLIGHT' | string;
  label: string;
  target: string;
  params: Record<string, string>;
}

export type SupportStreamAction = SupportActionButton | SupportActionChip;

export interface SupportChatDonePayload {
  ok?: boolean;
  replyMarkdown?: string;
  followUpQuestions?: string[];
  actionChips?: SupportStreamAction[];
}

export interface SupportChatStreamHandlers {
  onToken: (token: string) => void;
  onAction?: (action: SupportStreamAction) => void;
  onDone?: (payload?: SupportChatDonePayload) => void;
  onError?: (err: Error) => void;
}

export type SupportPageContextComponent = {
  name: string;
  description: string;
  dataOrigin: string;
  columns?: { name: string; purpose: string }[];
  statuses?: Record<string, string>;
};

export type SupportPageContext = {
  pathname: string;
  title: string;
  purpose: string;
  reversals: string[];
  correlations: string[];
  flow: string[];
  components: SupportPageContextComponent[];
};

export type SupportChatRequestOptions = {
  pageState?: Partial<PageStateSnapshot> | null;
  userRoles?: readonly string[];
};

function serializeComponent(component: RouteKnowledgeComponent): SupportPageContextComponent {
  return {
    name: component.name,
    description: component.description,
    dataOrigin: component.dataOrigin,
    ...(component.columns?.length ? { columns: component.columns } : {}),
    ...(component.statuses && Object.keys(component.statuses).length
      ? { statuses: component.statuses }
      : {}),
  };
}

function splitRoute(route: string): { pathname: string; search: string } {
  const raw = route || '/';
  const q = raw.indexOf('?');
  if (q === -1) {
    return { pathname: raw, search: '' };
  }
  return { pathname: raw.slice(0, q) || '/', search: raw.slice(q) };
}

/** Build the structured page context sent alongside the user message. */
export function buildSupportPageContext(route: string): SupportPageContext | null {
  const { pathname, search } = splitRoute(route);
  const knowledge = resolveKnowledgeContext(pathname, search);
  if (!knowledge) return null;
  return toPageContext(knowledgeContextKey(pathname, search), knowledge);
}

function toPageContext(routeKey: string, knowledge: RouteKnowledge): SupportPageContext {
  return {
    pathname: routeKey,
    title: knowledge.title,
    purpose: knowledge.purpose,
    reversals: knowledge.reversals,
    correlations: knowledge.correlations,
    flow: knowledge.flow,
    components: knowledge.components.map(serializeComponent),
  };
}

/**
 * Prefix the typed question with a hidden system-context block derived from the
 * active route's {@link RouteKnowledgeRegistry} entry (including settings tabs).
 */
export function injectRouteContextIntoMessage(userMessage: string, route: string): string {
  const { pathname, search } = splitRoute(route);
  const routeKey = knowledgeContextKey(pathname, search);
  const knowledge = resolveKnowledgeContext(pathname, search);
  const prefix = formatRouteKnowledgeForChat(routeKey, knowledge);
  return `${prefix} ${userMessage.trim()}`;
}

/**
 * Streams POST /api/v1/support/chat with role + route context headers and
 * structured pageContext / pageState for RAG/LLM grounding.
 */
export async function streamSupportChat(
  message: string,
  roles: readonly string[],
  route: string,
  handlers: SupportChatStreamHandlers,
  signal?: AbortSignal,
  options?: SupportChatRequestOptions,
): Promise<void> {
  const base = (apiClient.defaults.baseURL ?? '').replace(/\/$/, '');
  const url = base ? `${base}/api/v1/support/chat` : '/api/v1/support/chat';
  const pageContext = buildSupportPageContext(route);
  const enrichedMessage = injectRouteContextIntoMessage(message, route);
  const { pathname, search } = splitRoute(route);
  const userRoles = options?.userRoles ?? roles;
  const rawPageState = options?.pageState
    ? {
        ...options.pageState,
        userRoles: options.pageState.userRoles ?? [...userRoles],
        routePath: options.pageState.routePath ?? route,
      }
    : {
        routePath: route,
        pathname,
        search,
        userRoles: [...userRoles],
      };
  // Drop null/undefined so Jackson → Map.copyOf on the API never NPEs.
  const pageState = Object.fromEntries(
    Object.entries(rawPageState).filter(([, v]) => v !== null && v !== undefined),
  );

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
      routeContext: { pathname, search },
      pageState,
      userRoles: [...userRoles],
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
  let sawDone = false;

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
        sawDone = true;
        handlers.onDone?.(parseDonePayload(data));
      }
    }
  }
  if (!sawDone) {
    handlers.onDone?.();
  }
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

function parseDonePayload(raw: string): SupportChatDonePayload | undefined {
  if (!raw) return undefined;
  try {
    return JSON.parse(raw) as SupportChatDonePayload;
  } catch {
    return undefined;
  }
}

function parseActionPayload(raw: string): SupportStreamAction | null {
  try {
    const json = JSON.parse(raw) as Partial<SupportStreamAction> & {
      params?: Record<string, string>;
      target?: string;
    };
    if (!json.action) return null;
    const params = json.params ?? {};
    const target = json.target ?? params.target ?? '';

    if (json.type === 'action_chip' || json.action === 'NAVIGATE' || json.action === 'SPOTLIGHT') {
      return {
        type: 'action_chip',
        action: json.action,
        label: json.label ?? json.action,
        target,
        params,
      };
    }
    if (json.type === 'action_button') {
      return {
        type: 'action_button',
        action: json.action,
        label: json.label ?? json.action,
        params,
        ...(target ? { target } : {}),
      };
    }
    return null;
  } catch {
    return null;
  }
}
