import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TrainingMissionBanner } from './TrainingMissionBanner';
import { useTrainingSandboxStore } from './trainingSandboxStore';

describe('TrainingMissionBanner', () => {
  beforeEach(() => {
    useTrainingSandboxStore.getState().stopScenario();
  });

  it('renders nothing when no scenario is active', () => {
    const { container } = render(<TrainingMissionBanner />);
    expect(container).toBeEmptyDOMElement();
  });

  it('shows flight-simulator banner with step feedback and exit', async () => {
    useTrainingSandboxStore.getState().startScenario('PICKER_INBOUND');
    render(<TrainingMissionBanner />);

    expect(screen.getByTestId('training-mission-banner')).toHaveTextContent(
      /TRAINING SIMULATOR ACTIVE/i,
    );
    expect(screen.getByTestId('training-mission-banner')).toHaveTextContent(/Inbound receiving/i);
    expect(screen.getByTestId('training-mission-feedback')).toBeInTheDocument();

    await userEvent.setup().click(screen.getByTestId('training-mission-exit'));
    expect(useTrainingSandboxStore.getState().isTrainingMode()).toBe(false);
  });
});
