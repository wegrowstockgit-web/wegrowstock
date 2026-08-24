import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Plus, Trash2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { CustomerPriceTier } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { RightPeekDrawer } from '@/components/ui/RightPeekDrawer';
import { PriceTierCombobox } from '@/features/sales/PriceTierCombobox';
import { extractApiError } from '@/lib/apiClient';
import { cn } from '@/lib/utils';

type Tab = 'company' | 'financials' | 'addresses' | 'portal';

interface ShipRow {
  key: string;
  label: string;
  street: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

function emptyShip(label: string): ShipRow {
  return {
    key: crypto.randomUUID(),
    label,
    street: '',
    city: '',
    state: '',
    postalCode: '',
    country: 'US',
  };
}

export function NewCustomerDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('company');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [taxId, setTaxId] = useState('');
  const [status, setStatus] = useState('ACTIVE');
  const [paymentTerms, setPaymentTerms] = useState('NET30');
  const [creditLimit, setCreditLimit] = useState('');
  const [currency, setCurrency] = useState('USD');
  const [priceTierId, setPriceTierId] = useState('');
  const [priceTierName, setPriceTierName] = useState('');
  const [taxExempt, setTaxExempt] = useState(false);
  const [billStreet, setBillStreet] = useState('');
  const [billCity, setBillCity] = useState('');
  const [billState, setBillState] = useState('');
  const [billPostal, setBillPostal] = useState('');
  const [billCountry, setBillCountry] = useState('US');
  const [ships, setShips] = useState<ShipRow[]>([emptyShip('Primary warehouse')]);
  const [provisionShowroom, setProvisionShowroom] = useState(false);
  const [error, setError] = useState('');

  const reset = () => {
    setTab('company');
    setName('');
    setEmail('');
    setPhone('');
    setTaxId('');
    setStatus('ACTIVE');
    setPaymentTerms('NET30');
    setCreditLimit('');
    setCurrency('USD');
    setPriceTierId('');
    setPriceTierName('');
    setTaxExempt(false);
    setBillStreet('');
    setBillCity('');
    setBillState('');
    setBillPostal('');
    setBillCountry('US');
    setShips([emptyShip('Primary warehouse')]);
    setProvisionShowroom(false);
    setError('');
  };

