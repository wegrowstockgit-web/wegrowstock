import { describe, expect, it, vi } from 'vitest';
import { apiClient } from '@/api/client';
import { executeSupportAction, streamSupportChat } from './supportChatApi';

describe('streamSupportChat', () => {
  it('preserves whitespace between SSE token payloads', async () => {
    const chunks = [
      'event:token\ndata: As \n\n',
      'event:token\ndata: a B2B \n\n',
      'event:token\ndata: customer\n\n',
      'event:done\ndata: {"ok":true}\n\n',
    ];
    let i = 0;
    const body = new ReadableStream<Uint8Array>({
      pull(controller) {
        if (i >= chunks.length) {
          controller.close();
          return;
        }
        controller.enqueue(new TextEncoder().encode(chunks[i++]));
      },
    });

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body,
        text: async () => '',
      }),
    );

    let text = '';
    await streamSupportChat('hi', ['B2B_CUSTOMER'], '/showroom', {
      onToken: (t) => {
        text += t;
      },
    });

    expect(text).toBe('As a B2B customer');
    expect(fetch).toHaveBeenCalledWith(
      expect.stringContaining('/api/v1/support/chat'),
      expect.objectContaining({
        headers: expect.objectContaining({
          'X-User-Roles': 'B2B_CUSTOMER',
          'X-Current-Route': '/showroom',
        }),
      }),
    );

    vi.unstubAllGlobals();
  });

  it('parses action_button SSE events', async () => {
    const chunks = [
      'event:token\ndata: I can start a cycle count.\n\n',
      'event:action\ndata: {"type":"action_button","action":"generateCycleCount","label":"Generate cycle count for Aisle-4","params":{"zoneId":"Aisle-4"}}\n\n',
      'event:done\ndata: {"ok":true}\n\n',
    ];
    let i = 0;
    const body = new ReadableStream<Uint8Array>({
      pull(controller) {
        if (i >= chunks.length) {
          controller.close();
          return;
        }
        controller.enqueue(new TextEncoder().encode(chunks[i++]));
      },
    });

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body,
        text: async () => '',
      }),
    );

    const actions: Array<{ action: string; params: Record<string, string> }> = [];
    await streamSupportChat('generate cycle count', ['WAREHOUSE_MANAGER'], '/cycle-counts', {
      onToken: () => {},
      onAction: (a) => actions.push(a),
    });

    expect(actions).toHaveLength(1);
    expect(actions[0]).toMatchObject({
      type: 'action_button',
      action: 'generateCycleCount',
      label: 'Generate cycle count for Aisle-4',
      params: { zoneId: 'Aisle-4' },
    });

    vi.unstubAllGlobals();
  });

  it('ignores malformed action payloads', async () => {
    const chunks = [
      'event:action\ndata: not-json\n\n',
      'event:action\ndata: {"type":"other","action":"x"}\n\n',
      'event:done\ndata: {"ok":true}\n\n',
    ];
    let i = 0;
    const body = new ReadableStream<Uint8Array>({
      pull(controller) {
        if (i >= chunks.length) {
          controller.close();
          return;
        }
        controller.enqueue(new TextEncoder().encode(chunks[i++]));
      },
    });

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body,
        text: async () => '',
      }),
    );

    const actions: unknown[] = [];
    await streamSupportChat('hi', ['ADMIN'], '/', {
      onToken: () => {},
      onAction: (a) => actions.push(a),
    });
    expect(actions).toHaveLength(0);

    vi.unstubAllGlobals();
  });
});

describe('executeSupportAction', () => {
  it('posts action + params to execute endpoint', async () => {
    const spy = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { ok: true, cycleCountId: 'c1' },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {} as never,
    });

    const result = await executeSupportAction('generateCycleCount', { zoneId: 'Aisle-4' });
    expect(result).toEqual({ ok: true, cycleCountId: 'c1' });
    expect(spy).toHaveBeenCalledWith('/api/v1/support/actions/execute', {
      action: 'generateCycleCount',
      params: { zoneId: 'Aisle-4' },
    });
    spy.mockRestore();
  });
});
