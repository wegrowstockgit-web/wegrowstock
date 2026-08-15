import { apiClient } from '@/api/client';
import {
  enrichRouteKnowledge,
  formatRouteKnowledgeForChat,
  knowledgeContextKey,
  resolveKnowledgeContext,
  type RouteKnowledge,
  type RouteKnowledgeComponent,
} from '@/lib/pageKnowledge';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { useOfflineStore } from '@/stores/offlineStore';
import { useScannerLockStore } from '@/stores/scannerLockStore';
import type { PageStateSnapshot } from './usePageStateSnapshot';

/** Live Zustand telemetry injected into every support chat request. */
export function injectZustandTelemetry(
  pageState: Record<string, unknown>,
): Record<string, unknown> {
  const warehouse = useActiveWarehouseStore.getState();
  const offline = useOfflineStore.getState();
  const scanner = useScannerLockStore.getState();
  const quarantinedMutationsCount = offline.quarantinedMutations?.length ?? 0;
  return {
    ...pageState,
    activeWarehouseId: pageState.activeWarehouseId ?? warehouse.warehouseId ?? null,
    activeWarehouseName:
      pageState.activeWarehouseName
      ?? warehouse.warehouse?.name
      ?? null,
    lockReason: pageState.lockReason ?? warehouse.lockReason ?? null,
    quarantinedMutationsCount:
      pageState.quarantinedMutationsCount ?? quarantinedMutationsCount,
    quarantineCount: pageState.quarantineCount ?? quarantinedMutationsCount,
    isDeviceLocked: pageState.isDeviceLocked ?? scanner.isLocked ?? false,
  };
}

export interface SupportActionButton {
  type: 'action_button';
  action: string;
  label: string;
  params: Record<string, string>;
  target?: string;
}

export interface SupportActionChip {
  type: 'action_chip';
  action: 'NAVIGATE' | 'SPOTLIGHT' | 'START_TOUR' | string;
  label: string;
  target: string;
  params: Record<string, string>;
}

export type SupportStreamAction = SupportActionButton | SupportActionChip;

export type SupportActionDraft = {
  title: string;
  description: string;
  targetEndpoint: string;
  /** HTTP verb for Approve & Execute (defaults to POST). */
  httpMethod?: 'POST' | 'PATCH' | 'PUT' | 'DELETE' | 'GET' | string;
  payload: Record<string, unknown>;
};

export interface SupportChatDonePayload {
  ok?: boolean;
  replyMarkdown?: string;
  followUpQuestions?: string[];
  actionChips?: SupportStreamAction[];
  actionDraft?: SupportActionDraft | null;
  proactiveInsight?: string | null;
  escalation?: {
    ticketId: string;
    status: string;
    message: string;
  } | null;
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
  rolePermissions: string[];
  whoCanUse: string[];
  dataOrigin: string;
  reversals: string[];
  howToUndo: string[];
  correlations: string[];
  flow: string[];
  stepByStepFlow: string[];
  components: SupportPageContextComponent[];
  glossary?: Record<string, string>;
};

export type SupportChatRequestOptions = {
  pageState?: Partial<PageStateSnapshot> | null;
  userRoles?: readonly string[];
  imageBase64?: string | null;
  /** Spec alias — sent alongside {@link imageBase64} for multimodal clients. */
  base64Image?: string | null;
  imageMimeType?: string | null;
  /** Stable id for MessageChatMemoryAdvisor (defaults to a per-tab session). */
  sessionId?: string | null;
};

