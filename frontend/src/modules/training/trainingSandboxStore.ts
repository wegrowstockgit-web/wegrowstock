import { create } from 'zustand';

export type TrainingScenarioId = 'PICKER_INBOUND' | 'MANAGER_ALLOCATION';

export type TrainingStep = {
  id: string;
  instruction: string;
  /** Substring match against tracked UI action labels (case-insensitive). */
  matchLabel: string;
  feedback: string;
};

export type TrainingScenario = {
  id: TrainingScenarioId;
  title: string;
  roleHint: string;
  steps: TrainingStep[];
};

export const TRAINING_SCENARIOS: Record<TrainingScenarioId, TrainingScenario> = {
  PICKER_INBOUND: {
    id: 'PICKER_INBOUND',
    title: 'Inbound receiving practice',
    roleHint: 'PICKER',
    steps: [
      {
        id: 'scan-po',
        instruction: 'Open Receiving and practice scanning a purchase order barcode.',
        matchLabel: 'receiv',
        feedback: 'Great — PO identity locked. Next, verify the SKU matches the carton.',
      },
      {
        id: 'verify-sku',
        instruction: 'Scan or confirm the product SKU on the line.',
        matchLabel: 'confirm',
        feedback: 'SKU verified. Now enter or scan the putaway bin.',
      },
      {
        id: 'putaway',
        instruction: 'Complete putaway into the directed bin.',
        matchLabel: 'putaway',
        feedback: 'Mission complete — in live mode this would raise on-hand.',
      },
    ],
  },
  MANAGER_ALLOCATION: {
    id: 'MANAGER_ALLOCATION',
    title: 'Credit hold → allocate practice',
    roleHint: 'WAREHOUSE_MANAGER',
    steps: [
      {
        id: 'open-hold',
        instruction: 'Open Sales Orders and find an order on credit or stock hold.',
        matchLabel: 'sales',
        feedback: 'Order board open. Next, clear or acknowledge the hold reason.',
      },
      {
        id: 'clear-hold',
        instruction: 'Practice the on-screen action that clears the hold (Customers / credit).',
        matchLabel: 'credit',
        feedback: 'Hold cleared in training. Now allocate the order.',
      },
      {
        id: 'allocate',
        instruction: 'Click Allocate to reserve stock for the wave.',
        matchLabel: 'allocate',
        feedback: 'Mission complete — stock would now be reserved for picking.',
      },
    ],
  },
};

export type BlockedTrainingMutation = {
  timestamp: number;
  method: string;
  url: string;
};

type TrainingSandboxState = {
  activeScenarioId: TrainingScenarioId | null;
  /** Spec alias for {@link activeScenarioId} — non-null while simulator is running. */
  activeRole: TrainingScenarioId | null;
  isActive: boolean;
  stepIndex: number;
  lastFeedback: string | null;
  completed: boolean;
  /** Mutations blocked while training — never sent to the live API. */
  blockedMutations: BlockedTrainingMutation[];
  startScenario: (id: TrainingScenarioId) => void;
  /** Spec alias for {@link startScenario}. */
  toggleSandbox: (role: TrainingScenarioId) => void;
  stopScenario: () => void;
  /** Spec alias for {@link stopScenario}. */
  exitSandbox: () => void;
  /** Advance when a UI breadcrumb label matches the current step. */
  onUiAction: (elementLabel: string) => void;
  /** Record a blocked write so the simulator can show feedback without hitting prod. */
  recordBlockedMutation: (method: string, url: string) => void;
  isTrainingMode: () => boolean;
};

function applyScenario(id: TrainingScenarioId) {
  return {
    activeScenarioId: id,
    activeRole: id,
    isActive: true,
    stepIndex: 0,
    lastFeedback: `Training started: ${TRAINING_SCENARIOS[id].title}. No live stock will change.`,
    completed: false,
    blockedMutations: [] as BlockedTrainingMutation[],
  };
}

function clearScenario() {
  return {
    activeScenarioId: null as TrainingScenarioId | null,
    activeRole: null as TrainingScenarioId | null,
    isActive: false,
    stepIndex: 0,
    lastFeedback: null as string | null,
    completed: false,
    blockedMutations: [] as BlockedTrainingMutation[],
  };
}

export const useTrainingSandboxStore = create<TrainingSandboxState>((set, get) => ({
  activeScenarioId: null,
  activeRole: null,
  isActive: false,
  stepIndex: 0,
  lastFeedback: null,
  completed: false,
  blockedMutations: [],
  startScenario: (id) => set(applyScenario(id)),
  toggleSandbox: (role) => set(applyScenario(role)),
  stopScenario: () => set(clearScenario()),
  exitSandbox: () => set(clearScenario()),
  onUiAction: (elementLabel) => {
    const state = get();
    if (!state.activeScenarioId || state.completed) return;
    const scenario = TRAINING_SCENARIOS[state.activeScenarioId];
    const step = scenario.steps[state.stepIndex];
    if (!step) return;
    const hay = elementLabel.toLowerCase();
    if (!hay.includes(step.matchLabel.toLowerCase())) return;
    const nextIndex = state.stepIndex + 1;
    if (nextIndex >= scenario.steps.length) {
      set({
        stepIndex: nextIndex,
        lastFeedback: step.feedback,
        completed: true,
      });
      return;
    }
    set({
      stepIndex: nextIndex,
      lastFeedback: step.feedback,
      completed: false,
    });
  },
  recordBlockedMutation: (method, url) => {
    if (!get().isTrainingMode()) return;
    set((state) => ({
      blockedMutations: [
        ...state.blockedMutations,
        {
          timestamp: Date.now(),
          method: (method || 'POST').toUpperCase(),
          url: url || '',
        },
      ].slice(-20),
      lastFeedback:
        'Live write blocked in training — practice continues in the simulator only.',
    }));
  },
  isTrainingMode: () => get().activeScenarioId != null || get().isActive,
}));

/** Spec-facing alias for the flight-simulator store. */
export const useSandboxStore = useTrainingSandboxStore;
