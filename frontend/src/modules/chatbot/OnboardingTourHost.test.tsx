import { describe, expect, it, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { OnboardingTourHost } from './OnboardingTourHost';
import { usePreferencesStore } from '@/stores/preferencesStore';
import { useSessionStore } from '@/stores/session';

describe('OnboardingTourHost', () => {
  beforeEach(() => {
    usePreferencesStore.setState({
      showOnboardingTour: true,
      densityMode: 'cozy',
      activeTourId: null,
      currentTourStep: 0,
      isTourMovingRoutes: false,
      targetRoute: null,
    });
    useSessionStore.setState({
      authenticated: true,
      user: {
        id: 'u1',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
        warehouseIds: [],
        avatarUrl: null,
        tenantId: 't1',
      },
      lastRequestId: null,
      primarySession: null,
    });
  });

  it('prompts for the interactive tour and honors do not show again', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <OnboardingTourHost />
      </MemoryRouter>,
    );

    expect(await screen.findByTestId('onboarding-tour-prompt', {}, { timeout: 3_000 })).toBeInTheDocument();
    await user.click(screen.getByTestId('tour-dont-show'));
    expect(usePreferencesStore.getState().showOnboardingTour).toBe(false);
  });
});
