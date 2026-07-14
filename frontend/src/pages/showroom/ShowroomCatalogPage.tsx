import { useMemo, useState } from 'react';

import { useQuery } from '@tanstack/react-query';

import { Minus, Package, Plus, Search } from 'lucide-react';

import { apiClient } from '@/api/client';

import { mapPortalCatalog, type PortalCatalogItemRaw } from '@/api/portal';

import { Button } from '@/components/ui/Button';

import { Card } from '@/components/ui/Card';

import { EmptyState } from '@/components/ui/EmptyState';

import { Input } from '@/components/ui/Input';

import { TableSkeleton } from '@/components/ui/Skeleton';

import { useShowroomCart } from '@/showroom/useShowroomCart';



export function ShowroomCatalogPage() {

  const [search, setSearch] = useState('');

  const [category, setCategory] = useState('ALL');

  const { cart, adjustQty } = useShowroomCart();



  const { data: items = [], isLoading, isError, refetch } = useQuery({

    queryKey: ['portal', 'catalog', search, category],

    queryFn: async () => {

      const params = new URLSearchParams();

      if (search) params.set('q', search);

      if (category !== 'ALL') params.set('category', category);

      const res = await apiClient.get<PortalCatalogItemRaw[]>(

        `/api/v1/portal/catalog?${params}`

      );

      return mapPortalCatalog(res.data);

    },

    retry: false,

  });



  const categories = useMemo(() => {

    const cats = new Set(items.map((i) => i.category).filter(Boolean) as string[]);

    return ['ALL', ...Array.from(cats).sort()];

  }, [items]);



  if (isLoading) {

    return <TableSkeleton rows={6} cols={3} />;

  }



  if (isError) {

    return (

      <EmptyState

        icon={Package}

        title="Unable to load catalog"

        description="Check your connection and try again."

        action={<Button onClick={() => refetch()}>Retry</Button>}

      />

    );

  }



  return (

    <div>

      <div className="mb-6">

        <h1 className="text-2xl font-bold text-text">Catalog</h1>

        <p className="mt-1 text-sm text-text-muted">Tier pricing applied automatically</p>

      </div>



      <div className="mb-6 flex flex-col gap-3 sm:flex-row">

        <div className="relative flex-1">

          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />

          <Input

            value={search}

            onChange={(e) => setSearch(e.target.value)}

            placeholder="Search products..."

            className="pl-9"

          />

        </div>

        <select

          value={category}

          onChange={(e) => setCategory(e.target.value)}

          className="h-10 rounded-md border border-border bg-surface-raised px-3 text-sm text-text"

        >

          {categories.map((cat) => (

            <option key={cat} value={cat}>

              {cat === 'ALL' ? 'All categories' : cat}

            </option>

          ))}

        </select>

      </div>



      {items.length === 0 ? (

        <EmptyState

          icon={Package}

          title="No products available"

          description="Your catalog will appear here when products are published."

        />

      ) : (

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">

          {items.map((item) => {

            const cartLine = cart.find((l) => l.item.id === item.id);

            const qty = cartLine?.quantity ?? 0;



            return (

              <Card key={item.id}>

                <p className="font-mono text-xs text-text-muted">{item.sku}</p>

                <h3 className="mt-1 font-semibold text-text">{item.name}</h3>

                {item.category && (

                  <p className="mt-1 text-xs text-text-muted">{item.category}</p>

                )}

                <p className="mt-3 text-lg font-bold text-text">

                  {item.unitPrice.toLocaleString(undefined, {

                    style: 'currency',

                    currency: item.currency,

                  })}

                </p>

                {item.atp != null && (

                  <p className="text-xs text-text-muted">{item.atp} available</p>

                )}



                <div className="mt-4 flex items-center gap-2">

                  <Button

                    variant="secondary"

                    size="sm"

                    onClick={() => adjustQty(item, -1)}

                    disabled={qty === 0}

                  >

                    <Minus className="h-4 w-4" />

                  </Button>

                  <span className="w-8 text-center font-mono text-sm">{qty}</span>

                  <Button variant="secondary" size="sm" onClick={() => adjustQty(item, 1)}>

                    <Plus className="h-4 w-4" />

                  </Button>

                </div>

              </Card>

            );

          })}

        </div>

      )}

    </div>

  );

}

