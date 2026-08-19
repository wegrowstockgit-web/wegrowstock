export type IntegrationProviderId =
  | 'QUICKBOOKS'
  | 'XERO'
  | 'NETSUITE'
  | 'SHOPIFY'
  | 'AMAZON'
  | 'AS2'
  | 'STRIPE';

export interface IntegrationProviderMeta {
  id: IntegrationProviderId;
  label: string;
  category: 'ECOMMERCE' | 'ACCOUNTING' | 'EDI' | 'PAYMENTS';
  signupUrl: string;
  pricing: string;
  oauth: boolean;
  mapping: boolean;
}

export const INTEGRATION_PROVIDERS: Record<IntegrationProviderId, IntegrationProviderMeta> = {
  QUICKBOOKS: {
    id: 'QUICKBOOKS',
    label: 'QuickBooks',
    category: 'ACCOUNTING',
    signupUrl: 'https://quickbooks.intuit.com/signup/',
    pricing: 'Simple Start from $35/mo — 30-day trial, no credit card for sandbox.',
    oauth: true,
    mapping: true,
  },
  XERO: {
    id: 'XERO',
    label: 'Xero',
    category: 'ACCOUNTING',
    signupUrl: 'https://www.xero.com/signup/',
    pricing: 'Early plan from $25/mo — includes a 30-day trial.',
    oauth: true,
    mapping: true,
  },
  NETSUITE: {
    id: 'NETSUITE',
    label: 'NetSuite',
    category: 'ACCOUNTING',
    signupUrl: 'https://www.netsuite.com/portal/resource/signup.shtml',
    pricing: 'SuiteSuccess editions are quoted per company. Ask your NetSuite AE for a sandbox.',
    oauth: true,
    mapping: false,
  },
  SHOPIFY: {
    id: 'SHOPIFY',
    label: 'Shopify',
    category: 'ECOMMERCE',
    signupUrl: 'https://www.shopify.com/free-trial',
    pricing: 'Basic from $39/mo — 3-day trial, then paid plan.',
    oauth: true,
    mapping: false,
  },
  AMAZON: {
    id: 'AMAZON',
    label: 'Amazon Seller Central',
    category: 'ECOMMERCE',
    signupUrl: 'https://sell.amazon.com/',
    pricing: 'Professional selling $39.99/mo plus referral fees.',
    oauth: true,
    mapping: false,
  },
  AS2: {
    id: 'AS2',
    label: 'AS2 Trading Partners',
    category: 'EDI',
    signupUrl: 'https://www.boomi.com/platform/edi/',
    pricing: 'Use your existing VAN or host AS2 certificates in Operations.',
    oauth: false,
    mapping: false,
  },
  STRIPE: {
    id: 'STRIPE',
    label: 'Stripe',
    category: 'PAYMENTS',
    signupUrl: 'https://dashboard.stripe.com/register',
    pricing: 'No monthly fee — 2.9% + 30¢ per successful card charge in the US.',
    oauth: true,
    mapping: false,
  },
};

export function providerMeta(id: string): IntegrationProviderMeta {
  const key = id.toUpperCase() as IntegrationProviderId;
  return INTEGRATION_PROVIDERS[key] ?? {
    id: key,
    label: id,
    category: 'ACCOUNTING',
    signupUrl: 'https://www.stripe.com/register',
    pricing: 'Create a provider account, then return here to connect.',
    oauth: false,
    mapping: false,
  };
}

export function hubStatusLabel(status: string, tokenExpiringSoon?: boolean): string {
  if (status === 'LIVE') return 'LIVE';
  if (status === 'ACTION_REQUIRED' || tokenExpiringSoon) {
    return tokenExpiringSoon ? 'ACTION REQUIRED / TOKEN EXPIRED' : 'ACTION REQUIRED';
  }
  return 'DISCONNECTED';
}
