import { useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { Input } from '@/components/ui/Input';
import { cn } from '@/lib/utils';

export function DebouncedSearchInput({
  value,
  onDebouncedChange,
  delay = 300,
  placeholder = 'Search…',
  className,
}: {
  value: string;
  onDebouncedChange: (next: string) => void;
  delay?: number;
  placeholder?: string;
  className?: string;
}) {
  const [local, setLocal] = useState(value);
  const callbackRef = useRef(onDebouncedChange);
  callbackRef.current = onDebouncedChange;

  useEffect(() => {
    setLocal(value);
  }, [value]);

  useEffect(() => {
    if (local === value) return;
    const handle = window.setTimeout(() => {
      callbackRef.current(local);
    }, delay);
    return () => window.clearTimeout(handle);
  }, [local, delay, value]);

  return (
    <div className={cn('relative max-w-md min-w-[12rem] flex-1', className)}>
      <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-text-muted" />
      <Input
        value={local}
        onChange={(e) => setLocal(e.target.value)}
        placeholder={placeholder}
        aria-label="Search table"
        data-testid="table-search"
        className="pl-9"
      />
    </div>
  );
}
