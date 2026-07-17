/**
 * Thin adapter around a local QZ Tray (or compatible) websocket print agent.
 * Connects to wss://localhost:8181 by default; falls back gracefully when absent.
 */

export type PrintAgentStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

type Pending = {
  resolve: (value: unknown) => void;
  reject: (reason?: unknown) => void;
  timer: ReturnType<typeof setTimeout>;
};

const DEFAULT_URL = 'wss://localhost:8181';
const REQUEST_TIMEOUT_MS = 8_000;

export class QzTrayAgent {
  private ws: WebSocket | null = null;
  private status: PrintAgentStatus = 'disconnected';
  private pending = new Map<string, Pending>();
  private seq = 0;
  private onStatus?: (status: PrintAgentStatus, error?: string) => void;

  constructor(private readonly url = DEFAULT_URL) {}

  setStatusListener(listener: (status: PrintAgentStatus, error?: string) => void) {
    this.onStatus = listener;
  }

  getStatus(): PrintAgentStatus {
    return this.status;
  }

  async connect(): Promise<void> {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.setStatus('connected');
      return;
    }
    if (this.ws?.readyState === WebSocket.CONNECTING) {
      return;
    }

    this.setStatus('connecting');
    await new Promise<void>((resolve, reject) => {
      let settled = false;
      try {
        const ws = new WebSocket(this.url);
        this.ws = ws;
        ws.onopen = () => {
          settled = true;
          this.setStatus('connected');
          resolve();
        };
        ws.onerror = () => {
          if (!settled) {
            settled = true;
            this.cleanupSocket();
            this.setStatus('error', 'QZ Tray websocket unavailable');
            reject(new Error('QZ Tray websocket unavailable'));
          }
        };
        ws.onclose = () => {
          this.cleanupSocket();
          if (this.status !== 'error') {
            this.setStatus('disconnected');
          }
        };
        ws.onmessage = (event) => this.handleMessage(event.data);
      } catch (err) {
        this.setStatus('error', err instanceof Error ? err.message : 'Connect failed');
        reject(err);
      }
    });
  }

  disconnect(): void {
    this.rejectAll(new Error('Disconnected'));
    this.cleanupSocket();
    this.setStatus('disconnected');
  }

  async listPrinters(): Promise<string[]> {
    await this.ensureConnected();
    const result = await this.request(['printers', 'find'], []);
    if (Array.isArray(result)) {
      return result.map(String);
    }
    if (typeof result === 'string' && result.length > 0) {
      return [result];
    }
    return [];
  }

  /** Stream raw ZPL (or other raw) bytes to the named printer. */
  async printRaw(printerName: string, payload: string): Promise<void> {
    await this.ensureConnected();
    const config = { printer: { name: printerName } };
    const data = [
      {
        type: 'raw',
        format: 'command',
        flavor: 'plain',
        data: payload,
      },
    ];
    await this.request(['print'], [config, data]);
  }

  private async ensureConnected(): Promise<void> {
    if (this.ws?.readyState === WebSocket.OPEN) return;
    await this.connect();
  }

  private request(call: string[], params: unknown[]): Promise<unknown> {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error('Print agent not connected'));
    }
    const uid = `invsys-${Date.now()}-${++this.seq}`;
    const envelope = {
      call,
      params,
      uid,
      // Unsigned local-dev handshake; production QZ sites should enable signing.
      timestamp: new Date().toISOString(),
    };
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(uid);
        reject(new Error('Print agent request timed out'));
      }, REQUEST_TIMEOUT_MS);
      this.pending.set(uid, { resolve, reject, timer });
      this.ws!.send(JSON.stringify(envelope));
    });
  }

  private handleMessage(raw: unknown) {
    let parsed: {
      uid?: string;
      result?: unknown;
      error?: { message?: string } | string;
    };
    try {
      parsed = typeof raw === 'string' ? JSON.parse(raw) : (raw as typeof parsed);
    } catch {
      return;
    }
    const uid = parsed.uid;
    if (!uid || !this.pending.has(uid)) return;
    const entry = this.pending.get(uid)!;
    this.pending.delete(uid);
    clearTimeout(entry.timer);
    if (parsed.error) {
      const message =
        typeof parsed.error === 'string'
          ? parsed.error
          : parsed.error.message ?? 'Print agent error';
      entry.reject(new Error(message));
      return;
    }
    entry.resolve(parsed.result);
  }

  private rejectAll(err: Error) {
    for (const [, entry] of this.pending) {
      clearTimeout(entry.timer);
      entry.reject(err);
    }
    this.pending.clear();
  }

  private cleanupSocket() {
    if (this.ws) {
      this.ws.onopen = null;
      this.ws.onclose = null;
      this.ws.onerror = null;
      this.ws.onmessage = null;
      try {
        this.ws.close();
      } catch {
        /* ignore */
      }
    }
    this.ws = null;
  }

  private setStatus(status: PrintAgentStatus, error?: string) {
    this.status = status;
    this.onStatus?.(status, error);
  }
}

export const defaultQzTrayAgent = new QzTrayAgent();
