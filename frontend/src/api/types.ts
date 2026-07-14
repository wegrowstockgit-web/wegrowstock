export interface User {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tenantId: string;
  userId: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
}

/** @deprecated use TokenResponse */
export type LoginResponse = TokenResponse;

export interface SignupRequest {
  companyName: string;
  slug: string;
  email: string;
  password: string;
  displayName: string;
}

export interface AcceptInviteRequest {
  token: string;
  password: string;
  displayName: string;
}

export interface Warehouse {
  id: string;
  name: string;
  code: string;
}

export interface TenantLocation {
  id: string;
  parentLocationId?: string;
  type: string;
  code: string;
  name: string;
  path: string;
}

export interface TenantUser {
  id: string;
  email: string;
  displayName: string;
  status: string;
  roles: string[];
}

export type TenantSettingsMap = Record<string, unknown>;

export interface ProductVariant {
  id: string;
  sku: string;
  name: string;
  barcode?: string;
  attributes?: Record<string, string>;
  onHand: number;
  allocated: number;
  atp: number;
  price?: number;
  currency?: string;
  externalSyncEnabled?: boolean;
  weight?: number;
  weightUnit?: string;
  length?: number;
  width?: number;
  height?: number;
  dimUnit?: string;
  defaultSupplierId?: string;
  supplierLeadTimeDays?: number;
  defaultLocationId?: string;
  isKit?: boolean;
  dims?: Record<string, unknown>;
  reorderPoint?: number;
  reorderQty?: number;
  primaryMediaUrl?: string | null;
}

export interface VariantUomConversion {
  id: string;
  variantId: string;
  uomType: 'PURCHASING' | 'STANDARD' | 'SALES' | string;
  unitName: string;
  conversionRatio: number;
}

export interface AccountMapping {
  id: string;
  system: string;
  accountType: string;
  externalAccountId: string;
}

export interface UpdateAccountMapping {
  system: string;
  accountType: string;
  externalAccountId: string;
}

