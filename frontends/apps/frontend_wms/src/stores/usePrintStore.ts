import { create } from 'zustand';
import {
  defaultQzTrayAgent,
  type PrintAgentStatus,
  type QzTrayAgent,
} from '@/lib/qzTrayAgent';

export type PrintFormat = 'ZPL' | 'PDF';

interface PrintState {
  agentStatus: PrintAgentStatus;
  agentError: string | null;
  printers: string[];
  printersLoading: boolean;
  lastPrintError: string | null;
  /** Preferred Zebra / raw printer name from workstation settings (local cache). */
  boundPrinterName: string | null;
  connectAgent: () => Promise<boolean>;
  disconnectAgent: () => void;
  refreshPrinters: () => Promise<string[]>;
  setBoundPrinterName: (name: string | null) => void;
  /**
   * ZPL + connected agent → silent raw socket print.
   * PDF (or any ZPL failure) → browser native print via hidden iframe / Blob URL.
   */
  executePrint: (payload: string | Blob, format: PrintFormat) => Promise<'hardware' | 'browser'>;
}

function isZplPayload(payload: string): boolean {
  const trimmed = payload.trim();
  return trimmed.startsWith('^XA') || trimmed.includes('^XZ');
}

function browserPrintPdf(payload: string | Blob): Promise<void> {
  return new Promise((resolve, reject) => {
    try {
      let url: string;
      let revoke = false;
      if (typeof payload === 'string') {
        if (payload.startsWith('http://') || payload.startsWith('https://') || payload.startsWith('blob:')) {
          url = payload;
        } else if (payload.startsWith('%PDF') || payload.startsWith('easypost_mock_')) {
          // Mock / inline: render a printable HTML slip instead of a real PDF binary.
          const html = `<!doctype html><html><body style="font-family:sans-serif;padding:24px">
            <h1>Shipping Label</h1>
            <p>${payload.startsWith('easypost_mock_') ? `Mock label: ${payload}` : 'PDF label'}</p>
            <script>window.onload=()=>{window.print();}</script>
          </body></html>`;
          url = URL.createObjectURL(new Blob([html], { type: 'text/html' }));
          revoke = true;
        } else {
          const html = `<!doctype html><html><body style="font-family:monospace;white-space:pre-wrap;padding:16px">${escapeHtml(payload)}</body></html>`;
          url = URL.createObjectURL(new Blob([html], { type: 'text/html' }));
          revoke = true;
        }
      } else {
        url = URL.createObjectURL(payload);
        revoke = true;
      }

      const iframe = document.createElement('iframe');
      iframe.setAttribute('aria-hidden', 'true');
      iframe.style.position = 'fixed';
      iframe.style.right = '0';
      iframe.style.bottom = '0';
      iframe.style.width = '0';
      iframe.style.height = '0';
      iframe.style.border = '0';
      iframe.src = url;
      const cleanup = () => {
        iframe.remove();
        if (revoke) URL.revokeObjectURL(url);
      };
      let settled = false;
      const finish = (err?: unknown) => {
        if (settled) return;
        settled = true;
        cleanup();
        if (err) reject(err);
        else resolve();
      };
      iframe.onload = () => {
        try {
          iframe.contentWindow?.focus();
          iframe.contentWindow?.print();
          window.setTimeout(() => finish(), 300);
        } catch (err) {
          finish(err);
        }
      };
      document.body.appendChild(iframe);
      // jsdom / agents that never fire iframe.onload
      window.setTimeout(() => finish(), 800);
    } catch (err) {
      reject(err);
    }
  });
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

async function resolveZplText(payload: string | Blob): Promise<string> {
  if (typeof payload !== 'string') {
    return payload.text();
  }
  if (isZplPayload(payload)) {
    return payload;
  }
  if (payload.startsWith('http://') || payload.startsWith('https://')) {
    const res = await fetch(payload);
    return res.text();
  }
  return payload;
}

export function createPrintStore(agent: QzTrayAgent = defaultQzTrayAgent) {
  return create<PrintState>((set, get) => {
    agent.setStatusListener((agentStatus, agentError) => {
      set({ agentStatus, agentError: agentError ?? null });
    });

    return {
      agentStatus: agent.getStatus(),
      agentError: null,
      printers: [],
      printersLoading: false,
      lastPrintError: null,
      boundPrinterName: null,

      setBoundPrinterName: (name) => set({ boundPrinterName: name }),

      connectAgent: async () => {
        try {
          await agent.connect();
          set({ agentStatus: 'connected', agentError: null });
          return true;
        } catch (err) {
          set({
            agentStatus: 'error',
            agentError: err instanceof Error ? err.message : 'Connect failed',
          });
          return false;
        }
      },

      disconnectAgent: () => {
        agent.disconnect();
        set({ agentStatus: 'disconnected', printers: [] });
      },

      refreshPrinters: async () => {
        set({ printersLoading: true, lastPrintError: null });
        try {
          if (agent.getStatus() !== 'connected') {
            await agent.connect();
          }
          const printers = await agent.listPrinters();
          set({ printers, printersLoading: false, agentStatus: 'connected' });
          return printers;
        } catch (err) {
          set({
            printers: [],
            printersLoading: false,
            agentStatus: 'error',
            agentError: err instanceof Error ? err.message : 'Printer discovery failed',
          });
          return [];
        }
      },

      executePrint: async (payload, format) => {
        set({ lastPrintError: null });
        const printer = get().boundPrinterName;

        if (format === 'ZPL') {
          try {
            if (agent.getStatus() !== 'connected') {
              await agent.connect();
            }
            if (!printer) {
              throw new Error('No ZPL printer bound — pick one in Scanner Settings');
            }
            const zpl = await resolveZplText(payload);
            await agent.printRaw(printer, zpl);
            set({ agentStatus: 'connected' });
            return 'hardware';
          } catch (err) {
            const message = err instanceof Error ? err.message : 'Hardware print failed';
            set({ lastPrintError: message });
            // Graceful fallback: browser dialog with payload preview / mock slip
            await browserPrintPdf(typeof payload === 'string' ? payload : await payload.text());
            return 'browser';
          }
        }

        await browserPrintPdf(payload);
        return 'browser';
      },
    };
  });
}

export const usePrintStore = createPrintStore();
