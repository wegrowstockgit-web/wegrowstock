import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Link2 } from 'lucide-react';
import { apiClient } from '@/api/client';
import type { StripeBillingStatus, TenantEmailDomain } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/Table';
import { CarrierCredentials } from '@/features/settings/CarrierCredentials';
import { LiveConnectionBadge } from '@/features/settings/LiveConnectionBadge';
import { cn } from '@/lib/utils';

function statusChip(status?: string) {
  const normalized = status ?? 'UNKNOWN';
  return (
    <span
      className={cn(
        'rounded-full px-2 py-0.5 text-xs font-medium',
        normalized === 'CONNECTED' || normalized === 'VERIFIED' || normalized === 'complete' || normalized === 'ACTIVE'
          ? 'bg-success/20 text-success'
          : normalized === 'PENDING'
            ? 'bg-warning/20 text-warning'
            : 'bg-surface-overlay text-text-muted',
      )}
    >
      {normalized}
    </span>
  );
}

/**
 * Surface A — Stripe Connect, shipping credentials, email domains.
 * Financial underwriting lives at /settings/fintech (not here).
 */
export function BillingSettingsPage() {
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();
  const [error, setError] = useState('');
  const [domainName, setDomainName] = useState('');

  const { data: stripeStatus, refetch: refetchStripe } = useQuery({
    queryKey: ['billing', 'stripe-status'],
    queryFn: async () =>
      (await apiClient.get<StripeBillingStatus>('/api/v1/billing/stripe/status')).data,
    retry: false,
  });

  const { data: emailDomains = [] } = useQuery({
    queryKey: ['email-domains'],
    queryFn: async () =>
      (await apiClient.get<TenantEmailDomain[]>('/api/v1/settings/email-domains')).data,
    retry: false,
  });

  useEffect(() => {
    if (searchParams.get('stripe') === 'success') {
      void apiClient.get('/api/v1/billing/stripe/refresh').then(() => {
        void refetchStripe();
        void queryClient.invalidateQueries({ queryKey: ['billing', 'stripe-status'] });
      });
    }
  }, [searchParams, refetchStripe, queryClient]);

  const connectMutation = useMutation({
    mutationFn: async () => {
      const res = await apiClient.get<{ url: string }>('/api/v1/billing/stripe/onboarding-url', {
        params: { returnUrl: `${window.location.origin}/settings/billing?stripe=success` },
      });
      return res.data.url;
    },
    onSuccess: (url) => {
      window.location.href = url;
    },
    onError: () => setError('Could not start Stripe onboarding. Try again.'),
  });

  const registerDomainMutation = useMutation({
    mutationFn: async () => {
      await apiClient.post('/api/v1/settings/email-domains', { domainName });
    },
    onSuccess: () => {
      setDomainName('');
      void queryClient.invalidateQueries({ queryKey: ['email-domains'] });
    },
  });

  const verifyDomainMutation = useMutation({
    mutationFn: async (id: string) => {
      await apiClient.post(`/api/v1/settings/email-domains/${id}/verify`);
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['email-domains'] }),
  });

  const stripeLive =
    stripeStatus?.onboardingStatus === 'ACTIVE' || stripeStatus?.onboardingStatus === 'complete';

  return (
    <div className="space-y-6 p-6" data-testid="billing-settings-page">
      <div>
        <Link
          to="/settings"
          className="mb-3 inline-flex items-center gap-1 text-sm font-medium text-text-muted hover:text-text"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
          All settings
        </Link>
        <h1 className="text-2xl font-bold text-text">Billing</h1>
        <p className="mt-1 text-sm text-text-muted">
          Stripe Connect onboarding, platform fees, and shipping account credentials
        </p>
      </div>

      <Card>
        <CardHeader title="Billing & payments" description="Stripe Connect and platform fees" />
        <div className="mb-4 flex flex-wrap items-center gap-2">
          {statusChip(stripeStatus?.onboardingStatus ?? 'NOT_CONNECTED')}
          {stripeLive && <LiveConnectionBadge />}
          {stripeStatus?.connectedAccountId && (
            <span className="font-mono text-xs text-text-muted">{stripeStatus.connectedAccountId}</span>
          )}
        </div>
        {stripeStatus?.capabilities && Object.keys(stripeStatus.capabilities).length > 0 && (
          <div className="mb-4 flex flex-wrap gap-2">
            {Object.entries(stripeStatus.capabilities).map(([key, value]) => (
              <span key={key} className="text-xs text-text-muted">
                {key}: {String(value)}
              </span>
            ))}
          </div>
        )}
        <p className="text-sm text-text-muted">
          Connect your Stripe account to receive invoice payments directly. Platform fees apply per
          your tenant settings.
        </p>
        {error && <p className="mt-2 text-sm text-danger">{error}</p>}
        <Button
          className="mt-4"
          loading={connectMutation.isPending}
          onClick={() => {
            setError('');
            connectMutation.mutate();
          }}
        >
          <Link2 className="h-4 w-4" />
          Connect Stripe
        </Button>
      </Card>

      <CarrierCredentials />

      <Card>
        <CardHeader title="Email domains" description="Custom domains for PO and invoice emails" />
        <form
          className="mb-4 flex flex-wrap gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            registerDomainMutation.mutate();
          }}
        >
          <Input
            label="Domain"
            value={domainName}
            onChange={(e) => setDomainName(e.target.value)}
            placeholder="mail.yourcompany.com"
            required
          />
          <div className="flex items-end">
            <Button type="submit" loading={registerDomainMutation.isPending}>
              Add domain
            </Button>
          </div>
        </form>
        {emailDomains.length === 0 ? (
          <p className="text-sm text-text-muted">No custom domains registered.</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Domain</TableHead>
                <TableHead>Status</TableHead>
                <TableHead align="right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {emailDomains.map((domain) => (
                <TableRow key={domain.id}>
                  <TableCell>{domain.domainName}</TableCell>
                  <TableCell>{statusChip(domain.verificationStatus)}</TableCell>
                  <TableCell align="right">
                    {domain.verificationStatus === 'PENDING' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => verifyDomainMutation.mutate(domain.id)}
                        loading={verifyDomainMutation.isPending}
                      >
                        Verify
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Card>
    </div>
  );
}
