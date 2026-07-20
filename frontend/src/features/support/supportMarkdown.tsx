import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';

const STATUS_RE =
  /\b(DRAFT|SUBMITTED|IN_TRANSIT|PARTIALLY_RECEIVED|RECEIVED|CONFIRMED|ALLOCATED|BACKORDERED|PARTIALLY_SHIPPED|SHIPPED|CANCELLED|OPEN|RESOLVED|PENDING_MANAGER_REVIEW|PAID|FAILED|ACTIVE|INVITED|DISABLED)\b/g;

function highlightStatuses(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  let last = 0;
  let match: RegExpExecArray | null;
  const re = new RegExp(STATUS_RE.source, 'g');
  while ((match = re.exec(text)) !== null) {
    if (match.index > last) {
      nodes.push(text.slice(last, match.index));
    }
    nodes.push(
      <span
        key={`${match.index}-${match[1]}`}
        className="mx-0.5 inline-flex rounded-md bg-muted/50 px-1.5 py-0.5 font-mono text-xs font-semibold text-text"
      >
        {match[1]}
      </span>,
    );
    last = match.index + match[0].length;
  }
  if (last < text.length) {
    nodes.push(text.slice(last));
  }
  return nodes.length > 0 ? nodes : [text];
}

function renderInline(text: string): ReactNode {
  // **bold**
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return (
        <strong key={i} className="font-semibold text-text">
          {highlightStatuses(part.slice(2, -2))}
        </strong>
      );
    }
    return <span key={i}>{highlightStatuses(part)}</span>;
  });
}

/**
 * Lightweight markdown renderer for copilot bubbles (no extra dependency).
 * Supports headings, numbered/bulleted lists, bold, and status badge highlights.
 */
export function SupportMarkdown({ text, className }: { text: string; className?: string }) {
  const lines = text.split('\n');
  const blocks: ReactNode[] = [];
  let listBuf: { kind: 'ol' | 'ul'; items: string[] } | null = null;

  const flushList = () => {
    if (!listBuf) return;
    const Tag = listBuf.kind;
    blocks.push(
      <Tag
        key={`list-${blocks.length}`}
        className={cn(
          'my-2 space-y-1 pl-5 text-sm leading-relaxed',
          Tag === 'ol' ? 'list-decimal' : 'list-disc',
        )}
      >
        {listBuf.items.map((item, i) => (
          <li key={i}>{renderInline(item)}</li>
        ))}
      </Tag>,
    );
    listBuf = null;
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] ?? '';
    const heading = /^(#{1,3})\s+(.+)$/.exec(line);
    const ol = /^\d+\.\s+(.+)$/.exec(line);
    const ul = /^[-*]\s+(.+)$/.exec(line);

    if (heading) {
      flushList();
      const level = heading[1]!.length;
      const cls =
        level === 1
          ? 'mt-2 text-base font-semibold text-text'
          : 'mt-2 text-sm font-semibold text-text';
      blocks.push(
        <p key={`h-${i}`} className={cls}>
          {renderInline(heading[2]!)}
        </p>,
      );
      continue;
    }

    if (ol) {
      if (!listBuf || listBuf.kind !== 'ol') {
        flushList();
        listBuf = { kind: 'ol', items: [] };
      }
      listBuf.items.push(ol[1]!);
      continue;
    }

    if (ul) {
      if (!listBuf || listBuf.kind !== 'ul') {
        flushList();
        listBuf = { kind: 'ul', items: [] };
      }
      listBuf.items.push(ul[1]!);
      continue;
    }

    flushList();
    if (line.trim() === '') {
      blocks.push(<div key={`sp-${i}`} className="h-2" />);
      continue;
    }
    blocks.push(
      <p key={`p-${i}`} className="text-sm leading-relaxed text-text">
        {renderInline(line)}
      </p>,
    );
  }
  flushList();

  return (
    <div className={cn('space-y-0.5', className)} data-testid="support-markdown">
      {blocks}
    </div>
  );
}
