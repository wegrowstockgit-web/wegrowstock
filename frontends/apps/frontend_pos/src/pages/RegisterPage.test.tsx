import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { db } from '@/lib/db';
import { demoSession } from '@/lib/posSession';
import { PosSessionProvider } from '@/lib/PosSessionContext';
import { RegisterPage } from './RegisterPage';

function renderRegister(session = demoSession()) {
  return render(
    <MemoryRouter>
      <PosSessionProvider initial={session} disableFetch>
        <RegisterPage />
      </PosSessionProvider>
    </MemoryRouter>,
  );
}

describe('RegisterPage', () => {
  it('scans a UPC, adjusts qty, and checks out offline into the outbox', async () => {
    const user = userEvent.setup();
    renderRegister();

    const search = await screen.findByTestId('pos-upc-search');
    await user.type(search, '7501234567890');
    await user.click(screen.getByTestId('pos-upc-add'));

    expect(await screen.findByTestId('cart-row-7501234567890')).toBeTruthy();
    await user.click(screen.getByLabelText('Increase Agua 600ml'));
    expect(screen.getByTestId('qty-7501234567890').textContent).toBe('2');

    await user.click(screen.getByTestId('tax-MX'));
    await user.click(screen.getByTestId('numpad-5'));
    await user.click(screen.getByTestId('numpad-0'));
    expect(screen.getByTestId('pos-tender-buffer').textContent).toMatch(/50/);

    await user.click(screen.getByTestId('tender-exact'));
    expect(await screen.findByTestId('pos-success-overlay')).toHaveTextContent('SUCCESS - NEXT CUSTOMER');
    await waitFor(async () => {
      expect(await db.outbox_receipts.count()).toBe(1);
    });
    const receipt = await db.outbox_receipts.toCollection().first();
    expect(receipt?.lines[0]?.quantity).toBe(2);
    expect(receipt?.taxRegion).toBe('MX');
    expect(receipt?.id.charAt(14)).toBe('7');
    expect(screen.queryByTestId('cart-row-7501234567890')).toBeNull();
  });

  it('shows an error for unknown UPCs and empty checkout', async () => {
    const user = userEvent.setup();
    renderRegister();
    await screen.findByTestId('pos-upc-search');
    await user.type(screen.getByTestId('pos-upc-search'), '000');
    await user.click(screen.getByTestId('pos-upc-add'));
    expect(await screen.findByTestId('pos-scan-error')).toHaveTextContent('Unknown UPC');
    await user.click(screen.getByTestId('tender-20'));
    expect(screen.getByTestId('pos-scan-error')).toHaveTextContent('Scan an item first');
  });

  it('decrements qty to remove a line and accepts card tender', async () => {
    const user = userEvent.setup();
    renderRegister();
    await screen.findByTestId('pos-upc-search');
    await user.type(screen.getByTestId('pos-upc-search'), '049000042566{Enter}');
    expect(await screen.findByTestId('cart-row-049000042566')).toBeTruthy();
    await user.click(screen.getByLabelText('Decrease Cola 355ml'));
    expect(screen.queryByTestId('cart-row-049000042566')).toBeNull();

    await user.type(screen.getByTestId('pos-upc-search'), '022000001234{Enter}');
    await user.click(await screen.findByTestId('tender-card'));
    expect(await screen.findByTestId('pos-success-overlay')).toBeTruthy();
    expect(await db.outbox_receipts.count()).toBe(1);
  });

  it('edits the numpad buffer and accepts cash presets', async () => {
    const user = userEvent.setup();
    renderRegister();
    await screen.findByTestId('pos-upc-search');
    await user.type(screen.getByTestId('pos-upc-search'), '7501234567890{Enter}');
    await screen.findByTestId('cart-row-7501234567890');
    await user.click(screen.getByTestId('numpad-0'));
    await user.click(screen.getByTestId('numpad-1'));
    await user.click(screen.getByTestId('numpad-.'));
    await user.click(screen.getByTestId('numpad-.'));
    await user.click(screen.getByTestId('numpad-backspace'));
    await user.click(screen.getByTestId('numpad-C'));
    await user.click(screen.getByTestId('tender-50'));
    expect(await screen.findByTestId('pos-success-overlay')).toBeTruthy();
    const first = await db.outbox_receipts.toCollection().first();
    expect(first?.tenderType).toBe('CASH_50');
    await user.type(screen.getByTestId('pos-upc-search'), '7501234567890{Enter}');
    await user.click(await screen.findByTestId('tender-100'));
    expect((await db.outbox_receipts.toArray()).some((row) => row.tenderType === 'CASH_100')).toBe(true);
  });

  it('locks the register when Retail POS is not entitled', async () => {
    renderRegister(demoSession({ posEnabled: false, language: 'es', placeLanguage: 'es' }));
    expect(await screen.findByTestId('pos-locked')).toHaveTextContent(/POS no está activado/i);
    expect(screen.getByTestId('register-page')).toBeTruthy();
  });

  it('renders Spanish cashier copy from organization language', async () => {
    renderRegister(demoSession({ language: 'es', localeTag: 'es-MX', currency: 'MXN' }));
    expect(await screen.findByTestId('pos-upc-add')).toHaveTextContent('Añadir');
    expect(screen.getByTestId('tender-exact')).toHaveTextContent(/Efectivo/i);
  });
});