export interface SyncLog {
  id: string;
  system: string;
  entityType: string;
  entityId: string;
  status: 'PENDING' | 'SYNCED' | 'FAILED' | 'SKIPPED' | string;
  retryCount: number;
  lastError?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface OutboxEventItem {
  id: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  payload: Record<string, unknown>;
  status: string;
  retryCount: number;
  lastError?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PlatformAlertItem {
  id: string;
  alertType: string;
  severity: string;
  sourceSystem?: string;
  title: string;
  details: Record<string, unknown>;
  acknowledgedAt?: string;
  createdAt?: string;
}

export interface AuditLogItem {
  id: string;
  actorUserId?: string;
  action: string;
  entityType: string;
  entityId: string;
  diff: Record<string, unknown>;
  createdAt?: string;
}

export interface ChannelIntegration {
  id: string;
  platform: string;
  shopIdentifier: string;
  status: string;
  credentialStatus?: string;
  lastWebhookAt?: string;
  lastPushAt?: string;
  errorCount?: number;
}

export interface BomLine {
  id: string;
  bomId: string;
  componentVariantId: string;
  componentSku?: string;
  componentName?: string;
  quantityRequired: number;
  children?: BomLine[];
}

export interface Bom {
  id: string;
  parentVariantId: string;
  parentSku?: string;
  parentName?: string;
  name: string;
  isActive: boolean;
  autoAssemble?: boolean;
  lines?: BomLine[];
  createdAt?: string;
  updatedAt?: string;
}

export interface ManufacturingOperation {
  id: string;
  name: string;
  defaultHourlyRate: number;
}

export interface ProductionTimesheet {
  id: string;
  productionOrderId: string;
  operationId: string;
  operationName?: string;
  userId: string;
  startTime: string;
  endTime?: string;
  totalCost: number;
}

export interface ProductionOrder {
  id: string;
  number: string;
  parentVariantId: string;
  parentSku?: string;
  parentName?: string;
  qtyTarget: number;
  qtyProduced: number;
  status: 'DRAFT' | 'COMPONENTS_ALLOCATED' | 'WIP' | 'COMPLETED' | 'CANCELLED' | string;
  createdAt?: string;
  updatedAt?: string;
  primaryMediaUrl?: string | null;
}

export interface ReturnLine {
  id: string;
  returnId: string;
  salesOrderLineId: string;
  sku?: string;
  productName?: string;
  quantityExpected: number;
  quantityReceived: number;
  disposition?: 'RESTOCK' | 'SCRAP' | 'REPAIR' | string;
  putawayTarget?: string;
}

export interface Return {
  id: string;
  salesOrderId: string;
  salesOrderNumber?: string;
  customerName?: string;
  number: string;
  status: 'REQUESTED' | 'APPROVED' | 'RECEIVED' | 'CLOSED' | 'REJECTED' | string;
  returnLabelUrl?: string;
  lines?: ReturnLine[];
  createdAt?: string;
  updatedAt?: string;
}

export interface PortalCatalogItem {
  id: string;
  sku: string;
  name: string;
  unitPrice: number;
  currency: string;
  atp?: number;
  category?: string;
  primaryMediaUrl?: string | null;
}

export interface PortalOrder {
  id: string;
  number: string;
  status: string;
  total: number;
  currency: string;
  createdAt: string;
}

export interface CustomerPriceTier {
  id: string;
  name: string;
  discountPercent: number;
}

export interface DashboardStats {
  stockValue: number;
  currency: string;
  lowStockCount: number;
  openOrdersCount: number;
  unpaidInvoicesCount: number;
}

export interface DashboardKpiTrends {
  stockValueTrend: 'UP' | 'DOWN' | 'FLAT';
  lowStockTrend: 'UP' | 'DOWN' | 'FLAT';
  openOrdersTrend: 'UP' | 'DOWN' | 'FLAT';
  unpaidInvoicesTrend: 'UP' | 'DOWN' | 'FLAT';
}

export interface DashboardWorkQueue {
  needsAllocation: number;
  readyToInvoice: number;
  unpaidInvoices: number;
  lowStockItems: number;
}

export interface LowStockVelocityPoint {
  date: string;
  availableUnits: number;
}

export interface DashboardRecentOrder {
  id: string;
  number: string;
  customerName: string;
  status: string;
  createdAt: string;
}

export interface DashboardLowStockItem {
  variantId: string;
  sku: string;
  productName: string;
  available: number;
  reorderPoint: number;
}

export interface ForecastAlert {
  variantId: string;
  sku: string;
  productName: string;
  available: number;
  reorderPoint: number;
  recommendedPoQty: number;
  velocity30d: number;
  defaultSupplierId?: string;
  defaultSupplierName?: string;
  supplierLeadTimeDays?: number;
  calculatedAt?: string;
}

export interface TaxRate {
  id: string;
  name: string;
  rate: number;
  isDefault: boolean;
}

export interface StripeBillingStatus {
  connectedAccountId?: string | null;
  onboardingStatus: string;
  capabilities: Record<string, unknown>;
}

export interface ShippingCredentialStatus {
  system: string;
  status: string;
}

export interface TenantEmailDomain {
  id: string;
  domainName: string;
  verificationStatus: string;
  dkimTokens: Array<Record<string, string>>;
}

export interface PriorityAudit {
  id: string;
  locationId: string;
  locationPath: string;
  notes?: string;
  createdAt: string;
}

export interface SupplierPortalPo {
  id: string;
  number: string;
  supplierName: string;
  status: string;
  expectedAt?: string;
  lines: Array<{
    id: string;
    variantId: string;
    sku: string;
    qtyOrdered: number;
    barcode: string;
  }>;
}

export interface SupplierPortalLabel {
  barcode: string;
  sku: string;
  quantity: number;
  poNumber: string;
}

export interface PaginatedResponse<T> {
  items: T[];
  nextCursor?: string;
  total?: number;
}

export interface PurchaseOrder {
  id: string;
  number: string;
  supplierName: string;
  status: string;
  expectedAt?: string;
  destinationLocationId?: string;
  freightAmount?: number;
  dutiesAmount?: number;
}

export interface SalesOrder {
  id: string;
  number: string;
  customerName: string;
  status: string;
  createdAt: string;
  channel?: string;
  sourceLocationId?: string;
  customerPoNumber?: string;
  requestedShipDate?: string;
  /** NONE | PARTIAL | INVOICED — from server billing coverage */
  billingStatus?: 'NONE' | 'PARTIAL' | 'INVOICED' | string;
}

export interface PackLabelResponse {
  id: string;
  trackingNumber?: string;
  labelRef?: string;
  totalWeight?: number;
  postageAmount?: number;
  carrier?: string;
  status: string;
}

export interface SalesOrderLineDetail {
  id: string;
  variantId: string;
  sku?: string;
  name?: string;
  qtyOrdered: number;
  qtyShipped: number;
  unitPrice: number;
}

export interface SalesOrderDetail {
  id: string;
  number: string;
  customerName: string;
  status: string;
  lines: SalesOrderLineDetail[];
}

export interface PickingTask {
  id: string;
  allocationId: string;
  locationPath: string;
  zone?: string;
  sequenceOrder: number;
  status: string;
}

export interface DemandChartPoint {
  variantId: string;
  sku: string;
  historicalVelocity: number;
  forecastQty: number;
  seasonalityIndex: number;
  confidenceScore: number;
  calculatedAt: string;
}

export interface SupplierInvoiceIngestion {
  id: string;
  purchaseOrderId: string;
  status: 'PENDING' | 'RECONCILED' | 'CONFLICT';
  documentUrl?: string | null;
  matchConfidence: number;
  extractedData: Record<string, unknown>;
  createdAt: string;
}

export interface FintechDashboard {
  creditLine: {
    creditLimit: number;
    outstandingBalance: number;
    interestRateApr: number;
    utilizationStatus: string;
  };
  utilizationPercent: number;
  underwriting?: {
    gmv30d: number;
    gmv90d: number;
    dsoDays: number;
    avgInvoiceAgeDays: number;
    paymentVelocityScore: number;
    eligibleFactoringLimit: number;
  };
  eligibleInvoices: Array<{
    invoiceId: string;
    number: string;
    total: number;
    advanceAmount: number;
  }>;
}

export interface EdiDocumentLog {
  id: string;
  tradingPartnerId: string;
  direction: string;
  documentType: string;
  status: string;
  createdAt: string;
}

export interface PortalCreditSummary {
  creditLimit: number;
  availableCredit: number;
  status: string;
}

export interface PortalInvoice {
  id: string;
  number: string;
  status: string;
  total: number;
  currency: string;
  dueAt?: string;
}

export interface PortalReorderLine {
  variantId: string;
  sku: string;
  name: string;
  quantity: number;
}

export interface Invoice {
  id: string;
  number: string;
  customerName: string;
  status: string;
  total: number;
  currency: string;
  dueAt?: string;
  salesOrderId?: string;
}

export interface InvoiceDetail extends Invoice {}

export interface Customer {
  id: string;
  name: string;
  email?: string;
}

export interface Supplier {
  id: string;
  name: string;
  contactEmail?: string;
}

export interface ApiError {
  title: string;
  detail?: string;
  status: number;
  ssoAuthorizationUrl?: string;
}

export interface SerialScanResult {
  serialId: string;
  serialNumber: string;
  variantId: string;
  sku: string;
  productName: string;
  status: string;
  locationId?: string;
  locationPath?: string;
  quantity: number;
}

export interface FulfillmentScanResponse {
  variantId?: string;
  sku: string;
  name: string;
  requiresSerial: boolean;
  serialPrompt?: string | null;
  message: string;
  putawayTarget?: string | null;
  primaryMediaUrl?: string | null;
}

export interface SsoConfig {
  issuerUrl: string;
  clientId: string;
  enabled: boolean;
  forceSso: boolean;
  configured?: boolean;
  hasSecret?: boolean;
  protocol?: 'OIDC' | 'SAML' | string;
  samlMetadataUrl?: string | null;
  samlEntityId?: string | null;
}

export interface InventoryValuationRow {
  warehouseId: string;
  warehouseCode: string;
  warehouseName: string;
  variantId: string;
  sku: string;
  productName: string;
  onHand: number;
  avgCost: number;
  totalValue: number;
}

export interface InventoryValuationReport {
  grandTotal: number;
  currency: string;
  rows: InventoryValuationRow[];
}

export interface StockTurnoverRow {
  variantId: string;
  sku: string;
  productName: string;
  unitsShipped: number;
  averageOnHand: number;
  turnoverRate: number;
}

export interface StockTurnoverReport {
  periodDays: string;
  rows: StockTurnoverRow[];
}

export interface CogsLedgerRow {
  channel: string;
  customerId?: string;
  customerName?: string;
  movementType: string;
  quantity: number;
  unitCost: number;
  cogsAmount: number;
}

export interface CogsLedgerReport {
  totalCogs: number;
  currency: string;
  rows: CogsLedgerRow[];
}

export interface ReportChartPoint {
  label: string;
  value: number;
}

export interface ProfitByProductRow {
  variantId: string;
  sku: string;
  productName: string;
  revenue: number;
  cogs: number;
  grossProfit: number;
  marginPercent: number;
}

export interface ProfitByCustomerRow {
  customerId: string;
  customerName: string;
  revenue: number;
  cogs: number;
  grossProfit: number;
  marginPercent: number;
}

export interface ProfitMarginReport {
  totalRevenue: number;
  totalCogs: number;
  grossProfit: number;
  grossMarginPercent: number;
  currency: string;
  revenueByMonth: ReportChartPoint[];
  profitByMonth: ReportChartPoint[];
  byProduct: ProfitByProductRow[];
  byCustomer: ProfitByCustomerRow[];
}

export interface SalesPerformanceReport {
  totalRevenue: number;
  totalOrders: number;
  currency: string;
  revenueByMonth: ReportChartPoint[];
  ordersByStatus: ReportChartPoint[];
  revenueByChannel: ReportChartPoint[];
  revenueByCustomer: ReportChartPoint[];
}

export interface FulfillmentSummaryReport {
  unitsShipped30d: number;
  openOrderLines: number;
  fillRatePercent: number;
  ordersByStatus: ReportChartPoint[];
  shippedByWeek: ReportChartPoint[];
}

export interface PurchaseSpendRow {
  purchaseOrderId: string;
  number: string;
  supplierName: string;
  status: string;
  totalSpend: number;
}

export interface PurchaseSpendReport {
  totalSpend: number;
  currency: string;
  spendBySupplier: ReportChartPoint[];
  spendByMonth: ReportChartPoint[];
  rows: PurchaseSpendRow[];
}

export interface ReturnsAnalysisRow {
  returnId: string;
  number: string;
  customerName: string;
  salesOrderNumber: string;
  status: string;
  lineCount: number;
}

export interface ReturnsAnalysisReport {
  totalReturns: number;
  returnRatePercent: number;
  returnsByStatus: ReportChartPoint[];
  dispositionBreakdown: ReportChartPoint[];
  rows: ReturnsAnalysisRow[];
}

export interface CostCenter {
  id: string;
  code: string;
  name: string;
  budget?: number | null;
  createdAt?: string;
}

export interface InternalRequisitionLine {
  id: string;
  variantId: string;
  sku?: string | null;
  qtyRequested: number;
  qtyIssued: number;
}

export interface InternalRequisition {
  id: string;
  requisitionNumber: string;
  costCenterId: string;
  costCenterCode?: string | null;
  requestedByUserId?: string | null;
  status: 'DRAFT' | 'APPROVED' | 'ISSUED' | 'CANCELLED' | string;
  createdAt?: string;
  lines?: InternalRequisitionLine[];
}

export interface GenealogyNode {
  id: string;
  type: string;
  label: string;
  detail?: string | null;
  children?: GenealogyNode[];
}

export interface LotTraceResponse {
  lotId: string;
  lotNumber: string;
  upstream: GenealogyNode;
  downstream: GenealogyNode;
}

export interface VehicleAssignment {
  id: string;
  locationId: string;
  locationCode?: string | null;
  locationName?: string | null;
  technicianUserId: string;
  assignedAt: string;
  returnedAt?: string | null;
}

export interface VanStockLevel {
  variantId: string;
  sku?: string | null;
  lotId?: string | null;
  onHand: number;
  allocated: number;
  available: number;
}
