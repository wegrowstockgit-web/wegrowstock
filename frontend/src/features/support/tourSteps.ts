import type { DriveStep } from 'driver.js';
import type { TourId } from '@/stores/preferencesStore';

/** A workflow step that may require a React Router navigation before highlighting. */
export type WorkflowTourStep = {
  id: string;
  /** Route that must be mounted for this highlight (pathname prefix match). */
  route: string;
  element: string;
  title: string;
  description: string;
  /** Override Done/Next label for this step (single-step driver mounts). */
  doneBtnText?: string;
  /**
   * When advancing from this step, destroy the driver and move to another page
   * at `nextStep` (global index). `href` may include query params.
   */
  transition?: {
    route: string;
    nextStep: number;
    href?: string;
  };
};

/** Desktop office tour — allocation, waves, dense grids. */
export function officeTourSteps(): DriveStep[] {
  return workflowToDriveSteps(WORKFLOW_TOURS.office);
}

/** Floor / scanner tour — hardware scan + receive confirm. */
export function floorTourSteps(): DriveStep[] {
  return workflowToDriveSteps(WORKFLOW_TOURS.floor);
}

export function getWorkflowTour(tourId: TourId): WorkflowTourStep[] {
  return WORKFLOW_TOURS[tourId];
}

/** Surface B / handheld scanner routes — shift PIN + idle lock apply only here. */
export function isFloorRoute(pathname: string): boolean {
  return (
    pathname.startsWith('/fulfillment') ||
    pathname.startsWith('/inbound') ||
    pathname.startsWith('/cycle-counts') ||
    pathname.startsWith('/manufacturing/terminal') ||
    pathname.startsWith('/returns/receive') ||
    pathname.startsWith('/issue-supplies') ||
    pathname.startsWith('/replenishments') ||
    pathname.startsWith('/field')
  );
}

export function routeMatches(pathname: string, stepRoute: string): boolean {
  if (pathname === stepRoute) return true;
  return (
    pathname.startsWith(stepRoute.endsWith('/') ? stepRoute : `${stepRoute}/`) ||
    pathname.startsWith(stepRoute)
  );
}

function workflowToDriveSteps(steps: WorkflowTourStep[]): DriveStep[] {
  return steps.map((s) => ({
    element: s.element,
    popover: { title: s.title, description: s.description },
  }));
}

/**
 * Empathy-first copy: every description links a physical/UI action to a downstream digital effect.
 */
