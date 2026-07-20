import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { SupportAssistantWidget } from './SupportAssistantWidget';
import { useSessionStore } from '@/stores/session';
import { executeSupportAction, streamSupportChat } from './supportChatApi';

vi.mock('./supportChatApi', () => ({
  streamSupportChat: vi.fn(async (_msg, _roles, _route, handlers) => {
    handlers.onToken('Scan the PO barcode on your handheld.');
    handlers.onDone?.();
  }),
  executeSupportAction: vi.fn(async () => ({ ok: true, cycleCountId: 'cc-1' })),
}));

describe('SupportAssistantWidget', () => {
  beforeEach(() => {
    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('Scan the PO barcode on your handheld.');
      handlers.onDone?.();
    });
    vi.mocked(executeSupportAction).mockResolvedValue({ ok: true, cycleCountId: 'cc-1' });
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'picker@demo.test',
        displayName: 'Picker',
        roles: ['PICKER'],
        warehouseIds: [],
        avatarUrl: null,
        tenantId: 't1',
      },
      lastRequestId: null,
      primarySession: null,
    });
  });

  it('opens panel and streams a role-aware reply', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/fulfillment']}>
        <SupportAssistantWidget />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('support-assistant-fab'));
    expect(screen.getByTestId('support-assistant-panel')).toBeInTheDocument();
    await user.type(screen.getByTestId('support-assistant-input'), 'How do I process inbound?');
    await user.click(screen.getByTestId('support-assistant-send'));

    await waitFor(() => {
      expect(screen.getByTestId('support-assistant-reply')).toHaveTextContent(/Scan the PO barcode/i);
    });
  });

  it('renders action chips, follow-ups, and executes platform buttons', async () => {
    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers, _signal, options) => {
      expect(options?.pageState).toBeTruthy();
      handlers.onToken('**Diagnosis:** I can start a cycle count for Aisle-4.\n\n1. Confirm the button.');
      handlers.onAction?.({
        type: 'action_button',
        action: 'generateCycleCount',
        label: 'Generate cycle count for Aisle-4',
        params: { zoneId: 'Aisle-4' },
      });
      handlers.onAction?.({
        type: 'action_chip',
        action: 'NAVIGATE',
        label: 'Open Cycle Counts',
        target: '/cycle-counts',
        params: { target: '/cycle-counts' },
      });
      handlers.onDone?.({
        ok: true,
        followUpQuestions: ['How do I undo the last step safely?'],
      });
    });

    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u2',
        email: 'manager@demo.test',
        displayName: 'Manager',
        roles: ['WAREHOUSE_MANAGER'],
        warehouseIds: [],
        avatarUrl: null,
        tenantId: 't1',
      },
      lastRequestId: null,
      primarySession: null,
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/cycle-counts']}>
        <SupportAssistantWidget />
      </MemoryRouter>,
    );

    await user.click(screen.getByTestId('support-assistant-fab'));
    await user.type(screen.getByTestId('support-assistant-input'), 'Generate cycle count for zone Aisle-4');
    await user.click(screen.getByTestId('support-assistant-send'));

    const actionBtn = await screen.findByTestId('support-action-button');
    expect(actionBtn).toHaveTextContent(/Generate cycle count for Aisle-4/i);
    expect(actionBtn).toHaveAttribute('data-action', 'generateCycleCount');
    expect(screen.getByTestId('support-action-chip')).toHaveAttribute('data-action', 'NAVIGATE');
    expect(screen.getByTestId('support-follow-up')).toHaveTextContent(/undo/i);

    await user.click(actionBtn);
    await waitFor(() => {
      expect(executeSupportAction).toHaveBeenCalledWith('generateCycleCount', { zoneId: 'Aisle-4' });
    });
    await waitFor(() => {
      expect(screen.getByText(/Cycle count cc-1 is ready/i)).toBeInTheDocument();
    });
  });
});

