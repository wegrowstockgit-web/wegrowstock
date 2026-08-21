export interface User {
  id: string;
  email: string;
  displayName: string;
  roles: string[];
  /** Union of granted permission keys across all assigned roles. */
  grantedPermissions?: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
  tenantId?: string;
  department?: string | null;
  corporateDepartment?: string | null;
  timezonePreference?: string | null;
  localeLanguage?: string | null;
  assignedWarehouseId?: string | null;
  mfaEnabled?: boolean;
  shiftSchedule?: string | null;
  shiftScheduleType?: string | null;
  phone?: string | null;
  addressLine1?: string | null;
  uiDensityPreference?: string | null;
  /** Platform control-plane access (users.is_super_admin). */
  isSuperAdmin?: boolean;
  /** Commercial modules enabled for this tenant. */
  enabledModules?: string[];
  /** Commercial subscription tier: BASIC, INTERMEDIATE, ENTERPRISE. */
  tier?: string | null;
}

export interface LoginRequest {
  email: string;
  password: string;
  targetApp?: 'WMS' | 'POS' | 'ADMIN';
  mfaCredentialId?: string;
  mfaChallenge?: string;
  mfaSignature?: string;
}

/** Session metadata — JWTs are HttpOnly cookies only. */
export interface SessionResponse {
  tenantId: string;
  userId: string;
  roles: string[];
  warehouseIds?: string[];
  avatarUrl?: string | null;
  grantedPermissions?: string[];
}

/** @deprecated use SessionResponse */
export type TokenResponse = SessionResponse;

/** @deprecated use SessionResponse */
export type LoginResponse = SessionResponse;

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

export interface LogisticsAddress {
  street?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  country?: string;
}

export interface TenantLocation {
  id: string;
  parentLocationId?: string;
  type: string;
  code: string;
  name: string;
  path: string;
  /** STANDARD | PICK_FACE | RESERVE | RECEIVING */
  zoneBehavior?: string;
  /** Digital Twin floor coordinates */
  coordX?: number | null;
  coordY?: number | null;
  coordZ?: number | null;
  logisticsAddress?: LogisticsAddress;
  grossSquareFootage?: number | null;
  officeAreaSquareFootage?: number | null;
  clearHeightFeet?: number | null;
  totalDockDoors?: number | null;
  weightCapacityLimit?: number | null;
  /** Industry-standard alias for structural floor load (synced with weightCapacityLimit). */
  floorLoadCapacityLbs?: number | null;
}

export interface TenantUser {
  id: string;
  email: string;
  displayName: string;
  status: string;
  roles: string[];
  department?: string | null;
  corporateDepartment?: string | null;
  timezonePreference?: string | null;
  localeLanguage?: string | null;
  assignedWarehouseId?: string | null;
  mfaEnabled?: boolean;
  shiftSchedule?: string | null;
  shiftScheduleType?: string | null;
  warehouseIds?: string[];
}

export interface CreateUserPayload {
  email: string;
  roleIds: string[];
  role?: string;
  roles?: string[];
  customerId?: string;
  supplierId?: string;
}

export interface UpdateUserPayload {
  roleIds: string[];
  role?: string;
  roles?: string[];
  corporateDepartment?: string | null;
  timezonePreference?: string | null;
  localeLanguage?: string | null;
  shiftScheduleType?: string | null;
  assignedWarehouseId?: string | null;
  clearAssignedWarehouse?: boolean;
  warehouseIds?: string[];
}

export type TenantSettingsMap = Record<string, unknown>;

export interface Product {
  id: string;
  skuRoot: string;
  name: string;
  description?: string | null;
}

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
  isLotTracked?: boolean;
  dims?: Record<string, unknown>;
  reorderPoint?: number;
  reorderQty?: number;
  primaryMediaUrl?: string | null;
  hsTariffCode?: string | null;
  countryOfOrigin?: string | null;
  isHazmat?: boolean;
  palletTie?: number | null;
  palletHigh?: number | null;
  storageTempZone?: string;
  isFragile?: boolean;
  abcClassification?: string;
  lifecycleStatus?: string;
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

