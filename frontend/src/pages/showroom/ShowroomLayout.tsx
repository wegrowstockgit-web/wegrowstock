import { useState } from 'react';
import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { CreditCard, LogOut, Package, ShoppingBag, ShoppingCart } from 'lucide-react';
import { signOut } from '@/lib/signOut';
import { cn } from '@/lib/utils';
import { useSessionStore } from '@/stores/session';
import { Button } from '@/components/ui/Button';
import { useShowroomCart } from '@/showroom/useShowroomCart';
import { ShowroomCartDrawer, ShowroomCartFab } from '@/showroom/ShowroomCartDrawer';

const navItems = [
  { to: '/showroom/catalog', label: 'Catalog', icon: Package },
  { to: '/showroom/orders', label: 'Orders', icon: ShoppingCart },
  { to: '/showroom/checkout', label: 'Checkout', icon: ShoppingBag },
  { to: '/showroom/billing', label: 'Billing', icon: CreditCard },
];

export function ShowroomLayout() {
  const user = useSessionStore((s) => s.user);
  const navigate = useNavigate();
  const [cartOpen, setCartOpen] = useState(false);
  const { cart, cartCount, adjustQty } = useShowroomCart();

  const handleSignOut = async () => {
    await signOut();
    navigate('/login', { replace: true });
  };

  return (
    <div className="min-h-screen bg-surface" data-testid="showroom-layout">
      <header className="border-b border-border bg-surface-raised">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent text-text-inverse">
              <Package className="h-5 w-5" />
            </div>
            <div>
              <p className="text-sm font-semibold text-text">Wholesale Portal</p>
              <p className="text-xs text-text-muted">B2B ordering</p>
            </div>
          </div>

          <nav className="hidden items-center gap-1 sm:flex">
            {navItems.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-accent-muted text-accent'
                      : 'text-text-muted hover:bg-surface-overlay hover:text-text'
                  )
                }
              >
                <Icon className="h-4 w-4" />
                {label}
              </NavLink>
            ))}
          </nav>

          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-text-muted sm:inline">
              {user?.displayName ?? user?.email}
            </span>
            <Button variant="ghost" size="sm" onClick={() => void handleSignOut()}>
              <LogOut className="h-4 w-4" />
              Sign out
            </Button>
          </div>
        </div>

        <nav className="flex gap-1 overflow-x-auto border-t border-border px-4 py-2 sm:hidden">
          {navItems.map(({ to, label }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                cn(
                  'whitespace-nowrap rounded-md px-3 py-1.5 text-sm font-medium',
                  isActive ? 'bg-accent-muted text-accent' : 'text-text-muted'
                )
              }
            >
              {label}
            </NavLink>
          ))}
        </nav>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>

      <ShowroomCartFab count={cartCount} onClick={() => setCartOpen(true)} />
      <ShowroomCartDrawer
        open={cartOpen}
        onClose={() => setCartOpen(false)}
        cart={cart}
        onAdjust={(line, delta) => adjustQty(line.item, delta)}
      />
    </div>
  );
}
