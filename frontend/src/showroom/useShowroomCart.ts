import { useCallback, useEffect, useState } from 'react';
import type { PortalCatalogItem } from '@/api/types';

export interface CartLine {
  item: PortalCatalogItem;
  quantity: number;
}

const CART_KEY = 'showroom-cart';
const CART_EVENT = 'showroom-cart-updated';

function loadCart(): CartLine[] {
  try {
    const raw = sessionStorage.getItem(CART_KEY);
    return raw ? (JSON.parse(raw) as CartLine[]) : [];
  } catch {
    return [];
  }
}

function persistCart(cart: CartLine[]) {
  sessionStorage.setItem(CART_KEY, JSON.stringify(cart));
  window.dispatchEvent(new Event(CART_EVENT));
}

export function useShowroomCart() {
  const [cart, setCart] = useState<CartLine[]>(loadCart);

  useEffect(() => {
    const sync = () => setCart(loadCart());
    window.addEventListener(CART_EVENT, sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener(CART_EVENT, sync);
      window.removeEventListener('storage', sync);
    };
  }, []);

  const updateCart = useCallback((updater: (prev: CartLine[]) => CartLine[]) => {
    setCart((prev) => {
      const next = updater(prev);
      persistCart(next);
      return next;
    });
  }, []);

  const addLines = useCallback(
    (lines: Array<{ variantId: string; quantity: number; catalogItem?: PortalCatalogItem }>) => {
      updateCart((prev) => {
        const next = [...prev];
        for (const line of lines) {
          const existing = next.find((l) => l.item.id === line.variantId);
          if (existing) {
            existing.quantity += line.quantity;
          } else if (line.catalogItem) {
            next.push({ item: line.catalogItem, quantity: line.quantity });
          }
        }
        return next;
      });
    },
    [updateCart]
  );

  const adjustQty = useCallback(
    (item: PortalCatalogItem, delta: number) => {
      updateCart((prev) => {
        const existing = prev.find((l) => l.item.id === item.id);
        if (!existing) {
          if (delta <= 0) return prev;
          return [...prev, { item, quantity: delta }];
        }
        const qty = existing.quantity + delta;
        if (qty <= 0) return prev.filter((l) => l.item.id !== item.id);
        return prev.map((l) => (l.item.id === item.id ? { ...l, quantity: qty } : l));
      });
    },
    [updateCart]
  );

  const clearCart = useCallback(() => updateCart(() => []), [updateCart]);

  const cartCount = cart.reduce((sum, l) => sum + l.quantity, 0);

  return { cart, cartCount, updateCart, addLines, adjustQty, clearCart };
}