export const WORKFLOW_TOURS: Record<TourId, WorkflowTourStep[]> = {
  office: [
    {
      id: 'office-so',
      route: '/sales-orders',
      element: '[data-tour="nav-sales-orders"]',
      title: 'Sales orders & allocation',
      description:
        'Confirm and Allocate here. Physically reserving stock on this screen locks lots so the B2B portal and floor picks cannot double-sell the same unit.',
    },
    {
      id: 'office-products',
      route: '/products',
      element: '[data-tour="nav-products"]',
      title: 'Product grid',
      description:
        'Scan the catalog in this virtualized grid. Changing visibility or density here only reshapes your view — on-hand in the ledger stays untouched until receive/adjust posts.',
    },
    {
      id: 'office-density',
      route: '/products',
      element: '[data-testid="density-toggle"]',
      title: 'Density control',
      description:
        'Compact / Cozy / Spacious changes row height (32/44/64px) so you can scan more lines or verify details — the same SKU rows still map 1:1 to inventory ledger identity.',
    },
    {
      id: 'office-columns',
      route: '/products',
      element: '[data-testid="column-visibility-toggle"]',
      title: 'Columns & pins',
      description:
        'Hide unused fields or pin identifiers. Freeze columns stay aligned while you scroll — preventing mis-clicks that would allocate or adjust the wrong SKU.',
    },
    {
      id: 'office-copilot',
      route: '/sales-orders',
      element: '[data-testid="support-assistant-fab"]',
      title: 'Ask the copilot',
      description:
        'Role-aware help uses your current screen and clearance. Prefer asking here over guessing warehouse steps that could corrupt allocations.',
    },
  ],
  floor: [
    {
      id: 'floor-shell',
      route: '/fulfillment',
      element: '[data-testid="warehouse-floor-shell"]',
      title: 'Warehouse floor shell',
      description:
        'Large tap targets for gloved use. Office sidebars stay hidden so every scan lands on the task field — wrong focus would skip ledger writes.',
    },
    {
      id: 'floor-scan',
      route: '/fulfillment',
      element: '[data-tour="fulfillment-scan"]',
      title: 'Scan wedge field',
      description:
        'Point the hardware scanner here. The barcode wedge + Enter confirms the pick digitally; blocked SKUs protect the sales order from shipping the wrong item.',
    },
    {
      id: 'floor-inbound',
      route: '/inbound/receive',
      element: '[data-tour="inbound-receive"]',
      title: 'Inbound receive',
      description:
        'Scan PO paperwork, product barcodes, then the bin. Confirming putaway writes the ledger receive — that is what unlocks sellable qty for allocation and the B2B portal.',
    },
    {
      id: 'floor-copilot',
      route: '/fulfillment',
      element: '[data-testid="support-assistant-fab"]',
      title: 'Floor copilot',
      description:
        'Ask scanner-first questions (inbound, putaway, PIN unlock). Desktop PO creation is never suggested to pickers because it would create paper without a physical receipt.',
    },
  ],
  /**
   * 6-step procurement → receive → allocate journey (indices 0–5).
   * Cross-page hops use `transition` + TourOrchestrator navigate.
   */
  'receiving-to-allocation': [
    {
      id: 'r2a-po-grid',
      route: '/purchase-orders',
      element: '[data-tour="tour-po-grid"]',
      title: 'Inbound purchase orders',
      description:
        'This PO grid is the digital dock ticket. Each row is what the handheld will match when freight arrives — without it, inbound scans cannot post inventory.',
      doneBtnText: 'Next',
    },
    {
      id: 'r2a-po-receive',
      route: '/purchase-orders',
      element: '[data-tour="tour-po-receive-cta"]',
      title: 'Hand off to the floor',
      description:
        'When the truck arrives, open receive from the PO. Next we move to the warehouse scanner so putaway can unlock sellable stock.',
      doneBtnText: 'Next',
      transition: {
        route: '/inbound/receive',
        nextStep: 2,
        href: '/inbound/receive?po=PO-2026-00001',
      },
    },
    {
      id: 'r2a-inbound-shell',
      route: '/inbound/receive',
      element: '[data-tour="inbound-receive"]',
      title: 'Warehouse receive shell',
      description:
        'Glove-friendly putaway view. Confirming scans here writes the ledger receive — that is what the B2B portal and allocation see as available inventory.',
      doneBtnText: 'Next',
    },
    {
      id: 'r2a-inbound-scanner',
      route: '/inbound/receive',
      element: '[data-tour="tour-inbound-scanner"]',
      title: 'GS1 / barcode wedge',
      description:
        'Aim the scanner at this input. PO → product → bin closes the physical loop. Next we return to Sales Orders to allocate the newly received lots.',
      doneBtnText: 'Next',
      transition: {
        route: '/sales-orders',
        nextStep: 4,
        href: '/sales-orders',
      },
    },
    {
      id: 'r2a-so-allocation',
      route: '/sales-orders',
      element: '[data-tour="tour-so-allocation"]',
      title: 'Outbound allocation',
      description:
        'After putaway posts, Allocate here to reserve FEFO lots against customer demand. That reservation is what Generate Wave turns into physical pick tasks.',
      doneBtnText: 'Next',
    },
    {
      id: 'r2a-finish',
      route: '/sales-orders',
      element: '[data-testid="support-assistant-fab"]',
      title: 'You are ready',
      description:
        'Ask the copilot “what next after receive?” anytime. The Purchase Order → Bin → Sales Order path is the heartbeat of inventory truth.',
      doneBtnText: 'Finish Onboarding',
    },
  ],
};
