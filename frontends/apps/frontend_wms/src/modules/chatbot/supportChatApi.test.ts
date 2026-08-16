import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from '@/api/client';
import { usePreferencesStore } from '@/stores/preferencesStore';
import {
  executeSupportAction,
  executeSupportActionDraft,
  fetchSupportInsight,
  isSupportInsightRoute,
  resetSupportInsightAvailability,
  streamSupportChat,
} from './supportChatApi';

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
          'X-User-Language': 'en',
          'Accept-Language': 'en',
        }),
        body: expect.stringContaining('System Context:'),
      }),
    );
    const requestBody = JSON.parse(
      (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1].body as string,
    ) as {
      message: string;
      pageContext: { title: string } | null;
      routeContext: { pathname: string; search: string };
      pageState: { routePath: string; userRoles: string[] };
      userRoles: string[];
    };
    expect(requestBody.message).toContain('User Query:');
    expect(requestBody.message).toContain('hi');
    expect(requestBody.pageContext?.title).toBe('B2B Showroom');
    expect(requestBody.routeContext).toEqual({ pathname: '/showroom', search: '' });
    expect(requestBody.userRoles).toEqual(['B2B_CUSTOMER']);
    expect(requestBody.pageState.userRoles).toEqual(['B2B_CUSTOMER']);
    expect(requestBody.pageState.routePath).toBe('/showroom');
    expect((requestBody.pageState as { uiLanguage?: string }).uiLanguage).toBe('en');

    vi.unstubAllGlobals();
  });

  it('forwards the operator UI language to the support chat API', async () => {
    usePreferencesStore.getState().setLanguage('es');
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

    try {
      await streamSupportChat('hola', ['OWNER'], '/dashboard', { onToken: () => {} });
      const init = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as {
        headers: Record<string, string>;
        body: string;
      };
      expect(init.headers['X-User-Language']).toBe('es');
      expect(init.headers['Accept-Language']).toBe('es');
      expect(JSON.parse(init.body).pageState.uiLanguage).toBe('es');
    } finally {
      usePreferencesStore.getState().setLanguage('en');
      vi.unstubAllGlobals();
    }
  });

  it('embeds pageState snapshot fields in the chat payload', async () => {
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

    await streamSupportChat(
      'Why is this BACKORDERED?',
      ['WAREHOUSE_MANAGER'],
      '/sales-orders?status=BACKORDERED',
      { onToken: () => {} },
      undefined,
      {
        pageState: {
          routePath: '/sales-orders?status=BACKORDERED',
          pathname: '/sales-orders',
          search: '?status=BACKORDERED',
          userRoles: ['WAREHOUSE_MANAGER'],
          activeWarehouseId: 'wh-1',
          activeFilter: 'status=BACKORDERED',
          networkState: 'online',
          quarantineCount: 0,
          selectedEntity: 'SO-1',
          activeTab: null,
          activeWarehouseName: 'Main',
        },
      },
    );

    const init = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as { body: string };
    const payload = JSON.parse(init.body) as {
      pageState: {
        activeWarehouseId: string;
        activeFilter: string;
        selectedEntity: string;
        networkState: string;
      };
      routeContext: { pathname: string; search: string };
    };
    expect(payload.routeContext).toEqual({
      pathname: '/sales-orders',
      search: '?status=BACKORDERED',
    });
    expect(payload.pageState.activeWarehouseId).toBe('wh-1');
    expect(payload.pageState.activeFilter).toBe('status=BACKORDERED');
    expect(payload.pageState.selectedEntity).toBe('SO-1');
    expect(payload.pageState.networkState).toBe('online');
    expect(
      (payload.pageState as { isDeviceLocked?: boolean; quarantinedMutationsCount?: number })
        .isDeviceLocked,
    ).toBe(false);
    expect(
      (payload.pageState as { quarantinedMutationsCount?: number }).quarantinedMutationsCount,
    ).toBe(0);

    vi.unstubAllGlobals();
  });

  it('injects live Zustand scanner telemetry into pageState', async () => {
    const { useActiveWarehouseStore } = await import('@/stores/activeWarehouse');
    const { useOfflineStore } = await import('@/stores/offlineStore');
    const { useScannerLockStore } = await import('@/stores/scannerLockStore');

    useActiveWarehouseStore.setState({
      warehouseId: 'wh-telemetry',
      warehouse: { id: 'wh-telemetry', name: 'Telemetry DC', code: 'TEL' },
      contextLocked: true,
      lockReason: 'HARDWARE_SSID',
    });
    useOfflineStore.setState({
      quarantinedMutations: [
        {
          id: 'q1',
          idempotencyKey: 'ik-1',
          method: 'POST',
          url: '/api/v1/x',
          body: {},
          status: 409,
          title: 'conflict',
          detail: 'stock short',
          failedAt: Date.now(),
        },
      ],
    });
    useScannerLockStore.setState({ isLocked: true });

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

    await streamSupportChat('x', ['PICKER'], '/fulfillment', { onToken: () => {} });

    const init = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as { body: string };
    const payload = JSON.parse(init.body) as {
      pageState: {
        activeWarehouseId: string;
        lockReason: string;
        quarantinedMutationsCount: number;
        isDeviceLocked: boolean;
      };
    };
    expect(payload.pageState.activeWarehouseId).toBe('wh-telemetry');
    expect(payload.pageState.lockReason).toBe('HARDWARE_SSID');
    expect(payload.pageState.quarantinedMutationsCount).toBe(1);
    expect(payload.pageState.isDeviceLocked).toBe(true);

    useScannerLockStore.setState({ isLocked: false });
    useOfflineStore.setState({ quarantinedMutations: [] });
    vi.unstubAllGlobals();
  });

  it('parses action chips and follow-ups from done payload', async () => {
    const done = {
      ok: true,
      replyMarkdown: '**Diagnosis:** Order is BACKORDERED.',
      followUpQuestions: ['Why is this BACKORDERED?', 'How do I Un-allocate safely?'],
      actionChips: [
        {
          type: 'action_chip',
          action: 'NAVIGATE',
          label: 'Take me to Sales Orders',
          target: '/sales-orders',
          params: { target: '/sales-orders' },
        },
      ],
    };
    const chunks = [
      'event:token\ndata: **Diagnosis:** \n\n',
      `event:action\ndata: ${JSON.stringify(done.actionChips[0])}\n\n`,
      `event:done\ndata: ${JSON.stringify(done)}\n\n`,
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

    const actions: Array<{ type: string; action: string }> = [];
    let donePayload: { followUpQuestions?: string[] } | undefined;
    await streamSupportChat('help', ['WAREHOUSE_MANAGER'], '/sales-orders', {
      onToken: () => {},
      onAction: (a) => actions.push(a),
      onDone: (p) => {
        donePayload = p;
      },
    });

    expect(actions[0]).toMatchObject({
      type: 'action_chip',
      action: 'NAVIGATE',
      label: 'Take me to Sales Orders',
      target: '/sales-orders',
    });
    expect(donePayload?.followUpQuestions).toHaveLength(2);

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
      pageContext: {
        title: string;
        reversals: string[];
        components: { name: string; statuses?: Record<string, string> }[];
      };
    };
    expect(payload.pageContext.title).toBe('Sales Orders');
    expect(payload.pageContext.reversals.join(' ')).toMatch(/Un-allocate|Cancel/i);
    expect(payload.message).toContain('How to undo:');
    expect(payload.message).toContain('How do I undo allocation?');
    expect(payload.message).toMatch(/ALLOCATED|Statuses/i);
    expect(payload.message).not.toMatch(/SalesOrderService|\/api\/v1/i);
    expect(payload.pageContext.components?.length).toBeGreaterThan(0);
    const withStatuses = payload.pageContext.components.find((c) => c.statuses?.ALLOCATED);
    expect(withStatuses?.statuses?.ALLOCATED).toMatch(/reserv|pick|wave/i);

    vi.unstubAllGlobals();
  });

  it('injects settings-tab context including LBAC roles', async () => {
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

    await streamSupportChat(
      'What can a PICKER do?',
      ['OWNER'],
      '/settings?tab=users',
      { onToken: () => {} },
    );

    const init = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as { body: string };
    const payload = JSON.parse(init.body) as {
      message: string;
      pageContext: { title: string; pathname: string };
    };
    expect(payload.pageContext.pathname).toBe('/settings?tab=users');
    expect(payload.pageContext.title).toMatch(/Users/i);
    expect(payload.message).toMatch(/PICKER|LBAC|warehouse/i);

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

describe('executeSupportActionDraft', () => {
  it('routes supportAction drafts through draft-execute', async () => {
    const spy = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { ok: true, cycleCountId: 'cc-9' },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {} as never,
    });

    const draft = {
      title: 'Generate cycle count for Aisle-4',
      description: 'Creates a worksheet',
      targetEndpoint: '/api/v1/cycle-counts',
      payload: { supportAction: 'generateCycleCount', zoneId: 'Aisle-4' },
    };
    const result = await executeSupportActionDraft(draft);
    expect(result).toEqual({ ok: true, cycleCountId: 'cc-9' });
    expect(spy).toHaveBeenCalledWith('/api/v1/support/actions/draft-execute', { actionDraft: draft });
    spy.mockRestore();
  });

  it('posts pre-filled payload to targetEndpoint for allow-listed REST drafts', async () => {
    const spy = vi.spyOn(apiClient, 'request').mockResolvedValue({
      data: { status: 'ALLOCATED' },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {} as never,
    });

    const draft = {
      title: 'Allocate order',
      description: 'Reserve stock',
      targetEndpoint: '/api/v1/sales-orders/SO-1/allocate',
      httpMethod: 'POST',
      payload: { orderId: 'SO-1' },
    };
    const result = await executeSupportActionDraft(draft);
    expect(result.ok).toBe(true);
    expect(spy).toHaveBeenCalledWith({
      url: '/api/v1/sales-orders/SO-1/allocate',
      method: 'POST',
      data: { orderId: 'SO-1' },
      params: undefined,
    });
    spy.mockRestore();
  });

  it('uses PATCH when actionDraft.httpMethod is PATCH', async () => {
    const spy = vi.spyOn(apiClient, 'request').mockResolvedValue({
      data: { ok: true },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {} as never,
    });

    const draft = {
      title: 'Release hold',
      description: 'Clear credit hold',
      targetEndpoint: '/api/v1/sales-orders/SO-1/confirm',
      httpMethod: 'PATCH',
      payload: { status: 'CONFIRMED' },
    };
    await executeSupportActionDraft(draft);
    expect(spy).toHaveBeenCalledWith(
      expect.objectContaining({
        url: '/api/v1/sales-orders/SO-1/confirm',
        method: 'PATCH',
        data: { status: 'CONFIRMED' },
      }),
    );
    spy.mockRestore();
  });

  it('rejects unsafe target endpoints', async () => {
    const result = await executeSupportActionDraft({
      title: 'Danger',
      description: 'wipe',
      targetEndpoint: '/api/v1/admin/wipe',
      payload: {},
    });
    expect(result.ok).toBe(false);
    expect(String(result.error)).toMatch(/approved list/i);
  });
});

