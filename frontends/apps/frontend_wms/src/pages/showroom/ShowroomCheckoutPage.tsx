import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { CreditCard, FileText, ShoppingBag } from 'lucide-react';
import { createPortalOrder, requestPortalQuote } from '@/api/portal';
import { apiClient } from '@/api/client';
import type { AllocationPolicy } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/EmptyState';
import { Input } from '@/components/ui/Input';
import { useShowroomCart } from '@/showroom/useShowroomCart';
import { useTranslation } from 'react-i18next';

function AllocationPreference({
  value,
  onChange,
}: {
  value: AllocationPolicy;
  onChange: (next: AllocationPolicy) => void;
}) {
  const { t } = useTranslation();
  return (
    <fieldset className="space-y-2">
      <legend className="text-sm font-medium text-text">{t('sales.allocationPreference')}</legend>
      <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-border bg-surface p-3">
        <input
          type="radio"
          name="allocation-policy"
          className="mt-1 accent-accent"
          checked={value === 'SHIP_COMPLETE'}
          onChange={() => onChange('SHIP_COMPLETE')}
        />
        <span>
          <span className="block text-sm font-medium text-text">{t('sales.shipComplete')}</span>
          <span className="text-xs text-text-muted">{t('sales.shipCompleteHelp')}</span>
        </span>
      </label>
      <label className="flex cursor-pointer items-start gap-3 rounded-lg border border-border bg-surface p-3">
        <input
          type="radio"
          name="allocation-policy"
          className="mt-1 accent-accent"
          checked={value === 'ALLOW_PARTIAL'}
          onChange={() => onChange('ALLOW_PARTIAL')}
        />
        <span>
          <span className="block text-sm font-medium text-text">{t('sales.splitShipment')}</span>
          <span className="text-xs text-text-muted">{t('sales.splitShipmentHelp')}</span>
        </span>
      </label>
    </fieldset>
  );
}

