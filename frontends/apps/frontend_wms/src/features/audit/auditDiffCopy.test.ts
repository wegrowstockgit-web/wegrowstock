import { describe, expect, it } from 'vitest';
import {
  formatActionLabel,
  formatEntityLabel,
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
    ).toBe('Changed status');
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
});
