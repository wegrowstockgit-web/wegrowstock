/** Settings shells that own ScrollFadePort(s) — clip main so no outer scrollbar. */
export function isSettingsOwnedScrollRoute(pathname: string): boolean {
  if (pathname === '/settings') return true;
  return (
    pathname.startsWith('/settings/profile') ||
    pathname.startsWith('/settings/billing') ||
    pathname.startsWith('/settings/integrations') ||
    pathname.startsWith('/settings/fintech') ||
    pathname.startsWith('/settings/users')
  );
}

/** Document pages that scroll in main with hidden bars + fold cues. */
export function isMainFadeScrollRoute(pathname: string): boolean {
  return pathname === '/' || pathname === '/dashboard';
}

/**
 * Virtualized grids own their scrollport — clip main so only the table moves.
 * Document lists (suppliers, customers, invoices, returns) are NOT locked:
 * AppShell `<main>` is the single vertical scrollbar, flush to the viewport edge.
 */
export function isViewportLockedRoute(pathname: string): boolean {
  return (
    pathname === '/products' ||
    pathname.startsWith('/products/') ||
    pathname === '/purchase-orders' ||
    pathname === '/sales-orders' ||
    pathname === '/mrp' ||
    pathname.startsWith('/purchasing/mrp')
  );
}
