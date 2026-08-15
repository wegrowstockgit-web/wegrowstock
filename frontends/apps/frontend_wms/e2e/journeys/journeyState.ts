import fs from 'node:fs';
import path from 'node:path';

const STATE_PATH = path.join(process.cwd(), 'playwright', '.auth', 'journey-state.json');

export interface JourneyState {
  pickerEmail?: string;
  pickerUserId?: string;
  adminUserId?: string;
  managerUserId?: string;
  ownerUserId?: string;
  inviteTokenHash?: string;
  purchaseOrderId?: string;
  purchaseOrderNumber?: string;
  salesOrderId?: string;
  salesOrderNumber?: string;
  exceptionId?: string;
  events?: string[];
}

export function readJourneyState(): JourneyState {
  try {
    if (!fs.existsSync(STATE_PATH)) return { events: [] };
    return JSON.parse(fs.readFileSync(STATE_PATH, 'utf8')) as JourneyState;
  } catch {
    return { events: [] };
  }
}

export function writeJourneyState(patch: Partial<JourneyState>): JourneyState {
  const prev = readJourneyState();
  const next: JourneyState = {
    ...prev,
    ...patch,
    events: [...(prev.events ?? []), ...(patch.events ?? [])],
  };
  fs.mkdirSync(path.dirname(STATE_PATH), { recursive: true });
  fs.writeFileSync(STATE_PATH, JSON.stringify(next, null, 2), 'utf8');
  return next;
}

export function resetJourneyState(): void {
  fs.mkdirSync(path.dirname(STATE_PATH), { recursive: true });
  fs.writeFileSync(STATE_PATH, JSON.stringify({ events: [] }, null, 2), 'utf8');
}
