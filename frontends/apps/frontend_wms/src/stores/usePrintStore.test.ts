import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPrintStore } from '@/stores/usePrintStore';
import type { QzTrayAgent, PrintAgentStatus } from '@/lib/qzTrayAgent';

function mockAgent(overrides: Partial<QzTrayAgent> = {}): QzTrayAgent {
  let status: PrintAgentStatus = 'disconnected';
  let listener: ((s: PrintAgentStatus, e?: string) => void) | undefined;
  const agent = {
    setStatusListener: (fn: (s: PrintAgentStatus, e?: string) => void) => {
      listener = fn;
    },
    getStatus: () => status,
    connect: vi.fn(async () => {
      status = 'connected';
      listener?.('connected');
    }),
    disconnect: vi.fn(() => {
      status = 'disconnected';
      listener?.('disconnected');
    }),
    listPrinters: vi.fn(async () => ['Zebra ZP450', 'ZDesigner']),
    printRaw: vi.fn(async () => undefined),
    ...overrides,
  } as unknown as QzTrayAgent;
  return agent;
}

describe('usePrintStore', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('streams ZPL to hardware when agent connected and printer bound', async () => {
    const agent = mockAgent();
    const useStore = createPrintStore(agent);
    useStore.getState().setBoundPrinterName('Zebra ZP450');
    await useStore.getState().connectAgent();

    const route = await useStore.getState().executePrint('^XA^FDTEST^FS^XZ', 'ZPL');

    expect(route).toBe('hardware');
    expect(agent.printRaw).toHaveBeenCalledWith('Zebra ZP450', '^XA^FDTEST^FS^XZ');
  });

  it('falls back to browser when hardware print fails', async () => {
    const agent = mockAgent({
      printRaw: vi.fn(async () => {
        throw new Error('printer offline');
      }),
    });
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:mock-label'),
      revokeObjectURL: vi.fn(),
    });
    const appendSpy = vi.spyOn(document.body, 'appendChild');
    const useStore = createPrintStore(agent);
    useStore.getState().setBoundPrinterName('Zebra ZP450');
    await useStore.getState().connectAgent();

    const route = await useStore.getState().executePrint('^XA^FDTEST^FS^XZ', 'ZPL');

    expect(route).toBe('browser');
    expect(appendSpy).toHaveBeenCalled();
    expect(useStore.getState().lastPrintError).toContain('printer offline');
  });

  it('escapes mock EasyPost payload before building printable HTML', async () => {
    const createObjectURL = vi.fn(() => 'blob:mock-label');
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL,
      revokeObjectURL: vi.fn(),
    });
    vi.stubGlobal('Blob', class MockBlob {
      constructor(public parts: unknown[]) {}
    });
    const appendSpy = vi.spyOn(document.body, 'appendChild');
    const useStore = createPrintStore(mockAgent());

    const route = await useStore.getState().executePrint(
      'easypost_mock_<img src=x onerror=alert(1)>',
      'PDF',
    );

    expect(route).toBe('browser');
    expect(appendSpy).toHaveBeenCalled();
    const blob = createObjectURL.mock.calls[0]?.[0] as { parts?: unknown[] };
    const html = String(blob?.parts?.[0] ?? '');
    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;');
    expect(html).not.toContain('<img src=x onerror=alert(1)>');
  });

  it('lists printers from the local agent', async () => {
    const agent = mockAgent();
    const useStore = createPrintStore(agent);
    const printers = await useStore.getState().refreshPrinters();
    expect(printers).toEqual(['Zebra ZP450', 'ZDesigner']);
    expect(useStore.getState().printers).toHaveLength(2);
  });
});
