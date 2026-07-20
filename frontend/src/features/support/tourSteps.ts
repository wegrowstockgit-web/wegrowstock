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
  return pathname.startsWith(stepRoute.endsWith('/') ? stepRoute : `${stepRoute}/`)
    || pathname.startsWith(stepRoute);
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
  'receiving-to-allocation': [
    {
      id: 'r2a-po',
      route: '/purchase-orders',
      element: '[data-tour="nav-purchase-orders"]',
      title: 'Start at purchase orders',
      description:
        'Office staff submit the PO document here. That digital record is what the dock scanner will match — without it, inbound scans cannot post inventory.',
    },
    {
      id: 'r2a-inbound',
      route: '/inbound/receive',
      element: '[data-tour="inbound-receive"]',
      title: 'Receive on the floor',
      description:
        'Confirming this PO on the handheld (scan PO → product → bin) unlocks inventory for the B2B portal and frees stock for later allocation on Sales Orders.',
    },
    {
      id: 'r2a-allocate',
      route: '/sales-orders',
      element: '[data-tour="nav-sales-orders"]',
      title: 'Allocate after receive',
      description:
        'After putaway posts, Allocate here to reserve FEFO lots against customer orders. That reservation is what Generate Wave turns into physical pick tasks.',
    },
    {
      id: 'r2a-copilot',
      route: '/sales-orders',
      element: '[data-testid="support-assistant-fab"]',
      title: 'Workflow copilot',
      description:
        'Ask “what next after receive?” — the agent traces Purchase Order → Bin → Sales Order edges so you never skip the digital step that unlocks the floor.',
    },
  ],
};
