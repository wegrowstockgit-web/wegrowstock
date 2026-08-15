import type { ConflictFieldDescriptor, ServerSyncConflict } from './syncConflictTypes';

const ACTION_LABELS: Record<string, string> = {
  INBOUND_RECEIVE: 'Inbound Receive',
  OUTBOUND_PICK: 'Outbound Pick',
  CYCLE_COUNT: 'Cycle Count',
};

/** Natural-language incident line for supervisors (never raw JSON). */
export function formatConflictSummary(conflict: ServerSyncConflict): string {
  if (conflict.humanSummary?.trim()) {
    return conflict.humanSummary.trim();
  }
  const operator = conflict.pickerDisplayName?.trim() || 'Floor Operator';
  const action =
    conflict.actionLabel?.trim() ||
    ACTION_LABELS[conflict.actionType ?? ''] ||
    'Warehouse Transaction';
  const reason = humanizeError(conflict.errorMessage) || 'a business rule blocked the replay';
  return `Floor Operator [${operator}] failed to process an [${action}] transaction because ${reason}.`;
}

export function humanizeError(raw: string | null | undefined): string {
  if (!raw?.trim()) return '';
  let trimmed = raw.trim();
  const colon = trimmed.indexOf(':');
  if (colon > 0 && colon < 48 && /^[A-Z0-9_]+$/.test(trimmed.slice(0, colon))) {
    trimmed = trimmed.slice(colon + 1).trim();
  }
  if (!trimmed) return '';
  return trimmed.charAt(0).toLowerCase() + trimmed.slice(1);
}

export function payloadBody(conflict: ServerSyncConflict): Record<string, unknown> {
  const body = conflict.payload?.body;
  if (body && typeof body === 'object' && !Array.isArray(body)) {
    return body as Record<string, unknown>;
  }
  return {};
}

export function fieldValue(
  conflict: ServerSyncConflict,
  field: ConflictFieldDescriptor,
): string | number {
  const body = payloadBody(conflict);
  const raw = body[field.key];
  if (raw == null) return field.type === 'number' ? '' : '';
  if (field.type === 'number') {
    const n = Number(raw);
    return Number.isFinite(n) ? n : '';
  }
  return String(raw);
}

export function actionBadgeLabel(conflict: ServerSyncConflict): string {
  return (
    conflict.actionLabel?.trim() ||
    ACTION_LABELS[conflict.actionType ?? ''] ||
    'Transaction'
  );
}
