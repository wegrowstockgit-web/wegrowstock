import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { CheckCircle2, Package } from 'lucide-react';
import { applyForWholesale } from '@/api/portal';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';

export function ShowroomApplyPage() {
  const [companyName, setCompanyName] = useState('');
  const [taxId, setTaxId] = useState('');
  const [contactName, setContactName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: () =>
      applyForWholesale({
        companyName,
        taxId,
        contactName,
        email,
        phone: phone || undefined,
      }),
    onError: () => setError('Could not submit your application. Check the fields and try again.'),
  });

  if (mutation.isSuccess) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-surface px-4" data-testid="showroom-apply-success">
        <Card className="w-full max-w-lg p-8 text-center">
          <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
          <h1 className="mt-4 text-2xl font-bold text-text">Application Submitted</h1>
          <p className="mt-2 text-sm text-text-muted">
            Under Review by our Wholesale Team
          </p>
          <p className="mt-4 text-sm text-text">
            We will email {email} with a welcome link once your account is approved.
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Link
              to="/showroom/catalog"
              className="inline-flex h-10 items-center rounded-md border border-border bg-surface-raised px-4 text-sm font-medium text-text hover:bg-surface-overlay"
            >
              Browse catalog
            </Link>
            <Link
              to="/showroom/login"
              className="inline-flex h-10 items-center rounded-md bg-accent px-4 text-sm font-medium text-text-inverse hover:bg-accent-hover"
            >
              Wholesale login
            </Link>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-surface px-4 py-10">
      <Card className="w-full max-w-lg p-8">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent text-text-inverse">
            <Package className="h-5 w-5" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-text">Apply for Wholesale</h1>
            <p className="text-sm text-text-muted">Self-serve B2B onboarding — no password required</p>
          </div>
        </div>

        <form
          className="space-y-4"
          data-testid="showroom-apply-form"
          onSubmit={(e) => {
            e.preventDefault();
            setError('');
            mutation.mutate();
          }}
        >
          <Input
            label="Company Name"
            value={companyName}
            onChange={(e) => setCompanyName(e.target.value)}
            required
            autoFocus
          />
          <Input
            label="Tax/VAT ID (RFC/EIN)"
            value={taxId}
            onChange={(e) => setTaxId(e.target.value)}
            required
            placeholder="XX-XXXXXXX"
          />
          <Input
            label="Contact Name"
            value={contactName}
            onChange={(e) => setContactName(e.target.value)}
            required
          />
          <Input
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />
          <Input
            label="Phone"
            type="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            autoComplete="tel"
          />
          {error && <p className="text-sm text-danger">{error}</p>}
          <Button type="submit" className="w-full" size="lg" loading={mutation.isPending}>
            Submit application
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-text-muted">
          Already approved?{' '}
          <Link to="/showroom/login" className="font-medium text-accent hover:underline">
            Sign in with a magic link
          </Link>
        </p>
      </Card>
    </div>
  );
}
