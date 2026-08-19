import type { AuditLogItem } from '@/api/types';

const HIDDEN_KEYS = new Set([
  'id',
  'tenant_id',
  'tenantId',
  'password_hash',
  'passwordHash',
  'token_hash',
  'tokenHash',
  'terminal_pin_hash',
  'terminalPinHash',
  'created_at',
  'createdAt',
  'updated_at',
  'updatedAt',
  'last_login_at',
  'lastLoginAt',
  'version',
]);

const FIELD_LABELS: Record<string, string> = {
  status: 'Status',
  email: 'Email',
  display_name: 'Name',
  displayName: 'Name',
  phone: 'Phone',
  mfa_enabled: 'Two-factor authentication',
  mfaEnabled: 'Two-factor authentication',
  default_warehouse_id: 'Default warehouse',
  defaultWarehouseId: 'Default warehouse',
  locale_language: 'Language',
  localeLanguage: 'Language',
  timezone_preference: 'Time zone',
  timezonePreference: 'Time zone',
  must_change_password: 'Must change password',
  mustChangePassword: 'Must change password',
};

const ACTION_LABELS: Record<string, string> = {
  TG_INSERT: 'Created',
  TG_UPDATE: 'Updated',
  TS_UPDATE: 'Updated',
  TG_DELETE: 'Deleted',
  INVITE_USER: 'Sent invitation',
  USER_INVITE: 'Sent invitation',
  RESEND_INVITATION: 'Resent invitation',
  UPDATE_USER: 'Updated user',
  DEACTIVATE_USER: 'Deactivated user',
  USER_ORG_UPDATE: 'Updated access',
  POS_LINE_VOID: 'Voided register line',
  POS_EXCEPTION: 'POS exception',
  LOGIN_SUCCESS: 'Signed in',
  LOGIN_BLOCKED_CIDR: 'Blocked sign-in (off-network)',
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
  if (FIELD_LABELS[key]) return FIELD_LABELS[key];
  return key
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replaceAll('_', ' ')
    .replace(/\bid\b/gi, 'ID')
    .replace(/^\w/, (c) => c.toUpperCase());
}

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  SUSPENDED: 'Suspended',
  PENDING: 'Pending',
};

export function formatAuditValue(value: unknown): string {
  if (value == null || value === '') return '—';
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'number') return String(value);
  if (typeof value === 'string') {
    if (STATUS_LABELS[value.toUpperCase()]) return STATUS_LABELS[value.toUpperCase()]!;
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

function snapshot(diff: Record<string, unknown>, names: string[]): unknown {
  for (const name of names) {
    if (diff[name] !== undefined) return diff[name];
    const match = Object.keys(diff).find((key) => key.toLowerCase() === name.toLowerCase());
    if (match) return diff[match];
  }
  return undefined;
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
  const previous = snapshot(diff, ['old', 'from', 'previous', 'before']);
  const next = snapshot(diff, ['new', 'to', 'next', 'after']);
  if (previous !== undefined && next !== undefined) {
    return changedFields(previous, next);
  }
  if (isRecord(diff.args)) {
    return Object.entries(diff.args)
      .filter(([key]) => !HIDDEN_KEYS.has(key))
      .map(([key, value]) => ({ field: formatFieldName(key), to: formatAuditValue(value) }));
  }
  return Object.entries(diff)
    .filter(([key]) => !HIDDEN_KEYS.has(key) && !['source', 'table', 'op', 'method', 'resultType', 'requestId', 'actorUserId', 'ip', 'location', 'detail', 'summary'].includes(key))
    .filter(([, value]) => value != null && typeof value !== 'object')
    .map(([key, value]) => ({ field: formatFieldName(key), to: formatAuditValue(value) }));
}

export interface LoginAuditMeta {
  ip?: string;
  location?: string;
}

export function isLoginAuditAction(action?: string): boolean {
  return action === 'LOGIN_SUCCESS' || action === 'LOGIN_BLOCKED_CIDR';
}

export function extractLoginAuditMeta(diff: Record<string, unknown> | undefined): LoginAuditMeta {
  if (!diff) return {};
  const ip = typeof diff.ip === 'string' ? diff.ip.trim() : '';
  const location = typeof diff.location === 'string' ? diff.location.trim() : '';
  if (ip || location) {
    return { ip: ip || undefined, location: location || undefined };
  }
  const blob = [diff.detail, diff.summary].filter((value) => typeof value === 'string').join(' | ');
  const match = blob.match(/IP:\s*([^|]+?)\s*\|\s*Location:\s*(.+)$/i);
  if (!match) return {};
  return {
    ip: match[1]?.trim() || undefined,
    location: match[2]?.trim() || undefined,
  };
}

export function formatLoginAuditLine(diff: Record<string, unknown> | undefined): string {
  const { ip, location } = extractLoginAuditMeta(diff);
  if (ip && location) return `${ip} • ${location}`;
  return ip || location || '';
}

export function summarizeAuditDiff(diff: Record<string, unknown> | undefined, action?: string): string {
  if (isLoginAuditAction(action)) {
    const { location } = extractLoginAuditMeta(diff);
    if (action === 'LOGIN_BLOCKED_CIDR') {
      return location ? `Blocked sign-in from ${location}` : 'Blocked off-network sign-in';
    }
    return location ? `Signed in from ${location}` : 'Signed in';
  }
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
    const changes = auditFieldChanges(diff);
    if (changes.length === 0) return 'Saved this account';
    if (changes.length === 1) {
      const change = changes[0]!;
      if (change.from && change.to) {
        return `Changed ${change.field.toLowerCase()} from ${change.from} to ${change.to}`;
      }
      if (change.to) return `Set ${change.field.toLowerCase()} to ${change.to}`;
      return `Changed ${change.field.toLowerCase()}`;
    }
    if (changes.length === 2) {
      return `Changed ${changes[0]!.field.toLowerCase()} and ${changes[1]!.field.toLowerCase()}`;
    }
    return `Changed ${changes[0]!.field.toLowerCase()}, ${changes[1]!.field.toLowerCase()}, and ${changes.length - 2} more`;
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
