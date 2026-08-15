/**
 * Design-system table primitives (Surface A).
 * Canonical implementation lives in `@/components/ui/Table` — re-exported here
 * for the `ui/primitives` import path used by layout / grid tracks.
 */
export {
  Table,
  TableHeader,
  TableBody,
  TableRow,
  TableHead,
  TableCell,
} from '@/components/ui/Table';
export type { SortState, SortDir } from '@/hooks/useClientSort';
export { useClientSort } from '@/hooks/useClientSort';