  const mutation = useMutation({
    mutationFn: async () => {
      const billingAddress = {
        street: billStreet || undefined,
        city: billCity || undefined,
        state: billState || undefined,
        postalCode: billPostal || undefined,
        country: billCountry || undefined,
      };
      const shippingAddresses = ships
        .filter((row) => row.street || row.city)
        .map((row) => ({
          label: row.label,
          street: row.street || undefined,
          city: row.city || undefined,
          state: row.state || undefined,
          postalCode: row.postalCode || undefined,
          country: row.country || undefined,
        }));
      await apiClient.post('/api/v1/customers', {
        name,
        email: email || undefined,
        phone: phone || undefined,
        taxId: taxId || undefined,
        ein: taxId || undefined,
        paymentTerms,
        creditLimit: creditLimit ? Number(creditLimit) : undefined,
        currencyPreference: currency,
        customerStatus: status,
        priceTierId: priceTierId || undefined,
        taxExempt,
        billingAddress,
        shippingAddress: shippingAddresses[0] ?? billingAddress,
        shippingAddresses,
        provisionShowroom,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
      reset();
      onClose();
    },
    onError: (err) => setError(extractApiError(err, 'Could not create customer.')),
  });

  const tabs: Array<{ id: Tab; label: string }> = [
    { id: 'company', label: 'Company Details' },
    { id: 'financials', label: 'Financials & Pricing' },
    { id: 'addresses', label: 'Address Book' },
    { id: 'portal', label: 'B2B Portal Onboarding' },
  ];

  return (
    <RightPeekDrawer open={open} onClose={onClose} title="Add customer" width="lg">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          setError('');
          if (provisionShowroom && !email) {
            setError('Primary contact email is required to provision showroom access.');
            setTab('company');
            return;
          }
          mutation.mutate();
        }}
        className="flex h-full min-h-0 flex-col"
        data-testid="add-customer-form"
      >
        <div className="mb-4 flex flex-wrap gap-1" role="tablist" aria-label="New customer sections">
          {tabs.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={tab === item.id}
              data-testid={`add-customer-tab-${item.id}`}
              className={cn(
                'rounded-md px-3 py-1.5 text-sm font-medium',
                tab === item.id ? 'bg-accent-muted text-accent' : 'text-text-muted hover:bg-surface-overlay',
              )}
              onClick={() => setTab(item.id)}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div className="min-h-0 flex-1 space-y-4 overflow-auto pr-1">
          {tab === 'company' && (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Input
                label="Company Name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                required
                autoFocus
              />
              <Input
                label="Primary Contact Email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
              <Input label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
              <Input
                label="Tax ID / EIN"
                value={taxId}
                onChange={(e) => setTaxId(e.target.value)}
                placeholder="XX-XXXXXXX"
              />
              <Select label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
                <option value="ACTIVE">Active</option>
                <option value="HOLD">Hold</option>
                <option value="PROSPECT">Prospect</option>
              </Select>
            </div>
          )}

          {tab === 'financials' && (
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Select label="Currency" value={currency} onChange={(e) => setCurrency(e.target.value)}>
                {['USD', 'EUR', 'GBP', 'CAD'].map((code) => (
                  <option key={code} value={code}>
                    {code}
                  </option>
                ))}
              </Select>
              <Select
                label="Payment Terms"
                value={paymentTerms}
                onChange={(e) => setPaymentTerms(e.target.value)}
              >
                <option value="NET30">Net 30</option>
                <option value="NET60">Net 60</option>
                <option value="DUE_ON_RECEIPT">Due on receipt</option>
              </Select>
              <Input
                label="Credit Limit"
                type="number"
                min={0}
                value={creditLimit}
                onChange={(e) => setCreditLimit(e.target.value)}
              />
              <PriceTierCombobox
                value={priceTierId}
                selectedLabel={priceTierName}
                onSelect={(tier: CustomerPriceTier | null) => {
                  setPriceTierId(tier?.id ?? '');
                  setPriceTierName(tier?.name ?? '');
                }}
              />
              <label className="flex items-center gap-2 text-sm text-text sm:col-span-2">
                <input
                  type="checkbox"
                  checked={taxExempt}
                  onChange={(e) => setTaxExempt(e.target.checked)}
                  data-testid="customer-tax-exempt"
                />
                Tax exempt
              </label>
            </div>
          )}

          {tab === 'addresses' && (
            <div className="space-y-4">
              <fieldset className="space-y-3 rounded-md border border-border p-3">
                <legend className="px-1 text-sm font-medium text-text">Billing Address</legend>
                <Input
                  id="customer-billing-street"
                  label="Street"
                  value={billStreet}
                  onChange={(e) => setBillStreet(e.target.value)}
                />
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <Input label="City" value={billCity} onChange={(e) => setBillCity(e.target.value)} />
                  <Input label="State" value={billState} onChange={(e) => setBillState(e.target.value)} />
                  <Input label="Postal" value={billPostal} onChange={(e) => setBillPostal(e.target.value)} />
                  <Input label="Country" value={billCountry} onChange={(e) => setBillCountry(e.target.value)} />
                </div>
              </fieldset>
              {ships.map((row, index) => (
                <fieldset key={row.key} className="space-y-3 rounded-md border border-border p-3">
                  <legend className="px-1 text-sm font-medium text-text">
                    Shipping Address {index + 1}
                  </legend>
                  <Input
                    label="Location label"
                    value={row.label}
                    onChange={(e) =>
                      setShips((prev) =>
                        prev.map((item) =>
                          item.key === row.key ? { ...item, label: e.target.value } : item,
                        ),
                      )
                    }
                  />
                  <Input
                    label="Street"
                    value={row.street}
                    onChange={(e) =>
                      setShips((prev) =>
                        prev.map((item) =>
                          item.key === row.key ? { ...item, street: e.target.value } : item,
                        ),
                      )
                    }
                  />
                  <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                    <Input
                      label="City"
                      value={row.city}
                      onChange={(e) =>
                        setShips((prev) =>
                          prev.map((item) =>
                            item.key === row.key ? { ...item, city: e.target.value } : item,
                          ),
                        )
                      }
                    />
                    <Input
                      label="State"
                      value={row.state}
                      onChange={(e) =>
                        setShips((prev) =>
                          prev.map((item) =>
                            item.key === row.key ? { ...item, state: e.target.value } : item,
                          ),
                        )
                      }
                    />
                  </div>
                  {ships.length > 1 && (
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => setShips((prev) => prev.filter((item) => item.key !== row.key))}
                    >
                      <Trash2 className="h-4 w-4" />
                      Remove location
                    </Button>
                  )}
                </fieldset>
              ))}
              <Button
                type="button"
                variant="secondary"
                size="sm"
                data-testid="customer-add-shipping"
                onClick={() => setShips((prev) => [...prev, emptyShip(`Store #${prev.length}`)])}
              >
                <Plus className="h-4 w-4" />
                Add shipping address
              </Button>
            </div>
          )}

          {tab === 'portal' && (
            <label className="flex items-start gap-3 rounded-md border border-border p-3 text-sm text-text">
              <input
                type="checkbox"
                checked={provisionShowroom}
                onChange={(e) => setProvisionShowroom(e.target.checked)}
                data-testid="customer-provision-showroom"
                className="mt-1"
              />
              <span className="font-medium">Provision B2B Showroom Access</span>
            </label>
          )}
        </div>

        {error && <p className="mt-3 text-sm text-danger">{error}</p>}
        <div className="mt-4 flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" loading={mutation.isPending} data-testid="add-customer-submit" disabled={!name}>
            Add customer
          </Button>
        </div>
      </form>
    </RightPeekDrawer>
  );
}
