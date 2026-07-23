import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import type { ThermalPrinter } from '@/api/types';

const PRINTERS_KEY = ['thermal-printers'] as const;

export function useThermalPrinter() {
  const queryClient = useQueryClient();

  const listPrinters = useQuery({
    queryKey: PRINTERS_KEY,
    queryFn: async () =>
      (await apiClient.get<ThermalPrinter[]>('/api/v1/thermal-printers')).data,
    retry: false,
  });

  const printMutation = useMutation({
    mutationFn: async ({
      zplString,
      printerId,
    }: {
      zplString: string;
      printerId?: string;
    }) => {
      const path = printerId
        ? `/api/v1/thermal-printers/${printerId}/print`
        : '/api/v1/thermal-printers/print-default';
      await apiClient.post(path, { zpl: zplString });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PRINTERS_KEY });
    },
  });

  const printLabel = async (zplString: string, printerId?: string) => {
    await printMutation.mutateAsync({ zplString, printerId });
  };

  return {
    printers: listPrinters.data ?? [],
    isLoadingPrinters: listPrinters.isLoading,
    printersError: listPrinters.error,
    refetchPrinters: listPrinters.refetch,
    printLabel,
    isPrinting: printMutation.isPending,
    printError: printMutation.error,
  };
}
