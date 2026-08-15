/**
 * Floor (Surface B) route detection — core shell concern, not chatbot-specific.
 */
export function isFloorRoute(pathname: string): boolean {
  return (
    pathname.startsWith('/fulfillment') ||
    pathname.startsWith('/inbound') ||
    pathname.startsWith('/cycle-counts') ||
    pathname.startsWith('/manufacturing/terminal') ||
    pathname.startsWith('/returns/receive') ||
    pathname.startsWith('/issue-supplies') ||
    pathname.startsWith('/replenishments') ||
    pathname.startsWith('/cluster-pick') ||
    pathname.startsWith('/pallet-manifests') ||
    pathname.startsWith('/field')
  );
}
