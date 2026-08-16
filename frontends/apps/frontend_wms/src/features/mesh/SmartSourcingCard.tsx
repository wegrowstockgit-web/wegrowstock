import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { draftPoPath, fetchMeshSourcingSuggestions } from '@/api/mesh';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';

export function SmartSourcingCard() {
  const navigate = useNavigate();
  const { data = [], isLoading } = useQuery({
    queryKey: ['dashboard', 'mesh-sourcing'],
    queryFn: fetchMeshSourcingSuggestions,
  });

  if (isLoading || data.length === 0) {
    return null;
  }

  return (
    <Card data-testid="smart-sourcing-card">
      <CardHeader title="Smart sourcing" description="Low-stock SKUs available from connected mesh partners" />
      <ul className="mt-4 space-y-3">
        {data.map((item) => (
          <li
            key={`${item.variantId}-${item.partnerTenantId}`}
            className="flex flex-wrap items-center justify-between gap-3 rounded-md bg-surface px-3 py-2"
          >
            <p className="text-sm text-text">
              You are running low on <span className="font-semibold">{item.productName}</span>. Your Mesh
              Partner <span className="font-semibold">{item.partnerName}</span> has this in stock.
            </p>
            <Button size="sm" onClick={() => navigate(draftPoPath(item))}>
              Draft PO
            </Button>
          </li>
        ))}
      </ul>
    </Card>
  );
}
