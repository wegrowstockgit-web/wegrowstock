import { apiClient } from '@/api/client';

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

/**
 * Streams POST /api/v1/support/chat with role + route context headers.
 * Emits token text and structured action_button events.
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

  const res = await fetch(url, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      'X-User-Roles': roles.join(','),
      'X-Current-Route': route,
    },
    body: JSON.stringify({ message }),
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