function serializeComponent(component: RouteKnowledgeComponent): SupportPageContextComponent {
  const columns = Array.isArray(component.columns)
    ? component.columns
    : component.columns
      ? Object.entries(component.columns).map(([name, purpose]) => ({ name, purpose }))
      : undefined;
  return {
    name: component.name,
    description: component.description,
    dataOrigin: component.dataOrigin,
    ...(columns?.length ? { columns } : {}),
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
  const enriched = enrichRouteKnowledge(routeKey, knowledge);
  return {
    pathname: routeKey,
    title: enriched.title,
    purpose: enriched.purpose,
    rolePermissions: enriched.rolePermissions,
    whoCanUse: enriched.whoCanUse,
    dataOrigin: enriched.dataOrigin,
    reversals: enriched.howToUndo,
    howToUndo: enriched.howToUndo,
    correlations: enriched.correlations,
    flow: enriched.stepByStepFlow,
    stepByStepFlow: enriched.stepByStepFlow,
    components: enriched.components.map(serializeComponent),
    ...(enriched.glossary && Object.keys(enriched.glossary).length
      ? { glossary: enriched.glossary }
      : {}),
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
  // Deep-inject live warehouse / offline / scanner lock telemetry for zero-click awareness.
  const pageState = Object.fromEntries(
    Object.entries(injectZustandTelemetry(rawPageState as Record<string, unknown>)).filter(
      ([, v]) => v !== null && v !== undefined,
    ),
  );
  const recentBreadcrumbs = Array.isArray(
    (rawPageState as { recentBreadcrumbs?: unknown }).recentBreadcrumbs,
  )
    ? (rawPageState as { recentBreadcrumbs: unknown[] }).recentBreadcrumbs
    : [];

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
      recentBreadcrumbs,
      userRoles: [...userRoles],
      sessionId:
        options?.sessionId
        ?? (typeof crypto !== 'undefined' && 'randomUUID' in crypto
          ? crypto.randomUUID()
          : `support-${Date.now()}`),
      ...(() => {
        const image =
          options?.imageBase64
          ?? options?.base64Image
          ?? null;
        if (!image) return {};
        return {
          imageBase64: image,
          base64Image: image,
          imageMimeType: options?.imageMimeType ?? 'image/jpeg',
        };
      })(),
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

/**
 * Approve & Execute a HITL ActionDraft.
 * - Agent-tool drafts (`payload.supportAction`) go through the allow-listed draft-execute API.
 * - Otherwise calls `targetEndpoint` with `httpMethod` + `payload` via Axios (still subject to
 *   training-mode mutation blocking and server auth/tenant checks).
 */
export async function executeSupportActionDraft(
  actionDraft: SupportActionDraft,
): Promise<Record<string, unknown>> {
  const supportAction = actionDraft.payload?.supportAction;
  if (typeof supportAction === 'string' && supportAction.trim()) {
    const { data } = await apiClient.post<Record<string, unknown>>(
      '/api/v1/support/actions/draft-execute',
      { actionDraft },
    );
    return data;
  }

  const endpoint = (actionDraft.targetEndpoint || '').trim();
  if (!isSafeDraftEndpoint(endpoint)) {
    return {
      ok: false,
      error: 'That change is not on the approved list. Use the on-screen button instead.',
    };
  }

  const method = normalizeDraftHttpMethod(actionDraft.httpMethod);
  try {
    const { data } = await apiClient.request<Record<string, unknown>>({
      url: endpoint,
      method,
      data: method === 'GET' || method === 'DELETE' ? undefined : (actionDraft.payload ?? {}),
      params: method === 'GET' ? (actionDraft.payload ?? {}) : undefined,
    });
    return {
      ok: true,
      title: actionDraft.title,
      ...(data && typeof data === 'object' ? data : { result: data }),
    };
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Draft execution failed';
    // Soft-fail: still try the secure draft-execute allow-list path for navigational approvals.
    try {
      const { data } = await apiClient.post<Record<string, unknown>>(
        '/api/v1/support/actions/draft-execute',
        { actionDraft },
      );
      return data;
    } catch {
      return { ok: false, error: message, title: actionDraft.title };
    }
  }
}

function normalizeDraftHttpMethod(method?: string): 'POST' | 'PATCH' | 'PUT' | 'DELETE' | 'GET' {
  const upper = (method || 'POST').trim().toUpperCase();
  if (upper === 'PATCH' || upper === 'PUT' || upper === 'DELETE' || upper === 'GET') {
    return upper;
  }
  return 'POST';
}

function isSafeDraftEndpoint(endpoint: string): boolean {
  const lower = endpoint.toLowerCase();
  if (!lower.startsWith('/api/v1/')) return false;
  return (
    lower.includes('/allocate')
    || lower.includes('/unallocate')
    || lower.includes('/un-allocate')
    || lower.includes('/release')
    || lower.includes('/claim')
    || lower.includes('/confirm')
    || lower.includes('/cancel')
    || lower.includes('/cycle-counts')
  );
}

export async function fetchSupportInsight(route: string): Promise<string | null> {
  const { data } = await apiClient.get<{ proactiveInsight?: string | null }>(
    '/api/v1/support/insights',
    { params: { route } },
  );
  return data.proactiveInsight ?? null;
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

    if (
      json.type === 'action_chip'
      || json.action === 'NAVIGATE'
      || json.action === 'SPOTLIGHT'
      || json.action === 'START_TOUR'
    ) {
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
