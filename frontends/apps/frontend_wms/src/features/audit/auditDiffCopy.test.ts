import { describe, expect, it } from 'vitest';
import {
  formatActionLabel,
  formatEntityLabel,
  formatLoginAuditLine,
  summarizeAuditDiff,
  auditFieldChanges,
} from './auditDiffCopy';

describe('auditDiffCopy', () => {
  it('maps trigger actions and entities to plain language', () => {
    expect(formatActionLabel('TG_INSERT')).toBe('Created');
    expect(formatActionLabel('INVITE_USER')).toBe('Sent invitation');
    expect(formatEntityLabel('USERS')).toBe('User');
    expect(formatEntityLabel('TENANT_SETTINGS')).toBe('Workspace settings');
  });

  it('summarizes a postgres insert of a user', () => {
    expect(
      summarizeAuditDiff({
        op: 'INSERT',
        table: 'users',
        new: { id: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', email: 'ing.arturo@demo.test', display_name: 'Arturo' },
      }),
    ).toBe('Added ing.arturo@demo.test');
  });

  it('summarizes field updates without JSON', () => {
    expect(
      summarizeAuditDiff({
        op: 'UPDATE',
        old: { email: 'a@demo.test', status: 'ACTIVE' },
        new: { email: 'a@demo.test', status: 'INACTIVE' },
      }),
    ).toBe('Changed status from Active to Inactive');
  });

  it('uses invitation email from args', () => {
    expect(
      summarizeAuditDiff({ args: { email: 'buyer@acme.test' } }, 'INVITE_USER'),
    ).toBe('Invitation for buyer@acme.test');
  });

  it('prefers an explicit summary', () => {
    expect(summarizeAuditDiff({ summary: 'Invitation created for a@b.test as OWNER' })).toBe(
      'Invitation created for a@b.test as OWNER',
    );
  });

  it('lists before/after field changes and hides ids', () => {
    const changes = auditFieldChanges({
      op: 'UPDATE',
      old: { id: 'x', email: 'old@demo.test', status: 'ACTIVE' },
      new: { id: 'x', email: 'new@demo.test', status: 'ACTIVE' },
    });
    expect(changes).toEqual([{ field: 'Email', from: 'old@demo.test', to: 'new@demo.test' }]);
  });

  it('reads BEFORE/AFTER snapshots and skips technical ids', () => {
    const changes = auditFieldChanges({
      op: 'UPDATE',
      BEFORE: {
        id: 'x',
        tenant_id: 't',
        status: 'ACTIVE',
        display_name: 'Retail B',
        updated_at: '2026-08-18T20:00:00Z',
      },
      AFTER: {
        id: 'x',
        tenant_id: 't',
        status: 'INACTIVE',
        display_name: 'Retail B',
        updated_at: '2026-08-18T20:42:00Z',
      },
    });
    expect(changes).toEqual([{ field: 'Status', from: 'Active', to: 'Inactive' }]);
  });

  it('formats login success and blocked CIDR events', () => {
    expect(formatActionLabel('LOGIN_SUCCESS')).toBe('Signed in');
    expect(formatActionLabel('LOGIN_BLOCKED_CIDR')).toBe('Blocked sign-in (off-network)');
    expect(
      summarizeAuditDiff(
        { ip: '198.51.100.45', location: 'Dallas, TX, US', summary: 'IP: 198.51.100.45 | Location: Dallas, TX, US' },
        'LOGIN_SUCCESS',
      ),
    ).toBe('Signed in from Dallas, TX, US');
    expect(
      formatLoginAuditLine({ ip: '198.51.100.45', location: 'Dallas, TX, US' }),
    ).toBe('198.51.100.45 • Dallas, TX, US');
    expect(
      summarizeAuditDiff(
        { ip: '203.0.113.40', location: 'Dallas, TX, US' },
        'LOGIN_BLOCKED_CIDR',
      ),
    ).toBe('Blocked sign-in from Dallas, TX, US');
    expect(
      auditFieldChanges({ ip: '198.51.100.45', location: 'Dallas, TX, US', summary: 'IP: x | Location: y' }),
    ).toEqual([]);
  });
});
