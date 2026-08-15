import { useMutation, useQueryClient } from '@tanstack/react-query';
import { reverseLedgerTransaction } from '@/api/inventory';

export function useReverseTransactionMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (ledgerId: string) => reverseLedgerTransaction(ledgerId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['inventory_ledger'] }),
        queryClient.invalidateQueries({ queryKey: ['inventory_levels'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard_stats'] }),
        // Surface A dashboard uses ['dashboard'] for KPI stats
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ]);
    },
  });
}
