import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { VoidConfirmModal } from '@/components/VoidConfirmModal';
import {
  clearCartDraft,
  enqueueReceipt,
  loadActiveOrderId,
  loadCartDraft,
  logPosEvent,
  lookupCatalog,
  saveCartDraft,
  type CartLine,
} from '@/lib/db';
import { seedDemoCatalogIfEmpty } from '@/lib/catalogSeed';
import { cartTotals, formatMoney, lineTotal } from '@/lib/tax';
import { uuidv7 } from '@/lib/uuidv7';
import { cashPresets } from '@/lib/locale';
import { nextDollarAmount } from '@/lib/utils';
import { usePosSession } from '@/lib/PosSessionContext';
import { seedDemoManagerPinsIfEmpty } from '@/offline/pinVault';

const DEMO_STORE_ID =
  import.meta.env.VITE_DEMO_STORE_LOCATION_ID ?? 'a0000000-0000-4000-8000-000000000601';

const NUMPAD = ['7', '8', '9', '4', '5', '6', '1', '2', '3', 'C', '0', '.'] as const;

const CFDI_USOS = [
  { code: 'G01', label: 'G01 — Adquisición de mercancías' },
  { code: 'G03', label: 'G03 — Gastos en general' },
  { code: 'S01', label: 'S01 — Sin efectos fiscales' },
  { code: 'D01', label: 'D01 — Honorarios médicos' },
] as const;

type PosCustomer = { id: string; name: string; email?: string | null };

