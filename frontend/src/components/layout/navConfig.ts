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
  icon: LucideIcon;
  roles?: string[];
  hideForPicker?: boolean;
  hideForViewer?: boolean;
  tourAnchor?: string;
};

export type NavCategoryConfig = {
  id: string;
  category: string;
  icon: LucideIcon;
  items: NavLeafConfig[];
};

export type NavSoloConfig = NavLeafConfig & { id: string };

/**
 * Nested navigation matrix — every category + leaf icon is unique.
 *
 * Inbound (DownloadCloud) → PO FileSpreadsheet, Suppliers Factory
 * Outbound (UploadCloud) → SO ShoppingCart, Customers Users, Invoices DollarSign
 * Inventory (Package) → Products Layers, Replenishments ArrowDownUp, Cycle Counts ClipboardCheck…
 * Field (MapPin) → Issue Supplies HardDrive, Tech Truck Truck
 * Admin (Settings) → Reports BarChart3, RTLS Compass
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
      icon: LayoutDashboard,
    },
  ],
  categories: [
    {
      id: 'inbound',
      category: 'Inbound',
      icon: DownloadCloud,
      items: [
        {
          to: '/purchase-orders',
          label: 'Purchase Orders',
          icon: FileSpreadsheet,
          hideForPicker: true,
          tourAnchor: 'nav-purchase-orders',
        },
        {
          to: '/suppliers',
          label: 'Suppliers',
          icon: Factory,
          hideForPicker: true,
        },
        {
          to: '/returns',
          label: 'Returns',
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
      icon: UploadCloud,
      items: [
        {
          to: '/sales-orders',
          label: 'Sales Orders',
          icon: ShoppingCart,
          hideForPicker: true,
          tourAnchor: 'nav-sales-orders',
        },
        {
          to: '/customers',
          label: 'Customers',
          icon: Users,
          hideForPicker: true,
        },
        {
          to: '/invoices',
          label: 'Invoices',
          icon: DollarSign,
          hideForPicker: true,
        },
        {
          to: '/fulfillment',
          label: 'Fulfillment',
          icon: Scan,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
      ],
    },
    {
      id: 'inventory',
      category: 'Inventory',
      icon: Package,
      items: [
        {
          to: '/products',
          label: 'Products',
          icon: Layers,
          tourAnchor: 'nav-products',
        },
        {
          to: '/replenishments',
          label: 'Replenishments',
          icon: ArrowDownUp,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
        {
          to: '/cycle-counts',
          label: 'Cycle counts',
          icon: ClipboardCheck,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
        {
          to: '/exceptions',
          label: 'Exceptions',
          icon: AlertTriangle,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/compliance/lot-trace',
          label: 'Lot Trace',
          icon: GitCommit,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER', 'VIEWER'],
        },
      ],
    },
    {
      id: 'manufacturing',
      category: 'Manufacturing',
      icon: Component,
      items: [
        {
          to: '/manufacturing/boms',
          label: 'BOMs',
          icon: Cog,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/manufacturing/orders',
          label: 'Production Orders',
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
      icon: MapPin,
      items: [
        {
          to: '/issue-supplies',
          label: 'Issue Supplies',
          icon: HardDrive,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
        {
          to: '/field/truck',
          label: 'Technician Truck',
          icon: Truck,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],
          hideForViewer: true,
        },
      ],
    },
    {
      id: 'admin',
      category: 'Admin',
      icon: Settings,
      items: [
        {
          to: '/reports',
          label: 'Reports',
          icon: BarChart3,
          roles: ['OWNER', 'ADMIN'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/rtls',
          label: 'RTLS map',
          icon: Compass,
          roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],
          hideForPicker: true,
          hideForViewer: true,
        },
        {
          to: '/settings',
          label: 'Organization',
          icon: SlidersHorizontal,
          roles: ['OWNER', 'ADMIN'],
          hideForPicker: true,
          hideForViewer: true,
        },
      ],
    },
  ],
};