export interface LedgerAccount {
  accountId: string;
  name: string;
  type: string;
  classification: string;
  code: string;
}

export interface IntegrationConnectionStatus {
  connected: boolean;
  accountName: string;
  lastSyncAt: string;
  tokenExpiringSoon: boolean;
}

export interface IntegrationAuthUrl {
  authorizationUrl: string;
  state: string;
  provider: string;
}

export interface IntegrationConnectionTest {
  ok: boolean;
  readOk: boolean;
  writeOk: boolean;
  message: string;
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
  actorEmail?: string | null;
  actorDisplayName?: string | null;
  action: string;
  entityType: string;
  entityId: string;
  diff: Record<string, unknown>;
  createdAt?: string;
}

export interface AuditTenantPage {
  items: AuditLogItem[];
  nextCursor?: string | null;
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
  reasonCode?: string;
  mediaObjectId?: string;
  evidenceUrl?: string;
}

export interface Return {
  id: string;
  salesOrderId: string;
  salesOrderNumber?: string;
  customerName?: string;
  number: string;
  status:
    | 'REQUESTED'
    | 'PENDING_REVIEW'
    | 'APPROVED'
    | 'EXPECTED'
    | 'RECEIVED'
    | 'CLOSED'
    | 'REJECTED'
    | string;
  reasonCode?: string;
  returnLabelUrl?: string;
  estimatedLabelCost?: number;
  labelPurchaseMode?: string;
  evidenceUrls?: string[];
  lines?: ReturnLine[];
  createdAt?: string;
  updatedAt?: string;
}

export interface PortalRmaEligibleLine {
  salesOrderLineId: string;
  variantId: string;
  sku: string;
  name: string;
  qtyReturnable: number;
  unitPrice: number;
  requiresReview: boolean;
}

