import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { SupportAssistantWidget } from './SupportAssistantWidget';
import { useSessionStore } from '@/stores/session';
import {
  executeSupportAction,
  executeSupportActionDraft,
  fetchSupportInsight,
  streamSupportChat,
} from './supportChatApi';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { ToastProvider } from '@/components/ui/Toast';
import { useTrainingSandboxStore } from '@/modules/training/trainingSandboxStore';
import { compressImageForUpload } from '@/utils/imageCompression';

vi.mock('./supportChatApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./supportChatApi')>();
  return {
    ...actual,
    streamSupportChat: vi.fn(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('Scan the PO barcode on your handheld.');
      handlers.onDone?.();
    }),
    executeSupportAction: vi.fn(async () => ({ ok: true, cycleCountId: 'cc-1' })),
    executeSupportActionDraft: vi.fn(async () => ({ ok: true, cycleCountId: 'cc-draft' })),
    fetchSupportInsight: vi.fn(async () => null),
  };
});

vi.mock('@/utils/imageCompression', () => ({
  compressImageForUpload: vi.fn(async (file: File) => file),
}));

function renderWidget(route = '/fulfillment') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ToastProvider>
        <SupportAssistantWidget />
      </ToastProvider>
    </MemoryRouter>,
  );
}

describe('SupportAssistantWidget', () => {
  beforeEach(() => {
    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('Scan the PO barcode on your handheld.');
      handlers.onDone?.();
    });
    vi.mocked(executeSupportAction).mockResolvedValue({ ok: true, cycleCountId: 'cc-1' });
    vi.mocked(executeSupportActionDraft).mockReset();
    vi.mocked(executeSupportActionDraft).mockResolvedValue({ ok: true, cycleCountId: 'cc-draft' });
    vi.mocked(fetchSupportInsight).mockResolvedValue(null);
    vi.mocked(streamSupportChat).mockClear();
    vi.mocked(executeSupportActionDraft).mockClear();
    useTrainingSandboxStore.getState().stopScenario();
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

  it('does not poll insights or show the FAB on the login shell', () => {
    renderWidget('/login');
    expect(screen.queryByTestId('support-assistant-fab')).not.toBeInTheDocument();
    expect(fetchSupportInsight).not.toHaveBeenCalled();
  });

  it('opens panel and streams a role-aware reply', async () => {
    const user = userEvent.setup();
    renderWidget();

    await user.click(screen.getByTestId('support-assistant-fab'));
    expect(screen.getByTestId('support-assistant-panel')).toBeInTheDocument();
    await user.type(screen.getByTestId('support-assistant-input'), 'How do I process inbound?');
    await user.click(screen.getByTestId('support-assistant-send'));

    await waitFor(() => {
      expect(screen.getByTestId('support-assistant-reply')).toHaveTextContent(/Scan the PO barcode/i);
    });
    expect(screen.getByTestId('support-chat-user-icon')).toBeInTheDocument();
    expect(screen.getAllByTestId('support-chat-bot-icon').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByTestId('support-chat-user-bubble')).toHaveTextContent(/How do I process inbound/i);
  });

  it('shows proactive insight pill when insights API returns a bottleneck', async () => {
    vi.mocked(fetchSupportInsight).mockResolvedValue(
      '💡 3 orders are currently stuck on Credit Hold. Tap to review.',
    );
    renderWidget('/sales-orders');

    await waitFor(() => {
      expect(screen.getByTestId('support-proactive-insight')).toHaveTextContent(/Credit Hold/i);
    });
    const user = userEvent.setup();
    await user.click(screen.getByTestId('support-assistant-fab'));
    expect(screen.getByTestId('support-camera-button')).toBeInTheDocument();
    expect(screen.getByTestId('support-proactive-insight-panel')).toHaveTextContent(/Credit Hold/i);
    await user.click(screen.getByTestId('support-proactive-insight-panel'));
    await waitFor(() => {
      expect(streamSupportChat).toHaveBeenCalledWith(
        'How do I resolve these holds?',
        expect.anything(),
        expect.stringContaining('/sales-orders'),
        expect.anything(),
        expect.anything(),
        expect.anything(),
      );
    });
  });

  it('renders action draft card and approves execution', async () => {
    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('I can generate that cycle count for you.');
      handlers.onDone?.({
        ok: true,
        actionDraft: {
          title: 'Generate cycle count for Aisle-4',
          description: 'Creates a count worksheet for Aisle-4.',
          targetEndpoint: '/api/v1/cycle-counts',
          httpMethod: 'POST',
          payload: { supportAction: 'generateCycleCount', zoneId: 'Aisle-4' },
        },
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
    renderWidget('/cycle-counts');

    await user.click(screen.getByTestId('support-assistant-fab'));
    await user.type(screen.getByTestId('support-assistant-input'), 'Generate cycle count for zone Aisle-4');
    await user.click(screen.getByTestId('support-assistant-send'));

    expect(await screen.findByTestId('support-action-draft')).toHaveTextContent(/Aisle-4/i);
    await user.click(screen.getByTestId('support-draft-approve'));
    await waitFor(() => {
      expect(executeSupportActionDraft).toHaveBeenCalled();
    });
    await waitFor(() => {
      expect(screen.getByTestId('support-draft-approved')).toHaveTextContent(/✓ Executed/i);
    });
    await waitFor(() => {
      expect(screen.getByTestId('support-draft-executed-badge')).toBeInTheDocument();
    });
  });

  it('marks draft failed when approve soft-fails', async () => {
    vi.mocked(executeSupportActionDraft).mockResolvedValue({
      ok: false,
      error: 'Location not found for barcode: Missing',
    });
    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('I can start that count.');
      handlers.onDone?.({
        ok: true,
        actionDraft: {
          title: 'Generate cycle count for Missing',
          description: 'Creates a worksheet',
          targetEndpoint: '/api/v1/cycle-counts',
          httpMethod: 'POST',
          payload: { supportAction: 'generateCycleCount', zoneId: 'Missing' },
        },
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
    renderWidget('/cycle-counts');
    await user.click(screen.getByTestId('support-assistant-fab'));
    await user.type(screen.getByTestId('support-assistant-input'), 'Generate cycle count for zone Missing');
    await user.click(screen.getByTestId('support-assistant-send'));
    await user.click(await screen.findByTestId('support-draft-approve'));

    await waitFor(() => {
      expect(screen.getByTestId('support-draft-failed')).toBeInTheDocument();
    });
  });

  it('dismisses an action draft without executing', async () => {
    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('I can un-allocate that for you.');
      handlers.onDone?.({
        ok: true,
        actionDraft: {
          title: 'Un-allocate reserved stock',
          description: 'Releases reserved units back to open stock.',
          targetEndpoint: '/api/v1/sales-orders/SO-1/allocate',
          httpMethod: 'POST',
          payload: { intent: 'unallocate', orderId: 'SO-1' },
        },
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
    renderWidget('/sales-orders');
    await user.click(screen.getByTestId('support-assistant-fab'));
    await user.type(screen.getByTestId('support-assistant-input'), 'Please unallocate this order');
    await user.click(screen.getByTestId('support-assistant-send'));

    expect(await screen.findByTestId('support-action-draft')).toBeInTheDocument();
    await user.click(screen.getByTestId('support-draft-cancel'));
    await waitFor(() => {
      expect(screen.queryByTestId('support-action-draft')).not.toBeInTheDocument();
    });
    expect(executeSupportActionDraft).not.toHaveBeenCalled();
  });

  it('starts training mission from panel controls', async () => {
    const user = userEvent.setup();
    renderWidget('/dashboard');

    await user.click(screen.getByTestId('support-assistant-fab'));
    await user.click(screen.getByTestId('support-training-PICKER_INBOUND'));
    expect(useTrainingSandboxStore.getState().activeScenarioId).toBe('PICKER_INBOUND');
    expect(useTrainingSandboxStore.getState().isActive).toBe(true);
    expect(screen.getByTestId('support-training-simulator-header')).toHaveTextContent(
      /TRAINING SIMULATOR ACTIVE/i,
    );
  });

  it('intercepts Action Draft approve while training simulator is active', async () => {
    useTrainingSandboxStore.getState().startScenario('MANAGER_ALLOCATION');
    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('I can generate that cycle count.');
      handlers.onDone?.({
        ok: true,
        actionDraft: {
          title: 'Generate cycle count for Aisle-4',
          description: 'Creates a worksheet',
          targetEndpoint: '/api/v1/cycle-counts',
          httpMethod: 'POST',
          payload: { supportAction: 'generateCycleCount', zoneId: 'Aisle-4' },
        },
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
    renderWidget('/cycle-counts');
    await user.click(screen.getByTestId('support-assistant-fab'));
    await user.type(screen.getByTestId('support-assistant-input'), 'Generate cycle count');
    await user.click(screen.getByTestId('support-assistant-send'));
    await user.click(await screen.findByTestId('support-draft-approve'));

    await waitFor(() => {
      expect(screen.getByText(/Training scenario completed successfully/i)).toBeInTheDocument();
    });
    expect(executeSupportActionDraft).not.toHaveBeenCalled();
  });

  it('compresses a camera photo and sends Base64 with the chat payload', async () => {
    const user = userEvent.setup();
    renderWidget('/inbound/receive');

    await user.click(screen.getByTestId('support-assistant-fab'));
    const file = new File(['fake-image-bytes'], 'damage.jpg', { type: 'image/jpeg' });
    const input = screen.getByTestId('support-camera-input');
    await user.upload(input, file);

    await waitFor(() => {
      expect(compressImageForUpload).toHaveBeenCalled();
      expect(screen.getByTestId('support-image-pending')).toHaveTextContent(/damage\.jpg/i);
      expect(screen.getByTestId('support-image-thumbnail')).toBeInTheDocument();
    });

    await user.click(screen.getByTestId('support-assistant-send'));
    await waitFor(() => {
      expect(streamSupportChat).toHaveBeenCalledWith(
        expect.any(String),
        expect.any(Array),
        expect.any(String),
        expect.any(Object),
        expect.any(AbortSignal),
        expect.objectContaining({
          imageBase64: expect.any(String),
          base64Image: expect.any(String),
          imageMimeType: expect.stringMatching(/image\//),
        }),
      );
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
    renderWidget('/cycle-counts');

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

  it('SPOTLIGHT chip rings the matching DOM target for 3 seconds', async () => {
    document.body.innerHTML += '<button data-tour="confirm-receiving">Confirm Receiving</button>';
    const target = document.querySelector('[data-tour="confirm-receiving"]') as HTMLElement;
    target.scrollIntoView = vi.fn();

    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('**Diagnosis:** Tap Confirm Receiving on this page.');
      handlers.onAction?.({
        type: 'action_chip',
        action: 'SPOTLIGHT',
        label: 'Highlight Confirm Receiving',
        target: '[data-tour="confirm-receiving"]',
        params: { target: '[data-tour="confirm-receiving"]' },
      });
      handlers.onDone?.({ ok: true, followUpQuestions: [] });
    });

    const user = userEvent.setup();
    renderWidget('/inbound/receive');
    await user.click(screen.getByTestId('support-assistant-fab'));
    await user.type(screen.getByTestId('support-assistant-input'), 'Where is Confirm Receiving?');
    await user.click(screen.getByTestId('support-assistant-send'));

    const chip = await screen.findByTestId('support-action-chip');
    expect(chip).toHaveAttribute('data-action', 'SPOTLIGHT');
    await user.click(chip);
    expect(target.classList.contains('support-spotlight-ring')).toBe(true);
  });

  it('START_TOUR chip invokes preferencesStore.startTour', async () => {
    const startTour = vi.spyOn(usePreferencesStore.getState(), 'startTour');
    vi.mocked(streamSupportChat).mockImplementation(async (_msg, _roles, _route, handlers) => {
      handlers.onToken('**Diagnosis:** I can walk you through receiving to allocation.');
      handlers.onAction?.({
        type: 'action_chip',
        action: 'START_TOUR',
        label: 'Start Route Walkthrough',
        target: 'receiving-to-allocation',
        params: { target: 'receiving-to-allocation' },
      });
      handlers.onDone?.({ ok: true, followUpQuestions: [] });
    });

    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u3',
        email: 'mgr@demo.test',
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
    renderWidget('/dashboard');

    await user.click(screen.getByTestId('support-assistant-fab'));
    await user.type(screen.getByTestId('support-assistant-input'), 'Train me end-to-end');
    await user.click(screen.getByTestId('support-assistant-send'));

    const tourChip = await screen.findByTestId('support-action-chip');
    expect(tourChip).toHaveAttribute('data-action', 'START_TOUR');
    await user.click(tourChip);
    expect(startTour).toHaveBeenCalledWith('receiving-to-allocation');
    startTour.mockRestore();
  });
});
