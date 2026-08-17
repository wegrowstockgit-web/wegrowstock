import {
  AlertTriangle,
  ArrowDownUp,
  BarChart3,
  ClipboardCheck,
  Cog,
  Compass,
  Component,
  DollarSign,
  DownloadCloud,
  Factory,
  FileSpreadsheet,
  GitCommit,
  HardDrive,
  Layers,
  LayoutDashboard,
  ListOrdered,
  MapPin,
  Network,
  Package,
  RotateCcw,
  Scan,
  Settings,
  ShoppingCart,
  SlidersHorizontal,
  Truck,
  UploadCloud,
  Users,
  type LucideIcon,
} from 'lucide-react';

export type NavLeafConfig = {
  to: string;
  label: string;
  /** i18n key; Sidebar renders `t(labelKey, label)`. */
  labelKey: string;
  icon: LucideIcon;
  roles?: string[];
  /** Commercial AppModule names that must be entitled (e.g. MESH_NETWORK). */
  modules?: string[];
  hideForPicker?: boolean;
  hideForViewer?: boolean;
  tourAnchor?: string;
  testId?: string;
};

export type NavCategoryConfig = {
  id: string;
  category: string;
  labelKey: string;
  icon: LucideIcon;
  items: NavLeafConfig[];
};

export type NavSoloConfig = NavLeafConfig & { id: string };

/**
 * Nested navigation matrix — every category + leaf icon is unique.
 *
 * Mesh Network is a top-level office item (not the buyer /showroom portal).
 * RFQ inbox and wholesale applications stay on Sales Orders / Customers
 * (filter + tab), not as duplicate Outbound leaves.
 */
export const NAV_MATRIX: {
  solos: NavSoloConfig[];
  categories: NavCategoryConfig[];
} = {
  solos: [
    {
      id: 'dashboard',
      to: '/dashboard',
      label: 'Dashboard',
      labelKey: 'nav.dashboard',
      icon: LayoutDashboard,
      testId: 'nav-dashboard',
    },
    {
      id: 'mesh-network',
      to: '/mesh-network',
      label: 'Mesh Network',
      labelKey: 'nav.meshNetwork',
      icon: Network,
      roles: ['OWNER', 'ADMIN'],
      modules: ['MESH_NETWORK'],
      hideForPicker: true,
      hideForViewer: true,
      testId: 'nav-mesh-network',
    },
  ],
  categories: [
    {
      id: 'inbound',
      category: 'Inbound',
      labelKey: 'nav.inbound',
      icon: DownloadCloud,
      items: [
        {
          to: '/purchase-orders',
          label: 'Purchase Orders',
          labelKey: 'nav.purchaseOrders',
          icon: FileSpreadsheet,
          hideForPicker: true,
          tourAnchor: 'nav-purchase-orders',
        },
        {
          to: '/suppliers',
          label: 'Suppliers',
          labelKey: 'nav.suppliers',
          icon: Factory,
          hideForPicker: true,
        },
        {
          to: '/mrp',
          label: 'MRP reorder',
          labelKey: 'nav.mrpReorder',
          icon: SlidersHorizontal,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/returns',
          label: 'Returns',
          labelKey: 'nav.returns',
          icon: RotateCcw,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
      ],
    },
    {
      id: 'outbound',
      category: 'Outbound',
      labelKey: 'nav.outbound',
      icon: UploadCloud,
      items: [
        {
          to: '/sales-orders',
          label: 'Sales Orders',
          labelKey: 'nav.salesOrders',
          icon: ShoppingCart,
          hideForPicker: true,
          tourAnchor: 'nav-sales-orders',
        },
        {
          to: '/customers',
          label: 'Customers',
          labelKey: 'nav.customers',
          icon: Users,
          hideForPicker: true,
        },
        {
          to: '/invoices',
          label: 'Invoices',
          labelKey: 'nav.invoices',
          icon: DollarSign,
          hideForPicker: true,
        },
        {
          to: '/fulfillment',
          label: 'Fulfillment',
          labelKey: 'nav.fulfillment',
          icon: Scan,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
        {
          to: '/cluster-pick',
          label: 'Cluster pick',
          labelKey: 'nav.clusterPick',
          icon: Layers,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
        {
          to: '/pallet-manifests',
          label: 'Pallet manifests',
          labelKey: 'nav.palletManifests',
          icon: Package,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
      ],
    },
    {
      id: 'inventory',
      category: 'Inventory',
      labelKey: 'nav.inventory',
      icon: Package,
      items: [
        {
          to: '/products',
          label: 'Products',
          labelKey: 'nav.products',
          icon: Layers,
          tourAnchor: 'nav-products',
        },
        {
          to: '/replenishments',
          label: 'Replenishments',
          labelKey: 'nav.replenishments',
          icon: ArrowDownUp,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
        {
          to: '/cycle-counts',
          label: 'Cycle counts',
          labelKey: 'nav.cycleCounts',
          icon: ClipboardCheck,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
        {
          to: '/exceptions',
          label: 'Exceptions',
          labelKey: 'nav.exceptions',
          icon: AlertTriangle,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/compliance/lot-trace',
          label: 'Lot Trace',
          labelKey: 'nav.lotTrace',
          icon: GitCommit,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER'],
        },
      ],
    },
    {
      id: 'manufacturing',
      category: 'Manufacturing',
      labelKey: 'nav.manufacturing',
      icon: Component,
      items: [
        {
          to: '/manufacturing/boms',
          label: 'BOMs',
          labelKey: 'nav.boms',
          icon: Cog,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/manufacturing/orders',
          label: 'Production Orders',
          labelKey: 'nav.productionOrders',
          icon: ListOrdered,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
      ],
    },
    {
      id: 'field',
      category: 'Field',
      labelKey: 'nav.field',
      icon: MapPin,
      items: [
        {
          to: '/issue-supplies',
          label: 'Issue Supplies',
          labelKey: 'nav.issueSupplies',
          icon: HardDrive,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
        {
          to: '/field/truck',
          label: 'Technician Truck',
          labelKey: 'nav.technicianTruck',
          icon: Truck,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
      ],
    },
    {
      id: 'admin',
      category: 'Admin',
      labelKey: 'nav.admin',
      icon: Settings,
      items: [
        {
          to: '/reports',
          label: 'Reports',
          labelKey: 'nav.reports',
          icon: BarChart3,
          roles: ['OWNER', 'ADMIN'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/rtls',
          label: 'RTLS map',
          labelKey: 'nav.rtlsMap',
          icon: Compass,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/settings',
          label: 'Organization',
          labelKey: 'nav.organization',
          icon: SlidersHorizontal,
          roles: ['OWNER', 'ADMIN'],
          hideForPicker: true,
          hideForViewer: true,
        },
      ],
    },
  ],
};

export function assertNoShowroomNav(): boolean {
  const paths = [
    ...NAV_MATRIX.solos.map((item) => item.to),
    ...NAV_MATRIX.categories.flatMap((group) => group.items.map((item) => item.to)),
  ];
  return paths.every((to) => !to.startsWith('/showroom'));
}
