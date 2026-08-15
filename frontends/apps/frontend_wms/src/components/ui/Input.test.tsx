import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Input } from './Input';

describe('Input', () => {
  it('gives distinct ids to fields that share the same label', () => {
    render(
      <form>
        <Input label="Street" defaultValue="billing" />
        <Input label="Street" defaultValue="shipping" />
      </form>,
    );

    const fields = screen.getAllByLabelText('Street');
    expect(fields).toHaveLength(2);
    expect(fields[0]).toHaveAttribute('id');
    expect(fields[1]).toHaveAttribute('id');
    expect(fields[0].id).not.toBe(fields[1].id);
    expect(fields[0]).toHaveAttribute('name', 'street');
    expect(fields[1]).toHaveAttribute('name', 'street');
  });

  it('honors explicit id and name props', () => {
    render(<Input id="customer-billing-street" name="billingStreet" label="Street" />);
    const field = screen.getByLabelText('Street');
    expect(field).toHaveAttribute('id', 'customer-billing-street');
    expect(field).toHaveAttribute('name', 'billingStreet');
  });
});
