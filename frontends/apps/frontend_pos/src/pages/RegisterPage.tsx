import { useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  clearCartDraft,
  enqueueReceipt,
  loadCartDraft,
  lookupCatalog,
  saveCartDraft,
  type CartLine,
} from '@/lib/db';
import { seedDemoCatalogIfEmpty } from '@/lib/catalogSeed';
import { cartTotals, formatMoney, lineTotal, type TaxRegion } from '@/lib/tax';
import { uuidv7 } from '@/lib/uuidv7';
import { cashPresets } from '@/lib/locale';
import { usePosSession } from '@/lib/PosSessionContext';

const DEMO_STORE_ID =
  import.meta.env.VITE_DEMO_STORE_LOCATION_ID ?? 'a0000000-0000-4000-8000-000000000601';

const NUMPAD = ['7', '8', '9', '4', '5', '6', '1', '2', '3', 'C', '0', '.'] as const;

export function RegisterPage() {
  const searchRef = useRef<HTMLInputElement>(null);
  const { session, t } = usePosSession();
  const [query, setQuery] = useState('');
  const [lines, setLines] = useState<CartLine[]>([]);
  const [taxRegion, setTaxRegion] = useState<TaxRegion>(session.taxRegion);
  const [tenderBuffer, setTenderBuffer] = useState('');
  const [scanError, setScanError] = useState('');
  const [success, setSuccess] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setTaxRegion(session.taxRegion);
  }, [session.taxRegion]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      await seedDemoCatalogIfEmpty();
      const draft = await loadCartDraft();
      if (!cancelled) {
        setLines(draft);
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
    void saveCartDraft(lines);
  }, [lines, ready]);

  const totals = useMemo(() => cartTotals(lines, taxRegion), [lines, taxRegion]);
  const currency = session.currency;
  const locale = session.localeTag;
  const money = (amount: number) => formatMoney(amount, currency, locale);
  const presets = cashPresets(currency);
  const locked = session.posEnabled === false;

  const addUpc = async (raw: string) => {
    const upc = raw.trim();
    if (!upc) return;
    const item = await lookupCatalog(upc);
    if (!item) {
      setScanError(t('register.unknownUpc', { upc }));
      return;
    }
    setScanError('');
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
      if (qty <= 0) return prev.filter((line) => line.variantId !== variantId);
      return prev.map((line) => (line.variantId === variantId ? { ...line, qty } : line));
    });
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
    };
    await enqueueReceipt(receipt);
    setLines([]);
    setTenderBuffer('');
    setScanError('');
    void clearCartDraft();
    setSuccess(true);
    window.setTimeout(() => setSuccess(false), 900);
    searchRef.current?.focus();
  };

  const parsedTender = Number.parseFloat(tenderBuffer || '0');
  const taxLabel = taxRegion === 'MX' ? t('register.taxMx') : t('register.taxUs');
  const mismatch =
    session.posEnabled &&
    session.placeCurrency &&
    session.placeCurrency !== session.currency
      ? t('register.currencyMismatch', { wms: session.currency, place: session.placeCurrency })
      : '';

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
        <div
          className="pos-success"
          data-testid="pos-success-overlay"
          role="status"
        >
          <p>{t('register.success')}</p>
        </div>
      ) : null}

      <header className="pos-topbar">
        <div className="min-w-0">
          <p className="pos-brand">{session.companyName || 'weGrowStock'}</p>
          <p className="pos-meta">
            {session.language.toUpperCase()} · {session.currency}
            {mismatch ? ` · ${mismatch}` : ''}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="pos-chip" data-testid="pos-connection">
            {typeof navigator !== 'undefined' && navigator.onLine === false
              ? t('register.offline')
              : t('register.online')}
          </span>
          <Link to="/login" className="pos-signin" data-testid="pos-signin">
            {t('register.signIn')}
          </Link>
        </div>
      </header>

      <div className="pos-body">
        <section className="pos-cart">
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
                </tr>
              </thead>
              <tbody>
                {lines.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="pos-empty">
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
            <div className="pos-tax" data-testid="tax-region">
              {(['US', 'MX'] as const).map((region) => (
                <button
                  key={region}
                  type="button"
                  data-testid={`tax-${region}`}
                  className={taxRegion === region ? 'is-active' : ''}
                  onClick={() => setTaxRegion(region)}
                >
                  {region === 'US' ? t('register.taxUsShort') : t('register.taxMxShort')}
                </button>
              ))}
            </div>
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

          <div className="pos-actions">
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
              data-testid="tender-20"
              className="pos-cash"
              onClick={() => void completeSale(`CASH_${presets[0]}`, presets[0])}
            >
              {money(presets[0])}
            </button>
            <button
              type="button"
              data-testid="tender-50"
              className="pos-cash"
              onClick={() => void completeSale(`CASH_${presets[1]}`, presets[1])}
            >
              {money(presets[1])}
            </button>
            <button
              type="button"
              data-testid="tender-100"
              className="pos-cash"
              onClick={() => void completeSale(`CASH_${presets[2]}`, presets[2])}
            >
              {money(presets[2])}
            </button>
            <button
              type="button"
              data-testid="tender-card"
              className="pos-card"
              onClick={() => void completeSale('CREDIT_CARD', totals.grandTotal)}
            >
              {t('register.card')}
            </button>
          </div>
        </aside>
      </div>
    </div>
  );
}