export interface PortalRmaResponse {
  id: string;
  number: string;
  status: string;
  reviewReason?: string | null;
  returnLabelUrl?: string | null;
  estimatedLabelCost?: number | null;
  merchandiseValue?: number;
  labelPurchaseMode?: string | null;
  shippingInstruction?: string | null;
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

export type AllocationPolicy = 'SHIP_COMPLETE' | 'ALLOW_PARTIAL';

export interface PortalOrder {
  id: string;
  number: string;
  status: string;
  total: number;
  currency: string;
  createdAt: string;
  allocationPolicy?: AllocationPolicy | string;
  quoteExpiresAt?: string | null;
  manualDiscountTotal?: number;
  quoteNotes?: string | null;
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

export interface LaborVelocityHourlyPoint {
  hour: string;
  picks: number;
}

export interface LaborVelocityOperator {
  userId: string;
  operatorName: string;
  totalPicks: number;
  totalReceives: number;
  activePph: number;
  shiftPph: number;
  utilizationPercent: number;
  activeWaveHours: number;
  shiftHours: number;
  activePphDeltaVsAvg: number;
  hourlyPicks: LaborVelocityHourlyPoint[];
}

export interface LaborVelocityResponse {
  from: string;
  to: string;
  warehouseAvgActivePph: number;
  operators: LaborVelocityOperator[];
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

export interface TaxSchemeRate {
  id: string;
  name: string;
  rate: number;
  sortOrder: number;
}

export interface TaxScheme {
  id: string;
  name: string;
  taxInclusive: boolean;
  active: boolean;
  rates: TaxSchemeRate[];
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
  isVerified?: boolean;
  dnsVerificationToken?: string | null;
  dkimTokens: Array<Record<string, string>>;
}

export interface PriorityAudit {
  id: string;
  locationId: string;
  locationPath: string;
  notes?: string;
  createdAt: string;
}

export interface BlindCountSettings {
  blindCycleCounts: boolean;
  maxAutoAdjustValue: number | string;
}

export interface CycleCountLineView {
  id: string;
  cycleCountId: string;
  variantId: string;
  sku: string;
  locationPath?: string | null;
  lotId?: string | null;
  expectedQty: number | string;
  countedQty?: number | string | null;
  varianceStatus: string;
  financialImpact?: number | string | null;
}

export interface CycleCountDetail {
  id: string;
  locationId: string;
  locationPath: string;
  status: string;
  notes?: string | null;
  blindCycleCounts: boolean;
  maxAutoAdjustValue: number | string;
  lines: CycleCountLineView[];
}

export interface PendingVariance {
  lineId: string;
  cycleCountId: string;
  locationId: string;
  locationPath: string;
  variantId: string;
  sku: string;
  expectedQty: number | string;
  countedQty: number | string;
  financialDelta: number | string;
  varianceStatus: string;
  updatedAt: string;
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
  nextCursor?: string | null;
  hasMore?: boolean;
  totalElements?: number;
  totalPages?: number;
  page?: number;
  size?: number;
  /** Legacy alias used by older cursor clients. */
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
  notes?: string | null;
}

export interface PurchaseOrderLineDetail {
  id: string;
  variantId: string;
  qtyOrdered: number;
  qtyReceived: number;
  unitCost: number;
}

export interface PurchaseOrderDetail extends PurchaseOrder {
  lines: PurchaseOrderLineDetail[];
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
  allocationPolicy?: AllocationPolicy | string;
  quoteExpiresAt?: string | null;
  manualDiscountTotal?: number;
}

export interface PackLabelResponse {
  id: string;
  trackingNumber?: string;
  labelRef?: string;
  labelFileType?: string;
  totalWeight?: number;
  postageAmount?: number;
  carrier?: string;
  serviceLevel?: string;
  cartonId?: string;
  cartonName?: string;
  length?: number;
  width?: number;
  height?: number;
  volumetricWeight?: number;
  status: string;
}

export interface WorkstationSettings {
  id?: string | null;
  printMode: 'PDF' | 'ZPL';
  zplPrinterName?: string | null;
  labelFormat: string;
}

export interface PackPlacement {
  variantId: string;
  xIn: number;
  yIn: number;
  zIn: number;
  lengthIn: number;
  widthIn: number;
  heightIn: number;
}

export interface CartonizePreviewResponse {
  cartonId: string;
  cartonName: string;
  lengthIn: number;
  widthIn: number;
  heightIn: number;
  actualWeightLb: number;
  volumetricWeightLb: number;
  billableWeightLb: number;
  totalVolumeCuIn: number;
  /** FFD 3D packing configuration (inches from carton origin). */
  packing?: PackPlacement[];
}

export interface SalesOrderLineDetail {
  id: string;
  variantId: string;
  sku?: string;
  name?: string;
  qtyOrdered: number;
  qtyAllocated?: number;
  qtyShipped: number;
  qtyBackordered?: number;
  unitPrice: number;
}

export interface SalesOrderDetail {
  id: string;
  number: string;
  customerName: string;
  status: string;
  allocationPolicy?: AllocationPolicy | string;
  quoteExpiresAt?: string | null;
  manualDiscountTotal?: number;
  quoteNotes?: string | null;
  lines: SalesOrderLineDetail[];
}

export interface PickingTask {
  id: string;
  allocationId: string;
  variantId?: string;
  isLotTracked?: boolean;
  locationPath: string;
  zone?: string;
  sequenceOrder: number;
  status: string;
  /** MIB tote label assigned per sales order within a wave (e.g. "Tote A"). */
  toteIdentifier?: string | null;
  locationId?: string | null;
  coordX?: number | null;
  coordY?: number | null;
  /** Expected catalog SKU for client-side pre-validation. */
  sku?: string | null;
  /** Expected barcode / GTIN for client-side pre-validation. */
  barcode?: string | null;
  /** Expected allocation quantity for GS1 AI (30) checks. */
  quantity?: number | null;
}

export interface WayfindingPoint {
  x: number;
  y: number;
  locationId?: string;
  code?: string;
}

export interface WayfindingPath {
  fromLocationId: string;
  toLocationId: string;
  travelCost: number;
  points: WayfindingPoint[];
}

export interface NextBestAction {
  taskType: string | null;
  taskId: string | null;
  locationId: string | null;
  locationPath: string | null;
  instruction: string | null;
  toteIdentifier?: string | null;
  summary: string;
  /** Spatial / hierarchical travel score from TaskOrchestratorService. */
  travelScore?: number | null;
}

export interface MoveLpnResult {
  lpnId: string;
  lpnBarcode: string;
  destinationLocationId: string;
  linesMoved: number;
}

export interface MintedLpnResponse {
  id: string;
  lpnBarcode: string;
  locationId?: string | null;
  status: string;
  zpl: string;
}

export interface PackLpnResult {
  lpnId: string;
  lpnBarcode: string;
  linesPacked: number;
  itemCount: number;
}

export interface FulfillmentException {
  id: string;
  allocationId: string;
  reportedBy: string;
  warehouseId: string;
  resolutionStatus: string;
  metadata?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
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

export interface InvoiceDetail extends Invoice {
  documentUrl?: string | null;
}

export interface Customer {
  id: string;
  name: string;
  email?: string;
  taxId?: string | null;
  paymentTerms?: 'NET30' | 'NET60' | 'DUE_ON_RECEIPT' | string | null;
  creditLimit?: number | null;
  currencyPreference?: string | null;
  defaultCurrency?: string | null;
  customerStatus?: 'ACTIVE' | 'HOLD' | 'PROSPECT' | string;
  billingAddress?: LogisticsAddress;
  shippingAddress?: LogisticsAddress;
}

export interface CustomerBillingSla {
  id?: string;
  customerId?: string;
  storageMode: 'PALLET_POSITION' | 'CUBIC_VOLUME';
  ratePerUnit: number;
  pickFeePerItem: number;
}

export interface BillingAccrualRow {
  id: string;
  accrualDate: string;
  amount: number;
  description: string;
  status: string;
}

export interface CustomerBillingView {
  sla: CustomerBillingSla | null;
  unbilledAccruals: BillingAccrualRow[];
  unbilledTotal: number;
}

export interface ShowroomBillingAccruals {
  sla: Omit<CustomerBillingSla, 'id' | 'customerId'> | null;
  monthStart: string;
  monthToDateTotal: number;
  accruals: BillingAccrualRow[];
}

export interface Supplier {
  id: string;
  name: string;
  contactEmail?: string;
  paymentTerms?: 'NET30' | 'NET60' | 'DUE_ON_RECEIPT' | string | null;
  taxId?: string | null;
  businessRegistration?: string | null;
  bankAccountIban?: string | null;
  bankRoutingNumber?: string | null;
  defaultLeadTimeDays?: number | null;
  minimumOrderQuantityValue?: number | null;
  supplierRating?: number | null;
  defaultCurrency?: string | null;
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
  isLotTracked?: boolean;
  lotLoggedNotTracked?: boolean;
  crossDock?: boolean;
  stagingPath?: string | null;
  stagingLocationId?: string | null;
  crossDockSalesOrderNumber?: string | null;
  crossDockInstruction?: string | null;
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
  provider?: string;
  ssoProvider?: string;
  acsUrl?: string | null;
  samlCertificate?: string | null;
  corporateCidrIps?: string[];
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

export interface ComplianceLotTraceResponse {
  lotId: string;
  lotNumber: string;
  variantId: string;
  sku: string;
  origin: {
    ledgerId: string;
    receivedAt?: string | null;
    quantity?: number | null;
    locationId?: string | null;
    locationCode?: string | null;
    locationPath?: string | null;
    purchaseOrderId?: string | null;
    purchaseOrderNumber?: string | null;
    purchaseOrderLineId?: string | null;
    supplierId?: string | null;
    supplierName?: string | null;
  } | null;
  currentExposure: Array<{
    inventoryLevelId: string;
    locationId: string;
    locationCode: string;
    locationPath: string;
    locationType?: string | null;
    zoneBehavior?: string | null;
    onHand: number;
    allocated: number;
    available: number;
  }>;
  downstream: Array<{
    ledgerId: string;
    shippedAt?: string | null;
    quantity?: number | null;
    salesOrderId?: string | null;
    salesOrderNumber?: string | null;
    salesOrderLineId?: string | null;
    customerId?: string | null;
    customerName?: string | null;
    shipmentId?: string | null;
    trackingNumber?: string | null;
  }>;
}

export interface ReplenishmentTask {
  ruleId: string;
  variantId: string;
  sku: string;
  variantName: string;
  lotId?: string | null;
  lotNumber?: string | null;
  fromLocationId: string;
  fromLocationCode: string;
  fromLocationPath: string;
  toLocationId: string;
  toLocationCode: string;
  toLocationPath: string;
  pickFaceOnHand: number;
  minQuantity: number;
  maxQuantity: number;
  suggestedQuantity: number;
  instruction: string;
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

export interface ThermalPrinter {
  id: string;
  name: string;
  printerType: 'PRINTNODE' | 'DIRECT_SOCKET';
  isDefault: boolean;
  locationId?: string | null;
}

export interface ClusterPickStep {
  sequenceOrder: number;
  sku: string;
  qty: number;
  slotIndex: number;
  toteBarcode: string;
  locationPath: string;
  instruction: string;
}

export interface MrpSuggestionLine {
  variantId: string;
  sku: string;
  openSalesQty: number;
  safetyStock: number;
  onHand: number;
  allocated: number;
  inboundOpenPoQty: number;
  netRequirement: number;
  suggestedOrderQty: number;
  defaultSupplierId: string | null;
  defaultSupplierName: string | null;
  leadTimeDays: number;
  unitCost: number;
  capitalEstimate: number;
}

export interface MrpCalculateResult {
  createdPurchaseOrders: Array<{ id: string; number: string; supplierId: string }>;
  suggestions: MrpSuggestionLine[];
}

export interface PalletManifest {
  id: string;
  sscc18?: string | null;
  status: 'BUILDING' | 'SEALED' | 'DISPATCHED';
  bolNumber?: string | null;
  carrierName?: string | null;
  warehouseId?: string | null;
  items?: Array<{ id: string; lpnBarcode?: string | null; lpnId?: string | null }>;
}

export interface PalletManifestSealResult {
  id: string;
  sscc18: string;
  bolNumber: string;
  status: 'SEALED' | 'DISPATCHED';
}

export type RmaQcGrade = 'GRADE_A_NEW' | 'GRADE_B_OPEN_BOX' | 'GRADE_C_DAMAGED';
export type RmaQcDisposition = 'RESTOCK' | 'SCRAP' | 'REPAIR' | 'REFURBISH';

export interface RmaQcInspection {
  id: string;
  returnLineId: string;
  grade: RmaQcGrade;
  dispositionAction: RmaQcDisposition;
  inspectionNotes?: string | null;
  photoAttachmentIds: string[];
}

export interface RoleDefinition {
  id: string;
  name: string;
  networkAccessLevel?: 'STRICT_INTERNAL' | 'MFA_OUTSIDE_NETWORK' | 'ROAMING';
  isSystemRole?: boolean;
  description?: string | null;
}

export interface RolePermissionGrant {
  roleId: string;
  permissionKey: string;
  granted: boolean;
}

export interface RolePermissionsMatrixResponse {
  roles: RoleDefinition[];
  permissionKeys: string[];
  grants: RolePermissionGrant[];
  allowedCidrBlocks?: string[];
}
