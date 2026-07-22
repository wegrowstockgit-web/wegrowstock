import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ChatMessageBubble } from './ChatMessageBubble';

describe('ChatMessageBubble', () => {
  it('renders user icon and right-aligned bubble', () => {
    render(
      <ChatMessageBubble role="user">How do I resolve these holds?</ChatMessageBubble>,
    );
    expect(screen.getByTestId('support-chat-user-icon')).toBeInTheDocument();
    expect(screen.getByTestId('support-chat-user-bubble')).toHaveTextContent(/holds/i);
    expect(screen.getByTestId('support-chat-user-row')).toHaveAttribute('data-role', 'user');
  });

  it('renders bot icon and typing indicator while streaming', () => {
    render(
      <ChatMessageBubble role="assistant" streaming>
        ignored while streaming
      </ChatMessageBubble>,
    );
    expect(screen.getByTestId('support-chat-bot-icon')).toBeInTheDocument();
    expect(screen.getByTestId('support-chat-typing')).toBeInTheDocument();
    expect(screen.getByTestId('support-assistant-reply')).not.toHaveTextContent(/ignored/);
  });
});