describe('streamSupportChat multimodal payload', () => {
  it('sends both imageBase64 and base64Image aliases', async () => {
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

    await streamSupportChat(
      'Torn barcode on carton',
      ['PICKER'],
      '/inbound/receive',
      { onToken: () => {} },
      undefined,
      {
        imageBase64: 'abc123',
        base64Image: 'abc123',
        imageMimeType: 'image/jpeg',
      },
    );

    const init = (fetch as ReturnType<typeof vi.fn>).mock.calls[0][1] as { body: string };
    const payload = JSON.parse(init.body) as {
      imageBase64?: string;
      base64Image?: string;
      imageMimeType?: string;
    };
    expect(payload.imageBase64).toBe('abc123');
    expect(payload.base64Image).toBe('abc123');
    expect(payload.imageMimeType).toBe('image/jpeg');
    vi.unstubAllGlobals();
  });
});

describe('fetchSupportInsight', () => {
  afterEach(() => {
    resetSupportInsightAvailability();
    vi.restoreAllMocks();
  });

  it('skips auth-shell routes without calling the API', async () => {
    const spy = vi.spyOn(apiClient, 'get');
    expect(isSupportInsightRoute('/login')).toBe(false);
    expect(await fetchSupportInsight('/login')).toBeNull();
    expect(await fetchSupportInsight('/signup')).toBeNull();
    expect(await fetchSupportInsight('/invite/abc')).toBeNull();
    expect(spy).not.toHaveBeenCalled();
  });

  it('returns the proactive insight when the endpoint is present', async () => {
    vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { ok: true, proactiveInsight: '3 orders need attention' },
    });
    expect(await fetchSupportInsight('/dashboard')).toBe('3 orders need attention');
  });

  it('stops polling after a 404 so a disabled chatbot module does not spam the console', async () => {
    const spy = vi.spyOn(apiClient, 'get').mockRejectedValue({ response: { status: 404 } });
    expect(await fetchSupportInsight('/dashboard')).toBeNull();
    expect(await fetchSupportInsight('/fulfillment')).toBeNull();
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
