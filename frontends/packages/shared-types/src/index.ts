/** Commercial AppModule identifiers — keep in sync with backend AppModule enum. */
export const APP_MODULES = [
  'CORE',
  'SHOPIFY',
  'ACCOUNTING',
  'ADVANCED_FULFILLMENT',
  'MANUFACTURING',
  'DOCUMENTS',
  'MRP',
  'B2B_SHOWROOM',
  'FINTECH',
  'MESH_NETWORK',
  'RTLS_TELEMETRY',
  'AI_COPILOT',
  'RETAIL_POS',
] as const;

export type AppModule = (typeof APP_MODULES)[number];

export type CommercialTier = 'BASIC' | 'INTERMEDIATE' | 'ENTERPRISE';

export const COMMERCIAL_TIERS: CommercialTier[] = ['BASIC', 'INTERMEDIATE', 'ENTERPRISE'];

export type ControlPlaneTenant = {
  tenantId: string;
  name: string;
  slug: string;
  status: string;
  tier: CommercialTier;
  enabledModules: AppModule[];
};

export const MODULE_LABELS: Record<AppModule, string> = {
  CORE: 'Core WMS',
  SHOPIFY: 'Shopify',
  ACCOUNTING: 'Accounting (QB/Xero)',
  ADVANCED_FULFILLMENT: 'Advanced Fulfillment',
  MANUFACTURING: 'Manufacturing',
  DOCUMENTS: 'Documents / PDFs',
  MRP: 'MRP',
  B2B_SHOWROOM: 'B2B Showroom',
  FINTECH: 'Fintech',
  MESH_NETWORK: 'Mesh Network',
  RTLS_TELEMETRY: 'RTLS Telemetry',
  AI_COPILOT: 'AI Copilot',
  RETAIL_POS: 'Retail Point of Sale (POS)',
};

export const MODULE_TIER: Record<AppModule, 1 | 2 | 3> = {
  CORE: 1,
  SHOPIFY: 2,
  ACCOUNTING: 2,
  ADVANCED_FULFILLMENT: 2,
  MANUFACTURING: 2,
  DOCUMENTS: 2,
  MRP: 2,
  B2B_SHOWROOM: 3,
  FINTECH: 3,
  MESH_NETWORK: 3,
  RTLS_TELEMETRY: 3,
  AI_COPILOT: 3,
  RETAIL_POS: 3,
};

export const TIER_LABELS: Record<CommercialTier, string> = {
  BASIC: 'Basic',
  INTERMEDIATE: 'Intermediate',
  ENTERPRISE: 'Enterprise',
};

export type PlatformTierDefinition = {
  tierCode: CommercialTier;
  displayName: string;
  defaultModules: AppModule[];
  updatedAt: string;
};

export type FeatureFlagDto = {
  id: string;
  flagKey: string;
  description: string | null;
  isGlobal: boolean;
  createdAt: string;
  tenants?: TenantFeatureFlagOverrideDto[];
};

export type TenantFeatureFlagOverrideDto = {
  tenantId: string;
  enabled: boolean;
};

export type TenantThrottleRequestDto = {
  tenantId: string;
  customRateLimit?: number;
  isThrottled: boolean;
};
