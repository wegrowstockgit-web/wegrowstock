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
        body: expect.stringContaining('System Context:'),
      }),
    );
    const requestBody = JSON.parse(
      (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body as string,
    ) as {
      message: string;
      pageContext: { title: string } | null;
    };
    expect(requestBody.message).toContain('User Query:');
    expect(requestBody.message).toContain('hi');
    expect(requestBody.pageContext?.title).toBe('B2B Showroom');

    vi.unstubAllGlobals();
  });

  it('injects sales-order reversals into the chat payload', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        body: new ReadableStream({
          start(controller) {
            controller.enqueue(new TextEncoder().encode('event:done\ndata: {"ok":true}\n\n'));
            controller.close();
          },
        }),
        text: async () => '',
      }),
    );

    await streamSupportChat('How do I undo allocation?', ['WAREHOUSE_MANAGER'], '/sales-orders', {
      onToken: () => {},
    });

    const init = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as { body: string };
    const payload = JSON.parse(init.body) as {
      message: string;
      pageContext: { title: string; reversals: string[] };
    };
    expect(payload.pageContext.title).toBe('Sales Orders');
    expect(payload.pageContext.reversals.join(' ')).toMatch(/Un-allocate|Cancel/i);
    expect(payload.message).toContain('Reversal mechanism:');
    expect(payload.message).toContain('How do I undo allocation?');

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
