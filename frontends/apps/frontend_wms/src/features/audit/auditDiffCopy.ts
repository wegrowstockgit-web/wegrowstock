import type { AuditLogItem } from '@/api/types';

const HIDDEN_KEYS = new Set([
  'id',
  'tenant_id',
  'tenantId',
  'password_hash',
  'passwordHash',
  'token_hash',
  'tokenHash',
  'created_at',
  'createdAt',
  'updated_at',
  'updatedAt',
  'version',
]);

const ACTION_LABELS: Record<string, string> = {
  TG_INSERT: 'Created',
  TG_UPDATE: 'Updated',
  TG_DELETE: 'Deleted',
  INVITE_USER: 'Sent invitation',
  USER_INVITE: 'Sent invitation',
  RESEND_INVITATION: 'Resent invitation',
  UPDATE_USER: 'Updated user',
  DEACTIVATE_USER: 'Deactivated user',
  USER_ORG_UPDATE: 'Updated access',
  POS_LINE_VOID: 'Voided register line',
  POS_EXCEPTION: 'POS exception',
};

const ENTITY_LABELS: Record<string, string> = {
  USER: 'User',
  USERS: 'User',
  USER_ROLES: 'User role',
  USER_WAREHOUSES: 'Warehouse access',
  INVITATION: 'Invitation',
  TENANT_SETTINGS: 'Workspace settings',
  POS_EXCEPTION: 'POS exception',
  POS_LINE: 'Register line',
};

export interface AuditFieldChange {
  field: string;
  from?: string;
  to?: string;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value);
}

export function formatActionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

export function formatEntityLabel(entityType: string): string {
  return ENTITY_LABELS[entityType] ?? entityType.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

export function actorLabel(row: Pick<AuditLogItem, 'actorDisplayName' | 'actorEmail' | 'actorUserId'>): string {
  return row.actorDisplayName || row.actorEmail || 'System';
}

export function formatFieldName(key: string): string {
  return key
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replaceAll('_', ' ')
    .replace(/\bid\b/gi, 'ID')
    .replace(/^\w/, (c) => c.toUpperCase());
}

export function formatAuditValue(value: unknown): string {
  if (value == null || value === '') return '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'number') return String(value);
  if (typeof value === 'string') {
    if (/^\d{4}-\d{2}-\d{2}T/.test(value)) {
      const parsed = Date.parse(value);
      if (!Number.isNaN(parsed)) return new Date(parsed).toLocaleString();
    }
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)) {
      return `${value.slice(0, 8)}…`;
    }
    return value;
  }
  if (Array.isArray(value)) {
    if (value.length === 0) return 'None';
    if (value.every((item) => typeof item === 'string' || typeof item === 'number')) {
      return value.map(String).join(', ');
    }
    return `${value.length} items`;
  }
  if (isRecord(value)) {
    const keys = Object.keys(value).filter((key) => !HIDDEN_KEYS.has(key));
    return keys.length === 0 ? 'Updated' : `${keys.length} fields`;
  }
  return String(value);
}

function changedFields(oldValue: unknown, newValue: unknown): AuditFieldChange[] {
  const oldRecord = isRecord(oldValue) ? oldValue : {};
  const newRecord = isRecord(newValue) ? newValue : {};
  const keys = new Set([...Object.keys(oldRecord), ...Object.keys(newRecord)]);
  const changes: AuditFieldChange[] = [];
  for (const key of keys) {
    if (HIDDEN_KEYS.has(key)) continue;
    const from = oldRecord[key];
    const to = newRecord[key];
    if (JSON.stringify(from) === JSON.stringify(to)) continue;
    changes.push({
      field: formatFieldName(key),
      from: from === undefined ? undefined : formatAuditValue(from),
      to: to === undefined ? undefined : formatAuditValue(to),
    });
  }
  return changes;
}

export function auditFieldChanges(diff: Record<string, unknown> | undefined): AuditFieldChange[] {
  if (!diff || Object.keys(diff).length === 0) return [];
  if (typeof diff.field === 'string' && (diff.from !== undefined || diff.to !== undefined)) {
    return [
      {
        field: formatFieldName(diff.field),
        from: diff.from === undefined ? undefined : formatAuditValue(diff.from),
        to: diff.to === undefined ? undefined : formatAuditValue(diff.to),
      },
    ];
  }
  if (diff.old !== undefined || diff.new !== undefined) {
    return changedFields(diff.old, diff.new);
  }
  if (isRecord(diff.args)) {
    return Object.entries(diff.args)
      .filter(([key]) => !HIDDEN_KEYS.has(key))
      .map(([key, value]) => ({ field: formatFieldName(key), to: formatAuditValue(value) }));
  }
  return Object.entries(diff)
    .filter(([key]) => !HIDDEN_KEYS.has(key) && !['source', 'table', 'op', 'method', 'resultType', 'requestId', 'actorUserId'].includes(key))
    .filter(([, value]) => value != null && typeof value !== 'object')
    .map(([key, value]) => ({ field: formatFieldName(key), to: formatAuditValue(value) }));
}

export function summarizeAuditDiff(diff: Record<string, unknown> | undefined, action?: string): string {
  if (!diff || Object.keys(diff).length === 0) {
    return 'No details recorded';
  }
  if (typeof diff.summary === 'string' && diff.summary.trim()) {
    return diff.summary.trim();
  }

  const op = typeof diff.op === 'string' ? diff.op.toUpperCase() : '';
  const newRecord = isRecord(diff.new) ? diff.new : undefined;
  const emailFromArgs = isRecord(diff.args) && typeof diff.args.email === 'string' ? diff.args.email : undefined;
  const email =
    (typeof diff.email === 'string' && diff.email) ||
    (typeof newRecord?.email === 'string' && newRecord.email) ||
    emailFromArgs;

  if (op === 'INSERT') {
    if (email) return `Added ${email}`;
    const name = typeof newRecord?.display_name === 'string' ? newRecord.display_name : typeof newRecord?.displayName === 'string' ? newRecord.displayName : undefined;
    if (name) return `Added ${name}`;
    return `Added a ${formatEntityLabel(String(diff.table ?? 'record')).toLowerCase()}`;
  }
  if (op === 'DELETE') {
    if (email) return `Removed ${email}`;
    return 'Removed this record';
  }
  if (op === 'UPDATE') {
    const fields = auditFieldChanges(diff).map((change) => change.field.toLowerCase());
    if (fields.length === 0) return 'Saved without visible field changes';
    if (fields.length === 1) return `Changed ${fields[0]}`;
    if (fields.length === 2) return `Changed ${fields[0]} and ${fields[1]}`;
    return `Changed ${fields.slice(0, 2).join(', ')}, and ${fields.length - 2} more`;
  }

  if (email && (action === 'INVITE_USER' || action === 'USER_INVITE' || action === 'RESEND_INVITATION')) {
    return `Invitation for ${email}`;
  }

  const fieldChanges = auditFieldChanges(diff);
  if (fieldChanges.length === 1) {
    const change = fieldChanges[0]!;
    if (change.from && change.to) return `Changed ${change.field.toLowerCase()} from ${change.from} to ${change.to}`;
    if (change.to) return `${change.field}: ${change.to}`;
  }
  if (fieldChanges.length > 1) {
    return fieldChanges
      .slice(0, 3)
      .map((change) => (change.to ? `${change.field}: ${change.to}` : change.field))
      .join(' · ');
  }
  return formatActionLabel(action ?? 'UPDATE');
}
