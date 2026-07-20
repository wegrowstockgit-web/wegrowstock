import { describe, expect, it } from 'vitest';
import {
  actionBadgeLabel,
  fieldValue,
  formatConflictSummary,
  humanizeError,
  payloadBody,
} from './conflictSummary';
import type { ServerSyncConflict } from './syncConflictTypes';

function sample(overrides: Partial<ServerSyncConflict> = {}): ServerSyncConflict {
  return {
    id: 'c1',
    pickerDisplayName: 'Floor Picker',
    actionType: 'INBOUND_RECEIVE',
    actionLabel: 'Inbound Receive',
    errorMessage: 'BIN_FULL: allocated bin location is full',
    humanSummary: null,
    status: 'PENDING',
    payload: {
      url: '/api/v1/fulfillment/scan',
      body: { barcode: '123', quantity: 2, mode: 'receive' },
    },
    schemaMetadata: [
      {
        key: 'quantity',
        label: 'Corrected Quantity Count',
        type: 'number',
        mutable: true,
        constraints: { min: 1 },
      },
      {
        key: 'barcode',
        label: 'Scanned Item Master GTIN',
        type: 'string',
        mutable: false,
      },
    ],
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

describe('conflictSummary', () => {
  it('builds a human-readable summary without JSON jargon', () => {
    const text = formatConflictSummary(sample());
    expect(text).toContain('Floor Operator [Floor Picker]');
    expect(text).toContain('[Inbound Receive]');
    expect(text).toContain('allocated bin location is full');
    expect(text.toLowerCase()).not.toContain('payload_json');
    expect(text.toLowerCase()).not.toContain('schema_metadata');
  });

  it('prefers server humanSummary when present', () => {
    expect(formatConflictSummary(sample({ humanSummary: 'Server said hello.' }))).toBe(
      'Server said hello.',
    );
  });

  it('humanizeError strips ERROR_CODE prefixes', () => {
    expect(humanizeError('ATP_EXCEEDED: not enough stock')).toBe('not enough stock');
  });

  it('reads payload body and field values', () => {
    const row = sample();
    expect(payloadBody(row).barcode).toBe('123');
    expect(fieldValue(row, row.schemaMetadata![0])).toBe(2);
    expect(fieldValue(row, row.schemaMetadata![1])).toBe('123');
    expect(actionBadgeLabel(row)).toBe('Inbound Receive');
  });

  it('falls back to actionType labels and empty payload body', () => {
    expect(
      actionBadgeLabel(
        sample({ actionLabel: null, actionType: 'OUTBOUND_PICK' }),
      ),
    ).toBe('Outbound Pick');
    expect(actionBadgeLabel(sample({ actionLabel: null, actionType: 'UNKNOWN' }))).toBe(
      'Transaction',
    );
    expect(payloadBody(sample({ payload: { body: null } }))).toEqual({});
    expect(
      fieldValue(sample({ payload: { body: { quantity: 'x' } } }), {
        key: 'quantity',
        label: 'Qty',
        type: 'number',
        mutable: true,
      }),
    ).toBe('');
  });
});
