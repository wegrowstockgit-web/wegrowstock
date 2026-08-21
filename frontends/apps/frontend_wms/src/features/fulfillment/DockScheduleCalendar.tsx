import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { unwrapPageItems } from '@/api/page';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Input } from '@/components/ui/Input';
import { Select } from '@/components/ui/Select';
import { useToast } from '@/components/ui/Toast';
import { useActiveWarehouseStore } from '@/stores/activeWarehouse';
import { cn } from '@/lib/utils';

interface DockAppointment {
  id: string;
  warehouseId: string;
  dockDoorNumber: number;
  purchaseOrderId?: string | null;
  carrierName?: string | null;
  driverName?: string | null;
  truckLicensePlate?: string | null;
  appointmentStart: string;
  appointmentEnd: string;
  status: string;
}

const DOORS = [1, 2, 3, 4, 5, 6];
const HOURS = Array.from({ length: 24 }, (_, i) => i);

function hourLabel(h: number) {
  return `${String(h).padStart(2, '0')}:00`;
}

export function DockScheduleCalendar() {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const warehouseId = useActiveWarehouseStore((s) => s.warehouseId);
  const [day, setDay] = useState(() => new Date().toISOString().slice(0, 10));
  const [modalOpen, setModalOpen] = useState(false);
  const [door, setDoor] = useState(1);
  const [startHour, setStartHour] = useState(8);
  const [poNumber, setPoNumber] = useState('');
  const [carrier, setCarrier] = useState('');
  const [driver, setDriver] = useState('');
  const [plate, setPlate] = useState('');

  const from = `${day}T00:00:00.000Z`;
  const to = `${day}T23:59:59.999Z`;

  const { data: appointments = [], isLoading } = useQuery({
    queryKey: ['dock-appointments', warehouseId, day],
    enabled: !!warehouseId,
    queryFn: async () =>
      (
        await apiClient.get<DockAppointment[]>('/api/v1/dock-appointments', {
          params: { warehouseId, from, to },
        })
      ).data,
  });

  const byDoor = useMemo(() => {
    const map = new Map<number, DockAppointment[]>();
    for (const d of DOORS) map.set(d, []);
    for (const a of appointments) {
      const list = map.get(a.dockDoorNumber) ?? [];
      list.push(a);
      map.set(a.dockDoorNumber, list);
    }
    return map;
  }, [appointments]);

  const bookMutation = useMutation({
    mutationFn: async () => {
      const start = new Date(`${day}T${hourLabel(startHour)}:00.000Z`);
      const end = new Date(start.getTime() + 60 * 60 * 1000);
      // Resolve PO id by number if provided
      let purchaseOrderId: string | null = null;
      if (poNumber.trim()) {
        const pos = unwrapPageItems<{ id: string; number: string }>(
          (await apiClient.get('/api/v1/purchase-orders', { params: { page: 1, size: 100, search: poNumber.trim() } }))
            .data,
        );
        const match = pos.find((p) => p.number.toLowerCase() === poNumber.trim().toLowerCase());
        purchaseOrderId = match?.id ?? null;
      }
      await apiClient.post('/api/v1/dock-appointments', {
        warehouseId,
        dockDoorNumber: door,
        purchaseOrderId,
        carrierName: carrier || null,
        driverName: driver || null,
        truckLicensePlate: plate || null,
        appointmentStart: start.toISOString(),
        appointmentEnd: end.toISOString(),
      });
    },
    onSuccess: () => {
      toast('Dock appointment scheduled', { tone: 'success' });
      setModalOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['dock-appointments'] });
    },
    onError: () => toast('Could not schedule — check for door conflicts', { tone: 'danger' }),
  });

  const checkInMutation = useMutation({
    mutationFn: async (id: string) => apiClient.post(`/api/v1/dock-appointments/${id}/check-in`),
    onSuccess: () => {
      toast('Driver checked in', { tone: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['dock-appointments'] });
    },
  });

  return (
    <div className="space-y-4 p-4 sm:p-6" data-testid="dock-schedule-calendar">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-text">Dock door schedule</h1>
          <p className="mt-1 text-sm text-text-muted">Yard appointments by door and hour</p>
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <Input
            label="Day"
            type="date"
            value={day}
            onChange={(e) => setDay(e.target.value)}
            data-testid="dock-day"
          />
          <Button data-testid="dock-book-open" onClick={() => setModalOpen(true)} disabled={!warehouseId}>
            Book slot
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader title="24-hour door grid" description="Click a block to check in a scheduled truck" />
        {!warehouseId ? (
          <p className="px-4 pb-4 text-sm text-warning">Select an active warehouse first.</p>
        ) : isLoading ? (
          <p className="px-4 pb-4 text-sm text-text-muted">Loading…</p>
        ) : (
          <div className="overflow-x-auto px-2 pb-4">
            <div className="min-w-[64rem]">
              <div className="grid grid-cols-[4rem_repeat(24,minmax(2.5rem,1fr))] gap-px bg-border">
                <div className="bg-surface-raised p-1 text-xs font-semibold text-text">Door</div>
                {HOURS.map((h) => (
                  <div key={h} className="bg-surface-raised p-1 text-center text-[10px] text-text-muted">
                    {h}
                  </div>
                ))}
                {DOORS.map((d) => (
                  <div key={`row-${d}`} className="contents">
                    <div className="bg-surface-raised p-2 text-sm font-medium text-text">D{d}</div>
                    {HOURS.map((h) => {
                      const hit = (byDoor.get(d) ?? []).find((a) => {
                        const start = new Date(a.appointmentStart).getUTCHours();
                        return start === h;
                      });
                      return (
                        <button
                          key={`${d}-${h}`}
                          type="button"
                          data-testid={`dock-slot-${d}-${h}`}
                          className={cn(
                            'min-h-10 bg-surface p-0.5 text-[10px] leading-tight',
                            hit?.status === 'CHECKED_IN' && 'bg-accent-muted text-accent',
                            hit?.status === 'SCHEDULED' && 'bg-warning/20 text-warning',
                            hit?.status === 'UNLOADING' && 'bg-success/20 text-success',
                            !hit && 'hover:bg-surface-overlay',
                          )}
                          onClick={() => {
                            if (!hit) {
                              setDoor(d);
                              setStartHour(h);
                              setModalOpen(true);
                              return;
                            }
                            if (hit.status === 'SCHEDULED') {
                              checkInMutation.mutate(hit.id);
                            }
                          }}
                          title={hit ? `${hit.carrierName ?? 'Truck'} · ${hit.status}` : 'Book'}
                        >
                          {hit ? hit.status.slice(0, 3) : ''}
                        </button>
                      );
                    })}
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </Card>

      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog">
          <div className="w-full max-w-md rounded-xl bg-surface-raised p-5 shadow-xl" data-testid="dock-book-modal">
            <h2 className="text-lg font-semibold text-text">Book dock appointment</h2>
            <div className="mt-3 space-y-3">
              <Select label="Door" value={String(door)} onChange={(e) => setDoor(Number(e.target.value))}>
                {DOORS.map((d) => (
                  <option key={d} value={d}>
                    Door {d}
                  </option>
                ))}
              </Select>
              <Select
                label="Start hour (UTC)"
                value={String(startHour)}
                onChange={(e) => setStartHour(Number(e.target.value))}
              >
                {HOURS.map((h) => (
                  <option key={h} value={h}>
                    {hourLabel(h)}
                  </option>
                ))}
              </Select>
              <Input label="PO number" value={poNumber} onChange={(e) => setPoNumber(e.target.value)} />
              <Input label="Carrier" value={carrier} onChange={(e) => setCarrier(e.target.value)} />
              <Input label="Driver" value={driver} onChange={(e) => setDriver(e.target.value)} />
              <Input label="Plate" value={plate} onChange={(e) => setPlate(e.target.value)} />
            </div>
            <div className="mt-4 flex justify-end gap-2">
              <Button variant="secondary" onClick={() => setModalOpen(false)}>
                Cancel
              </Button>
              <Button data-testid="dock-book-submit" loading={bookMutation.isPending} onClick={() => bookMutation.mutate()}>
                Schedule
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
