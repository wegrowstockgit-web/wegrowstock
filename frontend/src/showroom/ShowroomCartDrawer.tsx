import { useNavigate } from 'react-router-dom';
import { Minus, Plus, ShoppingCart } from 'lucide-react';
import type { CartLine } from '@/showroom/useShowroomCart';
import { Button } from '@/components/ui/Button';
import { SlideOutDrawer } from '@/components/ui/SlideOutDrawer';
import { cn } from '@/lib/utils';

interface ShowroomCartDrawerProps {
  open: boolean;
  onClose: () => void;
  cart: CartLine[];
  onAdjust: (line: CartLine, delta: number) => void;
}

export function ShowroomCartDrawer({ open, onClose, cart, onAdjust }: ShowroomCartDrawerProps) {
  const navigate = useNavigate();
  const total = cart.reduce((sum, l) => sum + l.item.unitPrice * l.quantity, 0);
  const currency = cart[0]?.item.currency ?? 'USD';

  return (
    <SlideOutDrawer
      open={open}
      onClose={onClose}
      title="Your cart"
      description={cart.length === 0 ? 'Browse the catalog to add items' : `${cart.length} line${cart.length === 1 ? '' : 's'}`}
    >
      {cart.length === 0 ? (
        <p className="text-sm text-text-muted">Your cart is empty.</p>
      ) : (
        <ul className="divide-y divide-border">
          {cart.map((line) => (
            <li key={line.item.id} className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="truncate font-medium text-text">{line.item.name}</p>
                <p className="font-mono text-xs text-text-muted">{line.item.sku}</p>
              </div>
              <div className="flex items-center gap-2">
                <Button variant="secondary" size="sm" onClick={() => onAdjust(line, -1)}>
                  <Minus className="h-3.5 w-3.5" />
                </Button>
                <span className="w-6 text-center font-mono text-sm">{line.quantity}</span>
                <Button variant="secondary" size="sm" onClick={() => onAdjust(line, 1)}>
                  <Plus className="h-3.5 w-3.5" />
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {cart.length > 0 && (
        <div className="mt-6 border-t border-border pt-4">
          <div className="flex justify-between text-sm">
            <span className="text-text-muted">Estimated total</span>
            <span className="font-mono font-semibold text-text">
              {total.toLocaleString(undefined, { style: 'currency', currency })}
            </span>
          </div>
          <Button
            className="mt-4 w-full"
            onClick={() => {
              onClose();
              navigate('/showroom/checkout');
            }}
          >
            Proceed to checkout
          </Button>
        </div>
      )}
    </SlideOutDrawer>
  );
}

interface ShowroomCartFabProps {
  count: number;
  onClick: () => void;
}

export function ShowroomCartFab({ count, onClick }: ShowroomCartFabProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'fixed bottom-6 right-6 z-40 flex h-14 w-14 items-center justify-center rounded-full bg-accent text-text-inverse shadow-elevated transition-transform hover:scale-105 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'
      )}
      aria-label={`Open cart${count > 0 ? `, ${count} items` : ''}`}
    >
      <ShoppingCart className="h-6 w-6" />
      {count > 0 && (
        <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-danger px-1 text-xs font-bold text-white">
          {count > 99 ? '99+' : count}
        </span>
      )}
    </button>
  );
}
