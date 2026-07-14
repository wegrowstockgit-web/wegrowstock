import { NavLink } from 'react-router-dom';

import {

  LayoutDashboard,

  Package,

  ScanLine,

  ShoppingCart,

  FileText,

  FileBarChart,

  Users,

  Truck,

  Settings,

  ClipboardList,

  Boxes,

  Factory,

  RotateCcw,

} from 'lucide-react';

import { cn } from '@/lib/utils';

import { useSessionStore } from '@/stores/session';



type NavItem = {

  to: string;

  label: string;

  icon: React.ComponentType<{ className?: string }>;

  roles?: string[];

  hideForPicker?: boolean;

  hideForViewer?: boolean;

};



const navItems: NavItem[] = [

  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },

  { to: '/products', label: 'Products', icon: Package },

  {

    to: '/fulfillment',

    label: 'Fulfillment',

    icon: ScanLine,

    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],

    hideForViewer: true,

  },

  {

    to: '/cycle-counts',

    label: 'Cycle counts',

    icon: ClipboardList,

    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER', 'PICKER'],

    hideForViewer: true,

  },

  {

    to: '/manufacturing/boms',

    label: 'Manufacturing',

    icon: Factory,

    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],

    hideForPicker: true,

    hideForViewer: true,

  },

  {

    to: '/manufacturing/orders',

    label: 'Production Orders',

    icon: Factory,

    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],

    hideForPicker: true,

    hideForViewer: true,

  },

  {

    to: '/returns',

    label: 'Returns',

    icon: RotateCcw,

    roles: ['OWNER', 'ADMIN', 'WAREHOUSE_MANAGER'],

    hideForPicker: true,

    hideForViewer: true,

  },

  { to: '/purchase-orders', label: 'Purchase Orders', icon: ClipboardList, hideForPicker: true },

  { to: '/sales-orders', label: 'Sales Orders', icon: ShoppingCart, hideForPicker: true },

  { to: '/invoices', label: 'Invoices', icon: FileText, hideForPicker: true },

  {
    to: '/reports',
    label: 'Reports',
    icon: FileBarChart,
    roles: ['OWNER', 'ADMIN'],
    hideForPicker: true,
    hideForViewer: true,
  },

  { to: '/customers', label: 'Customers', icon: Users, hideForPicker: true },

  { to: '/suppliers', label: 'Suppliers', icon: Truck, hideForPicker: true },

];



export function Sidebar() {

  const hasRole = useSessionStore((s) => s.hasRole);

  const isPickerOnly = useSessionStore((s) => s.isPickerOnly);

  const isViewerOnly = useSessionStore((s) => s.isViewerOnly);

  const user = useSessionStore((s) => s.user);



  const visibleItems = navItems.filter((item) => {

    if (item.roles && !hasRole(...item.roles)) return false;

    if (isPickerOnly() && item.hideForPicker) return false;

    if (isViewerOnly() && item.hideForViewer) return false;

    return true;

  });



  const primaryRole = user?.roles[0] ?? 'USER';



  return (

    <aside className="flex h-full w-[var(--sidebar-width)] flex-col border-r border-border bg-surface-raised">

      <div className="flex h-[var(--header-height)] items-center gap-3 border-b border-border bg-gradient-to-r from-accent-muted to-transparent px-4">

        <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent text-text-inverse shadow-sm">

          <Boxes className="h-4 w-4" />

        </div>

        <div>

          <p className="text-sm font-bold tracking-tight text-text">InventorySystem</p>

          <p className="text-xs text-text-muted">WMS & Supply Chain</p>

        </div>

      </div>



      <nav className="flex-1 space-y-1 overflow-y-auto p-3">

        {visibleItems.map(({ to, label, icon: Icon }) => (

          <NavLink

            key={to}

            to={to}

            className={({ isActive }) =>

              cn(

                'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',

                isActive

                  ? 'bg-accent-muted text-accent'

                  : 'text-text-muted hover:bg-surface-overlay hover:text-text'

              )

            }

          >

            <Icon className="h-4 w-4 shrink-0" />

            {label}

          </NavLink>

        ))}

      </nav>



      <div className="border-t border-border p-3">

        <p className="mb-2 px-3 text-xs font-medium uppercase tracking-wide text-text-muted">

          Signed in as

        </p>

        <p className="truncate px-3 text-sm font-medium text-text">

          {user?.displayName ?? user?.email}

        </p>

        <p className="mt-0.5 px-3 text-xs text-text-muted">{primaryRole.replaceAll('_', ' ')}</p>



        {hasRole('ADMIN', 'OWNER') && (

          <NavLink

            to="/settings"

            className={({ isActive }) =>

              cn(

                'mt-3 flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',

                isActive

                  ? 'bg-accent-muted text-accent'

                  : 'text-text-muted hover:bg-surface-overlay hover:text-text'

              )

            }

          >

            <Settings className="h-4 w-4" />

            Settings

          </NavLink>

        )}

      </div>

    </aside>

  );

}


