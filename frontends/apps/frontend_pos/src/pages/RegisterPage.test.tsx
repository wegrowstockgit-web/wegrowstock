import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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

const TEST_CATALOG = [
  {
    id: 'a0000000-0000-4000-8000-000000000701',
    upc: '7501234567890',
    sku: 'AGUA',
    name: 'Agua 600ml',
    price: 12.5,
    imageUrl: '/catalog/agua.svg',
  },
  {
    id: 'a0000000-0000-4000-8000-000000000702',
    upc: '049000042566',
    sku: 'COLA',
    name: 'Cola 355ml',
    price: 18,
    imageUrl: '/catalog/cola.svg',
  },
  {
    id: 'a0000000-0000-4000-8000-000000000703',
    upc: '022000001234',
    sku: 'BREAD',
    name: 'Bread loaf',
    price: 29.9,
    imageUrl: '/catalog/bread.svg',
  },
];

const mxSession = () => demoSession({ taxRegion: 'MX', currency: 'MXN', localeTag: 'es-MX', placeCurrency: 'MXN' });

describe('RegisterPage', () => {
  beforeEach(async () => {
    await db.products.bulkPut(TEST_CATALOG);
  });
  it('scans a UPC, adjusts qty, and checks out offline into the outbox', async () => {
    const user = userEvent.setup();
    renderRegister(mxSession());

    const search = await screen.findByTestId('pos-upc-search');
    await user.type(search, '7501234567890');
    await user.click(screen.getByTestId('pos-upc-add'));

    expect(await screen.findByTestId('cart-row-7501234567890')).toBeTruthy();
    await user.click(screen.getByLabelText('Increase Agua 600ml'));
    expect(screen.getByTestId('qty-7501234567890').textContent).toBe('2');

    await user.click(screen.getByTestId('solicitar-factura'));
    await user.type(screen.getByTestId('pos-factura-rfc'), 'XAXX010101000');
    await user.selectOptions(screen.getByTestId('pos-factura-uso'), 'G01');
    await user.click(screen.getByTestId('pos-factura-save'));
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
    expect(receipt?.facturaRfc).toBe('XAXX010101000');
    expect(receipt?.facturaUsoCfdi).toBe('G01');
    expect(receipt?.id.charAt(14)).toBe('7');
    expect(screen.queryByTestId('cart-row-7501234567890')).toBeNull();
  }, 15_000);

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
  }, 15_000);

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
    await user.click(screen.getByTestId('tender-next-dollar'));
    expect(await screen.findByTestId('pos-success-overlay')).toBeTruthy();
    const first = await db.outbox_receipts.toCollection().first();
    expect(first?.tenderType).toBe('CASH_NEXT_DOLLAR');
    await user.type(screen.getByTestId('pos-upc-search'), '7501234567890{Enter}');
    await user.click(await screen.findByTestId('tender-20'));
    expect((await db.outbox_receipts.toArray()).some((row) => row.tenderType === 'CASH_20')).toBe(true);
  }, 15_000);

  it('accepts tender digits from the physical keyboard', async () => {
    const user = userEvent.setup();
    renderRegister();
    await screen.findByTestId('pos-upc-search');
    await user.type(screen.getByTestId('pos-upc-search'), '7501234567890{Enter}');
    await screen.findByTestId('cart-row-7501234567890');
    await user.click(screen.getByTestId('numpad-C'));
    await user.keyboard('20.5');
    expect(screen.getByTestId('pos-tender-buffer').textContent).toMatch(/20\.50|20\.5/);
    await user.keyboard('{Backspace}{Backspace}');
    expect(screen.getByTestId('pos-tender-buffer').textContent).toMatch(/20/);
  }, 15_000);

  it('locks the register when Retail POS is not entitled', async () => {
    renderRegister(demoSession({ posEnabled: false, language: 'es', placeLanguage: 'es' }));
    expect(await screen.findByTestId('pos-locked')).toHaveTextContent(/POS no está activado/i);
    expect(screen.getByTestId('register-page')).toBeTruthy();
  });

  it('logs a LINE_VOID when a cashier removes a line', async () => {
    const user = userEvent.setup();
    renderRegister();
    await screen.findByTestId('pos-upc-search');
    await user.type(screen.getByTestId('pos-upc-search'), '7501234567890{Enter}');
    await screen.findByTestId('cart-row-7501234567890');
    await user.click(screen.getByTestId('cart-remove-7501234567890'));
    expect(screen.queryByTestId('cart-row-7501234567890')).toBeNull();
    await waitFor(async () => {
      expect(await db.audit_events.count()).toBe(1);
    });
    const event = await db.audit_events.toCollection().first();
    expect(event?.eventType).toBe('LINE_VOID');
    expect(event?.valueVoided).toBe(12.5);
    expect(event?.productId).toBeTruthy();
  });

  it('requires a manager PIN before voiding the transaction', async () => {
    const user = userEvent.setup();
    renderRegister();
    await screen.findByTestId('pos-upc-search');
    await user.type(screen.getByTestId('pos-upc-search'), '7501234567890{Enter}');
    await screen.findByTestId('cart-row-7501234567890');
    await user.click(screen.getByTestId('void-transaction'));
    const confirm = await screen.findByTestId('void-confirm-yes');
    expect(confirm).toBeDisabled();

    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    expect(await screen.findByTestId('void-pin-error')).toBeTruthy();
    expect(confirm).toBeDisabled();

    await user.click(screen.getByTestId('scanner-pin-back'));
    await user.click(screen.getByTestId('scanner-pin-back'));
    await user.click(screen.getByTestId('scanner-pin-back'));
    await user.click(screen.getByTestId('scanner-pin-back'));
    await user.click(screen.getByTestId('scanner-pin-digit-1'));
    await user.click(screen.getByTestId('scanner-pin-digit-2'));
    await user.click(screen.getByTestId('scanner-pin-digit-3'));
    await user.click(screen.getByTestId('scanner-pin-digit-4'));
    expect(confirm).toBeEnabled();
    await user.click(confirm);

    expect(screen.queryByTestId('cart-row-7501234567890')).toBeNull();
    await waitFor(async () => {
      expect(await db.audit_events.count()).toBe(1);
    });
    const event = await db.audit_events.toCollection().first();
    expect(event?.eventType).toBe('TX_VOID');
    expect(event?.managerOverrideId).toBe('a0000000-0000-4000-8000-000000000203');
    expect(event?.valueVoided).toBeGreaterThan(12.5);
  });

  it('renders Spanish cashier copy from organization language', async () => {
    renderRegister(demoSession({ language: 'es', localeTag: 'es-MX', currency: 'MXN' }));
    expect(await screen.findByTestId('pos-upc-add')).toHaveTextContent('Añadir');
    expect(screen.getByTestId('tender-exact')).toHaveTextContent(/Efectivo/i);
  });

  it('attaches a CRM customer to the ticket', async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/api/v1/pos/customers')) {
          return {
            ok: true,
            json: async () => [{ id: 'cust-1', name: 'Retail Partners LLC', email: 'ap@retailpartners.com' }],
          };
        }
        return { ok: false, status: 401, json: async () => ({}) };
      }),
    );
    renderRegister();
    await screen.findByTestId('pos-upc-search');
    await user.click(screen.getByTestId('pos-add-customer'));
    expect(await screen.findByTestId('pos-customer-modal')).toBeTruthy();
    await user.click(await screen.findByTestId('pos-customer-cust-1'));
    expect(screen.getByTestId('pos-add-customer')).toHaveTextContent('Retail Partners LLC');

    await user.type(screen.getByTestId('pos-upc-search'), '7501234567890{Enter}');
    await user.click(screen.getByTestId('tender-exact'));
    await waitFor(async () => {
      expect(await db.outbox_receipts.count()).toBe(1);
    });
    const receipt = await db.outbox_receipts.toCollection().first();
    expect(receipt?.customerId).toBe('cust-1');
    expect(receipt?.customerName).toBe('Retail Partners LLC');
  });

});
