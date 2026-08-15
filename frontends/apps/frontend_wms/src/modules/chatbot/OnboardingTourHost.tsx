import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { usePreferencesStore, type TourId } from '@/stores/preferencesStore';
import { useIsAuthenticated } from '@/stores/session';
import { isFloorRoute } from './tourSteps';

/**
 * After login hydration: if showOnboardingTour, prompt then start a workflow tour
 * (single-page or receiving→allocation) via the persisted tour state machine.
 */
export function OnboardingTourHost() {
  const authenticated = useIsAuthenticated();
  const location = useLocation();
  const showOnboardingTour = usePreferencesStore((s) => s.showOnboardingTour);
  const activeTourId = usePreferencesStore((s) => s.activeTourId);
  const setShowOnboardingTour = usePreferencesStore((s) => s.setShowOnboardingTour);
  const startTour = usePreferencesStore((s) => s.startTour);
  const [promptOpen, setPromptOpen] = useState(false);
  /** "Not now" must not re-open on every route change during the same session. */
  const declinedThisSession = useRef(false);

  useEffect(() => {
    if (!authenticated || !showOnboardingTour || activeTourId || declinedThisSession.current) {
      setPromptOpen(false);
      return;
    }
    const id = window.setTimeout(() => setPromptOpen(true), 600);
    return () => window.clearTimeout(id);
  }, [authenticated, showOnboardingTour, activeTourId, location.pathname]);

  const begin = (tourId: TourId) => {
    declinedThisSession.current = true;
    setPromptOpen(false);
    startTour(tourId, 0);
  };

  const declineOnce = () => {
    declinedThisSession.current = true;
    setPromptOpen(false);
  };

  const neverAgain = () => {
    setShowOnboardingTour(false);
    setPromptOpen(false);
  };

  const defaultTour: TourId = isFloorRoute(location.pathname)
    ? 'floor'
    : 'receiving-to-allocation';

  return (
    <Modal
      open={promptOpen}
      onClose={declineOnce}
      title="Interactive system tour"
      description="Would you like the interactive system tour?"
    >
      <div className="flex flex-col gap-3" data-testid="onboarding-tour-prompt">
        <p className="text-sm text-text-muted">
          Steps connect physical actions to digital effects (receive unlocks B2B inventory; allocate
          reserves lots for waves). Multi-page tours pause and resume across routes.
        </p>
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="ghost" onClick={neverAgain} data-testid="tour-dont-show">
            Don&apos;t show again
          </Button>
          <Button type="button" variant="secondary" onClick={declineOnce} data-testid="tour-skip">
            Not now
          </Button>
          {!isFloorRoute(location.pathname) && (
            <Button
              type="button"
              variant="secondary"
              onClick={() => begin('office')}
              data-testid="tour-start-office"
            >
              Office only
            </Button>
          )}
          <Button type="button" onClick={() => begin(defaultTour)} data-testid="tour-start">
            Start tour
          </Button>
        </div>
      </div>
    </Modal>
  );
}
