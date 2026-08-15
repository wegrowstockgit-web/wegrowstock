import type { ReactNode } from 'react';
import { Bot, User } from 'lucide-react';
import { cn } from '@/lib/utils';

type ChatMessageBubbleProps = {
  role: 'user' | 'assistant';
  children: ReactNode;
  /** Shown while the assistant is waiting for the first token. */
  streaming?: boolean;
  className?: string;
};

/**
 * Role-aligned chat row: user on the right with a person icon, assistant on the left with a bot icon.
 */
export function ChatMessageBubble({
  role,
  children,
  streaming = false,
  className,
}: ChatMessageBubbleProps) {
  const isUser = role === 'user';

  return (
    <div
      className={cn(
        'flex w-full gap-2.5',
        isUser ? 'flex-row-reverse' : 'flex-row',
        className,
      )}
      data-testid={isUser ? 'support-chat-user-row' : 'support-chat-assistant-row'}
      data-role={role}
    >
      <div
        className={cn(
          'mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
          isUser
            ? 'bg-accent text-white'
            : 'border border-border bg-surface text-accent',
        )}
        aria-hidden
        data-testid={isUser ? 'support-chat-user-icon' : 'support-chat-bot-icon'}
      >
        {isUser ? <User className="h-4 w-4" strokeWidth={2.25} /> : <Bot className="h-4 w-4" strokeWidth={2.25} />}
      </div>

      <div
        className={cn(
          'min-w-0 max-w-[min(100%,18.5rem)] rounded-2xl px-3.5 py-2.5 shadow-sm',
          isUser
            ? 'rounded-tr-md bg-accent text-white'
            : 'rounded-tl-md border border-border bg-surface-overlay text-text',
        )}
        data-testid={isUser ? 'support-chat-user-bubble' : 'support-assistant-reply'}
      >
        {isUser ? (
          <p className="whitespace-pre-wrap text-sm leading-relaxed text-white">{children}</p>
        ) : streaming ? (
          <TypingIndicator />
        ) : (
          children
        )}
      </div>
    </div>
  );
}

function TypingIndicator() {
  return (
    <div
      className="flex h-5 items-center gap-1 px-0.5"
      data-testid="support-chat-typing"
      aria-label="Assistant is typing"
      role="status"
    >
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="h-1.5 w-1.5 animate-pulse rounded-full bg-text-muted"
          style={{ animationDelay: `${i * 160}ms` }}
        />
      ))}
    </div>
  );
}