export function ShowroomCheckoutPage() {
  const navigate = useNavigate();
  const { cart, clearCart } = useShowroomCart();
  const [step, setStep] = useState<'review' | 'confirm' | 'done'>('review');
  const [doneKind, setDoneKind] = useState<'order' | 'quote'>('order');
  const [customerPo, setCustomerPo] = useState('');
  const [shipDate, setShipDate] = useState('');
  const [quoteNotes, setQuoteNotes] = useState('');
  const [allocationPolicy, setAllocationPolicy] = useState<AllocationPolicy>('ALLOW_PARTIAL');

  const { data: paymentTerms } = useQuery({
    queryKey: ['portal', 'payment-terms'],
    queryFn: async () => {
      const res = await apiClient.get<{ terms: string }>('/api/v1/portal/payment-terms');
      return res.data.terms ?? 'NET 30';
    },
    retry: false,
  });

  const { data: credit } = useQuery({
    queryKey: ['portal', 'credit'],
    queryFn: async () => {
      const res = await apiClient.get<{ availableCredit: number; creditLimit: number; status: string }>(
        '/api/v1/portal/credit'
      );
      return res.data;
    },
    retry: false,
  });

  const checkoutPayload = () => ({
    lines: cart.map((l) => ({
      variantId: l.item.id,
      quantity: l.quantity,
    })),
    customerPoNumber: customerPo || undefined,
    requestedShipDate: shipDate ? new Date(shipDate).toISOString() : undefined,
    allocationPolicy,
    quoteNotes: quoteNotes || undefined,
  });

  const placeOrderMutation = useMutation({
    mutationFn: async () => createPortalOrder(checkoutPayload()),
    onSuccess: () => {
      clearCart();
      setDoneKind('order');
      setStep('done');
    },
  });

  const requestQuoteMutation = useMutation({
    mutationFn: async () => requestPortalQuote(checkoutPayload()),
    onSuccess: () => {
      clearCart();
      setDoneKind('quote');
      setStep('done');
    },
  });

  const subtotal = cart.reduce((sum, l) => sum + l.item.unitPrice * l.quantity, 0);
  const currency = cart[0]?.item.currency ?? 'USD';
  const availableCredit = Number(credit?.availableCredit ?? 0);
  const overCredit = credit != null && subtotal > availableCredit;
  const pending = placeOrderMutation.isPending || requestQuoteMutation.isPending;

  if (cart.length === 0 && step !== 'done') {
    return (
      <EmptyState
        icon={ShoppingBag}
        title="Your cart is empty"
        description="Add items from the catalog before checking out."
        action={<Button onClick={() => navigate('/showroom/catalog')}>Browse catalog</Button>}
      />
    );
  }

  if (step === 'done') {
    return (
      <Card className="text-center" padding="lg">
        <ShoppingBag className="mx-auto mb-4 h-12 w-12 text-success" />
        <h2 className="text-xl font-bold text-text">
          {doneKind === 'quote' ? 'Quote requested' : 'Order submitted'}
        </h2>
        <p className="mt-2 text-sm text-text-muted">
          {doneKind === 'quote'
            ? 'A sales rep will review pricing and send a quote you can accept in one click.'
            : 'Your order has been placed and is pending confirmation.'}
        </p>
        <Button className="mt-6" onClick={() => navigate('/showroom/orders')}>
          View orders
        </Button>
      </Card>
    );
  }

  return (
    <div className="mx-auto max-w-lg">
      <h1 className="mb-6 text-2xl font-bold text-text">Checkout</h1>

      <div className="mb-4 flex gap-2">
        {(['review', 'confirm'] as const).map((s, i) => (
          <div
            key={s}
            className={`h-1 flex-1 rounded-full ${
              step === s || (step === 'confirm' && i === 0) ? 'bg-accent' : 'bg-border'
            }`}
          />
        ))}
      </div>

      {step === 'review' && (
        <Card>
          <CardHeader title="Order summary" description={`${cart.length} line items`} />
          <ul className="space-y-3">
            {cart.map((line) => (
              <li key={line.item.id} className="flex justify-between text-sm">
                <span>
                  {line.item.name} × {line.quantity}
                </span>
                <span className="font-mono">
                  {(line.item.unitPrice * line.quantity).toLocaleString(undefined, {
                    style: 'currency',
                    currency: line.item.currency,
                  })}
                </span>
              </li>
            ))}
          </ul>
          <div className="mt-4 space-y-3 border-t border-border pt-4">
            <Input
              label="Your PO number"
              value={customerPo}
              onChange={(e) => setCustomerPo(e.target.value)}
              placeholder="Optional — for your records"
            />
            <Input
              label="Requested ship date"
              type="date"
              value={shipDate}
              onChange={(e) => setShipDate(e.target.value)}
            />
            <AllocationPreference value={allocationPolicy} onChange={setAllocationPolicy} />
            <div className="flex flex-col gap-1.5">
              <label htmlFor="quote-notes" className="text-sm font-medium text-text">
                Quote notes
              </label>
              <textarea
                id="quote-notes"
                value={quoteNotes}
                onChange={(e) => setQuoteNotes(e.target.value)}
                placeholder="Optional — terms, target pricing, or delivery notes for your rep"
                rows={3}
                className="w-full rounded-md border border-border bg-surface-raised px-3 py-2 text-sm text-text placeholder:text-text-muted focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
            </div>
          </div>
          <div className="mt-4 flex justify-between font-semibold">
            <span>Subtotal</span>
            <span className="font-mono">
              {subtotal.toLocaleString(undefined, { style: 'currency', currency })}
            </span>
          </div>
          {credit && (
            <p className="mt-2 text-xs text-text-muted">
              Available credit:{' '}
              <span className="font-mono font-medium text-text">
                {availableCredit.toLocaleString(undefined, { style: 'currency', currency: 'USD' })}
              </span>
            </p>
          )}
          <Button className="mt-6 w-full" onClick={() => setStep('confirm')}>
            Continue
          </Button>
        </Card>
      )}

      {step === 'confirm' && (
        <Card>
          <CardHeader title="Payment terms" description="Review before submitting" />
          <div className="mb-4 flex items-center gap-3 rounded-lg border border-border bg-surface p-4">
            <CreditCard className="h-5 w-5 text-accent" />
            <div>
              <p className="font-medium text-text">{paymentTerms ?? 'NET 30'}</p>
              <p className="text-sm text-text-muted">Invoiced per your account terms</p>
            </div>
          </div>
          <p className="mb-4 text-sm text-text-muted">
            {allocationPolicy === 'SHIP_COMPLETE'
              ? 'Ship complete: the order holds until every line is in stock.'
              : 'Split shipment: available lines ship now; the rest is backordered.'}
          </p>
          {(customerPo || shipDate) && (
            <dl className="mb-4 space-y-1 text-sm">
              {customerPo && (
                <div className="flex justify-between">
                  <dt className="text-text-muted">PO</dt>
                  <dd className="font-mono text-text">{customerPo}</dd>
                </div>
              )}
              {shipDate && (
                <div className="flex justify-between">
                  <dt className="text-text-muted">Ship by</dt>
                  <dd className="text-text">{new Date(shipDate).toLocaleDateString()}</dd>
                </div>
              )}
            </dl>
          )}
          <p className="mb-2 text-sm text-text-muted">
            Total:{' '}
            <span className="font-mono font-semibold text-text">
              {subtotal.toLocaleString(undefined, { style: 'currency', currency })}
            </span>
          </p>
          {overCredit && (
            <p className="mb-4 text-sm text-danger" role="alert">
              This order exceeds your available credit. Reduce the cart or contact AR.
            </p>
          )}
          <div className="flex flex-col gap-3">
            <div className="flex gap-3">
              <Button variant="secondary" className="flex-1" onClick={() => setStep('review')}>
                Back
              </Button>
              <Button
                className="flex-1"
                loading={placeOrderMutation.isPending}
                disabled={overCredit || pending}
                onClick={() => placeOrderMutation.mutate()}
              >
                Instant Checkout
              </Button>
            </div>
            <Button
              variant="secondary"
              className="w-full"
              loading={requestQuoteMutation.isPending}
              disabled={pending}
              onClick={() => requestQuoteMutation.mutate()}
            >
              <FileText className="h-4 w-4" />
              Request Custom Quote
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}