export function RegisterPage() {
  const searchRef = useRef<HTMLInputElement>(null);
  const { session, t } = usePosSession();
  const [query, setQuery] = useState('');
  const [lines, setLines] = useState<CartLine[]>([]);
  const [tenderBuffer, setTenderBuffer] = useState('');
  const [scanError, setScanError] = useState('');
  const [success, setSuccess] = useState(false);
  const [ready, setReady] = useState(false);
  const [orderId, setOrderId] = useState('');
  const [voidOpen, setVoidOpen] = useState(false);
  const [customer, setCustomer] = useState<PosCustomer | null>(null);
  const [customerOpen, setCustomerOpen] = useState(false);
  const [customerQuery, setCustomerQuery] = useState('');
  const [customers, setCustomers] = useState<PosCustomer[]>([]);
  const [facturaOpen, setFacturaOpen] = useState(false);
  const [facturaRfc, setFacturaRfc] = useState('');
  const [facturaUso, setFacturaUso] = useState<(typeof CFDI_USOS)[number]['code']>('G03');
  const [facturaSaved, setFacturaSaved] = useState(false);

  const taxRegion = session.taxRegion;

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      seedDemoManagerPinsIfEmpty();
      await seedDemoCatalogIfEmpty();
      const draft = await loadCartDraft();
      const draftOrderId = await loadActiveOrderId();
      if (!cancelled) {
        setLines(draft);
        setOrderId(draftOrderId ?? '');
        setReady(true);
        searchRef.current?.focus();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!ready) return;
    void saveCartDraft(lines, orderId || undefined);
  }, [lines, orderId, ready]);

  useEffect(() => {
    if (!customerOpen) return;
    let cancelled = false;
    void (async () => {
      try {
        const response = await fetch('/api/v1/pos/customers', {
          credentials: 'include',
          headers: { Accept: 'application/json' },
        });
        if (!response.ok) return;
        const rows = (await response.json()) as PosCustomer[];
        if (!cancelled && Array.isArray(rows)) setCustomers(rows);
      } catch {
        /* Offline register still completes sales without CRM. */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [customerOpen]);

  const totals = useMemo(() => cartTotals(lines, taxRegion), [lines, taxRegion]);
  const currency = session.currency;
  const locale = session.localeTag;
  const money = (amount: number) => formatMoney(amount, currency, locale);
  const presets = cashPresets(currency);
  const locked = session.posEnabled === false;
  const nextDollar = nextDollarAmount(totals.grandTotal);
  const cashierId = session.cashierId || 'offline-cashier';
  const mxLocale = taxRegion === 'MX';

  const ensureOrderId = (current = orderId) => {
    if (current) return current;
    const next = uuidv7();
    setOrderId(next);
    return next;
  };

  const addUpc = async (raw: string) => {
    const upc = raw.trim();
    if (!upc) return;
    const item = await lookupCatalog(upc);
    if (!item) {
      setScanError(t('register.unknownUpc', { upc }));
      return;
    }
    setScanError('');
    ensureOrderId();
    setLines((prev) => {
      const existing = prev.find((line) => line.variantId === item.id);
      if (existing) {
        return prev.map((line) =>
          line.variantId === item.id ? { ...line, qty: line.qty + 1 } : line,
        );
      }
      return [
        ...prev,
        {
          variantId: item.id,
          upc: item.upc,
          name: item.name,
          unitPrice: item.unitPrice,
          qty: 1,
          imageUrl: item.imageUrl,
        },
      ];
    });
    setQuery('');
    searchRef.current?.focus();
  };

  const setQty = (variantId: string, qty: number) => {
    setLines((prev) => {
      if (qty <= 0) {
        const next = prev.filter((line) => line.variantId !== variantId);
        if (next.length === 0) setOrderId('');
        return next;
      }
      return prev.map((line) => (line.variantId === variantId ? { ...line, qty } : line));
    });
  };

  const removeLine = (line: CartLine) => {
    const activeOrderId = ensureOrderId();
    void logPosEvent({
      timestamp: Date.now(),
      cashierId,
      eventType: 'LINE_VOID',
      orderId: activeOrderId,
      productId: line.variantId,
      valueVoided: lineTotal(line.unitPrice, line.qty),
    });
    setLines((prev) => {
      const next = prev.filter((row) => row.variantId !== line.variantId);
      if (next.length === 0) setOrderId('');
      return next;
    });
  };

  const resetTicket = () => {
    setLines([]);
    setOrderId('');
    setTenderBuffer('');
    setScanError('');
    setCustomer(null);
    setFacturaRfc('');
    setFacturaUso('G03');
    setFacturaSaved(false);
    void clearCartDraft();
  };

  const voidTransaction = (managerId: string) => {
    if (!managerId) return;
    const activeOrderId = orderId || uuidv7();
    void logPosEvent({
      timestamp: Date.now(),
      cashierId,
      eventType: 'TX_VOID',
      orderId: activeOrderId,
      valueVoided: totals.grandTotal,
      managerOverrideId: managerId,
    });
    resetTicket();
    setVoidOpen(false);
    searchRef.current?.focus();
  };

  const pressKey = (key: string) => {
    if (key === 'C') {
      setTenderBuffer('');
      return;
    }
    if (key === '⌫') {
      setTenderBuffer((prev) => prev.slice(0, -1));
      return;
    }
    setTenderBuffer((prev) => {
      if (key === '.' && prev.includes('.')) return prev;
      if (prev === '0' && key !== '.') return key;
      return `${prev}${key}`;
    });
  };

  const completeSale = async (tenderType: string, amount: number) => {
    if (locked) return;
    if (lines.length === 0) {
      setScanError(t('register.scanFirst'));
      return;
    }
    const receipt = {
      id: uuidv7(),
      storeLocationId: localStorage.getItem('posStoreLocationId') || DEMO_STORE_ID,
      taxRegion,
      tenderType,
      tenderAmount: amount,
      lines: lines.map((line) => ({
        variantId: line.variantId,
        upc: line.upc,
        quantity: line.qty,
        unitPrice: line.unitPrice,
      })),
      createdAt: Date.now(),
      customerId: customer?.id,
      customerName: customer?.name,
      facturaRfc: facturaSaved ? facturaRfc.trim().toUpperCase() : undefined,
      facturaUsoCfdi: facturaSaved ? facturaUso : undefined,
    };
    await enqueueReceipt(receipt);
    resetTicket();
    setSuccess(true);
    window.setTimeout(() => setSuccess(false), 900);
    searchRef.current?.focus();
  };

  const parsedTender = Number.parseFloat(tenderBuffer || '0');
  const taxLabel = taxRegion === 'MX' ? t('register.taxMx') : t('register.taxUs');
  const mismatch =
    session.posEnabled &&
    session.tenantBaseCurrency &&
    session.tenantBaseCurrency !== session.currency
      ? t('register.currencyMismatch', { wms: session.tenantBaseCurrency, place: session.currency })
      : '';
  const filteredCustomers = customers.filter((row) => {
    const q = customerQuery.trim().toLowerCase();
    if (!q) return true;
    return `${row.name} ${row.email ?? ''}`.toLowerCase().includes(q);
  });

  return (
    <div className="pos-shell" data-testid="register-page">
      {locked ? (
        <div className="pos-locked" data-testid="pos-locked">
          <div className="pos-locked-card">
            <p className="pos-kicker">{session.tier || 'POS'}</p>
            <h1>{t('locked.title')}</h1>
            <p>{t('locked.body')}</p>
            <Link to="/login" className="pos-locked-link">
              {t('locked.signin')}
            </Link>
          </div>
        </div>
      ) : null}

      {success ? (
        <div className="pos-success" data-testid="pos-success-overlay" role="status">
          <p>{t('register.success')}</p>
        </div>
      ) : null}

      <header className="pos-topbar">
        <div className="min-w-0">
          <p className="pos-brand">{session.companyName || 'weGrowStock'}</p>
          <p className="pos-meta">
            {session.language.toUpperCase()} · {session.currency}
            {session.tenantBaseCurrency && session.tenantBaseCurrency !== session.currency
              ? ` · ${session.tenantBaseCurrency}@${session.liveExchangeRate}`
              : ''}
            {mismatch ? ` · ${mismatch}` : ''}
          </p>
        </div>
        <span className="pos-chip" data-testid="pos-connection">
          {typeof navigator !== 'undefined' && navigator.onLine === false
            ? t('register.offline')
            : t('register.online')}
        </span>
      </header>

      <div className="pos-body">
        <section className="pos-cart">
          <div className="pos-cart-tools">
            <button
              type="button"
              className="pos-add-customer"
              data-testid="pos-add-customer"
              onClick={() => setCustomerOpen(true)}
            >
              {customer ? customer.name : t('register.addCustomer')}
            </button>
            {customer ? (
              <button
                type="button"
                className="pos-clear-customer"
                data-testid="pos-clear-customer"
                onClick={() => setCustomer(null)}
              >
                {t('register.clearCustomer')}
              </button>
            ) : null}
          </div>
          <form
            className="pos-scan"
            onSubmit={(event) => {
              event.preventDefault();
              void addUpc(query);
            }}
          >
            <label className="sr-only" htmlFor="pos-upc-search">
              {t('register.scanLabel')}
            </label>
            <input
              id="pos-upc-search"
              ref={searchRef}
              data-testid="pos-upc-search"
              className="pos-scan-input"
              placeholder={t('register.scanPlaceholder')}
              inputMode="numeric"
              autoComplete="off"
              autoFocus
              value={query}
              onChange={(event) => setQuery(event.target.value)}
            />
            <button type="submit" data-testid="pos-upc-add" className="pos-scan-add">
              {t('register.add')}
            </button>
          </form>
          {scanError ? (
            <p className="pos-error" data-testid="pos-scan-error">
              {scanError}
            </p>
          ) : null}

          <div className="pos-cart-scroll">
            <table className="pos-table" data-testid="pos-cart-table">
              <thead>
                <tr>
                  <th>{t('register.item')}</th>
                  <th className="hidden sm:table-cell">{t('register.upc')}</th>
                  <th className="text-right">{t('register.unitPrice')}</th>
                  <th className="text-center">{t('register.qty')}</th>
                  <th className="text-right">{t('register.lineTotal')}</th>
                  <th className="text-right">{t('register.removeShort')}</th>
                </tr>
              </thead>
              <tbody>
                {lines.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="pos-empty">
                      {t('register.empty')}
                    </td>
                  </tr>
                ) : (
                  lines.map((line) => (
                    <tr key={line.variantId} data-testid={`cart-row-${line.upc}`}>
                      <td>
                        <div className="pos-item">
                          <div className="pos-thumb">
                            {line.imageUrl ? (
                              <img src={line.imageUrl} alt="" className="h-full w-full object-cover" />
                            ) : (
                              line.name.slice(0, 2).toUpperCase()
                            )}
                          </div>
                          <span className="pos-item-name">{line.name}</span>
                        </div>
                      </td>
                      <td className="hidden font-mono text-sm text-slate-500 sm:table-cell">{line.upc}</td>
                      <td className="text-right font-mono">{money(line.unitPrice)}</td>
                      <td>
                        <div className="pos-qty">
                          <button
                            type="button"
                            aria-label={t('register.decrease', { name: line.name })}
                            onClick={() => setQty(line.variantId, line.qty - 1)}
                          >
                            −
                          </button>
                          <span data-testid={`qty-${line.upc}`}>{line.qty}</span>
                          <button
                            type="button"
                            aria-label={t('register.increase', { name: line.name })}
                            className="pos-qty-plus"
                            onClick={() => setQty(line.variantId, line.qty + 1)}
                          >
                            +
                          </button>
                        </div>
                      </td>
                      <td className="text-right font-mono font-semibold">{money(lineTotal(line.unitPrice, line.qty))}</td>
                      <td className="text-right">
                        <button
                          type="button"
                          className="pos-remove"
                          data-testid={`cart-remove-${line.upc}`}
                          aria-label={t('register.remove', { name: line.name })}
                          onClick={() => removeLine(line)}
                        >
                          🗑️ {t('register.removeShort')}
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>

        <aside className="pos-tender">
          <div className="pos-tender-head">
            <p className="pos-kicker">{t('register.checkout')}</p>
            {mxLocale ? (
              <button
                type="button"
                data-testid="solicitar-factura"
                className={`pos-factura-btn${facturaSaved ? ' is-active' : ''}`}
                onClick={() => setFacturaOpen(true)}
              >
                {t('register.solicitarFactura')}
              </button>
            ) : (
              <div className="pos-tax" data-testid="tax-region">
                {taxRegion}
              </div>
            )}
          </div>

          <dl className="pos-totals">
            <div>
              <dt>{t('register.subtotal')}</dt>
              <dd data-testid="pos-subtotal">{money(totals.subtotal)}</dd>
            </div>
            <div className="pos-tax-line">
              <dt>{taxLabel}</dt>
              <dd data-testid="pos-tax">{money(totals.tax)}</dd>
            </div>
            <div className="pos-grand">
              <dt>{t('register.grandTotal')}</dt>
              <dd data-testid="pos-grand-total">{money(totals.grandTotal)}</dd>
            </div>
          </dl>

          <p className="pos-buffer" data-testid="pos-tender-buffer">
            {tenderBuffer ? money(parsedTender) : '—'}
          </p>

          <div className="pos-numpad" data-testid="pos-numpad">
            {NUMPAD.map((key) => (
              <button key={key} type="button" data-testid={`numpad-${key}`} onClick={() => pressKey(key)}>
                {key}
              </button>
            ))}
            <button type="button" data-testid="numpad-backspace" className="pos-backspace" onClick={() => pressKey('⌫')}>
              ⌫ {t('register.backspace')}
            </button>
          </div>

          <div className="pos-quick-tenders" data-testid="pos-quick-tenders">
            <button
              type="button"
              data-testid="tender-exact"
              className="pos-exact"
              onClick={() => void completeSale('EXACT_CASH', totals.grandTotal)}
            >
              {t('register.exactCash')}
            </button>
            <button
              type="button"
              data-testid="tender-next-dollar"
              className="pos-cash"
              onClick={() => void completeSale('CASH_NEXT_DOLLAR', nextDollar)}
            >
              {t('register.nextDollar')}
            </button>
            <button
              type="button"
              data-testid="tender-20"
              className="pos-cash"
              onClick={() => void completeSale(`CASH_${presets[0]}`, presets[0])}
            >
              {money(presets[0])}
            </button>
          </div>

          <div className="pos-actions">
            <button
              type="button"
              data-testid="tender-card"
              className="pos-card"
              onClick={() => void completeSale('CREDIT_CARD', totals.grandTotal)}
            >
              {t('register.card')}
            </button>
            <button
              type="button"
              data-testid="void-transaction"
              className="pos-void"
              disabled={lines.length === 0 || locked}
              onClick={() => setVoidOpen(true)}
            >
              {t('register.voidTransaction')}
            </button>
          </div>
        </aside>
      </div>

      {customerOpen ? (
        <div className="pos-void-overlay" data-testid="pos-customer-modal" role="dialog" aria-modal="true">
          <div className="pos-void-card">
            <h1>{t('register.addCustomer')}</h1>
            <input
              data-testid="pos-customer-search"
              className="pos-scan-input"
              placeholder={t('register.customerSearch')}
              value={customerQuery}
              onChange={(event) => setCustomerQuery(event.target.value)}
            />
            <ul className="pos-customer-list">
              {filteredCustomers.length === 0 ? (
                <li className="pos-empty">{t('register.noCustomers')}</li>
              ) : (
                filteredCustomers.map((row) => (
                  <li key={row.id}>
                    <button
                      type="button"
                      data-testid={`pos-customer-${row.id}`}
                      onClick={() => {
                        setCustomer(row);
                        setCustomerOpen(false);
                        setCustomerQuery('');
                      }}
                    >
                      {row.name}
                      {row.email ? <span>{row.email}</span> : null}
                    </button>
                  </li>
                ))
              )}
            </ul>
            <button type="button" data-testid="pos-customer-cancel" onClick={() => setCustomerOpen(false)}>
              {t('register.facturaCancel')}
            </button>
          </div>
        </div>
      ) : null}

      {facturaOpen ? (
        <div className="pos-void-overlay" data-testid="pos-factura-modal" role="dialog" aria-modal="true">
          <div className="pos-void-card">
            <h1>{t('register.facturaTitle')}</h1>
            <label>
              {t('register.facturaRfc')}
              <input
                data-testid="pos-factura-rfc"
                value={facturaRfc}
                autoCapitalize="characters"
                onChange={(event) => setFacturaRfc(event.target.value)}
              />
            </label>
            <label>
              {t('register.facturaUso')}
              <select
                data-testid="pos-factura-uso"
                value={facturaUso}
                onChange={(event) => setFacturaUso(event.target.value as (typeof CFDI_USOS)[number]['code'])}
              >
                {CFDI_USOS.map((uso) => (
                  <option key={uso.code} value={uso.code}>
                    {uso.label}
                  </option>
                ))}
              </select>
            </label>
            <div className="pos-void-actions">
              <button type="button" data-testid="pos-factura-cancel" onClick={() => setFacturaOpen(false)}>
                {t('register.facturaCancel')}
              </button>
              <button
                type="button"
                data-testid="pos-factura-save"
                disabled={facturaRfc.trim().length < 12}
                onClick={() => {
                  setFacturaSaved(true);
                  setFacturaOpen(false);
                }}
              >
                {t('register.facturaSave')}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      <VoidConfirmModal
        open={voidOpen}
        cartValueLabel={t('register.voidValue', { amount: money(totals.grandTotal) })}
        title={t('register.voidTitle')}
        body={t('register.voidBody')}
        pinTitle={t('register.voidPin')}
        pinHint={t('register.voidPinHint')}
        invalidPin={t('register.voidInvalidPin')}
        confirmLabel={t('register.voidConfirm')}
        cancelLabel={t('register.voidCancel')}
        onCancel={() => setVoidOpen(false)}
        onConfirm={voidTransaction}
      />
    </div>
  );
}
