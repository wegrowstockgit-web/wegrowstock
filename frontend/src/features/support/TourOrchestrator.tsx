import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { driver, type Driver } from 'driver.js';
import 'driver.js/dist/driver.css';
import { installPreferencesTestHook, usePreferencesStore } from '@/stores/preferencesStore';
import { useIsAuthenticated } from '@/stores/session';
import { getWorkflowTour, routeMatches, type WorkflowTourStep } from './tourSteps';

/**
 * Router-aware driver.js orchestrator for cross-page workflow tours.
 * Pauses/destroys on route transition, resumes with drive(nextStep) when the target mounts.
 */
export function TourOrchestrator() {
  const location = useLocation();
  const navigate = useNavigate();
  const authenticated = useIsAuthenticated();
  const driverRef = useRef<Driver | null>(null);
  const resumeTimer = useRef<number | null>(null);

  useEffect(() => {
    installPreferencesTestHook();
  }, []);

  const activeTourId = usePreferencesStore((s) => s.activeTourId);
  const currentTourStep = usePreferencesStore((s) => s.currentTourStep);
  const isTourAwaitingRoute = usePreferencesStore((s) => s.isTourAwaitingRoute);
  const awaitingRoute = usePreferencesStore((s) => s.awaitingRoute);
  const setTourStep = usePreferencesStore((s) => s.setTourStep);
  const setAwaitingRoute = usePreferencesStore((s) => s.setAwaitingRoute);
  const clearTour = usePreferencesStore((s) => s.clearTour);

  // Persisted tour + logged-out /login must not navigate into protected routes.
  useEffect(() => {
    if (authenticated || !activeTourId) return;
    destroyDriver();
    clearTour();
  }, [authenticated, activeTourId, clearTour]);

  // When awaiting a route, resume as soon as React Router mounts the target.
  useEffect(() => {
    if (!authenticated || !activeTourId || !isTourAwaitingRoute || !awaitingRoute) return;
    if (!routeMatches(location.pathname, awaitingRoute)) return;

    setAwaitingRoute(null);
    if (resumeTimer.current) window.clearTimeout(resumeTimer.current);
    resumeTimer.current = window.setTimeout(() => {
      mountDriverAtStep(activeTourId, currentTourStep);
    }, 350);

    return () => {
      if (resumeTimer.current) window.clearTimeout(resumeTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mountDriver closes over latest helpers
  }, [authenticated, location.pathname, activeTourId, isTourAwaitingRoute, awaitingRoute, currentTourStep]);

  // Fresh tour start (not awaiting): drive when step route matches current location.
  useEffect(() => {
    if (!authenticated || !activeTourId || isTourAwaitingRoute) return;
    const steps = getWorkflowTour(activeTourId);
    const step = steps[currentTourStep];
    if (!step) {
      clearTour();
      return;
    }
    if (!routeMatches(location.pathname, step.route)) {
      // Navigate to the step's route first, then await resume.
      destroyDriver();
      setAwaitingRoute(step.route);
      navigate(step.route);
      return;
    }
    const timer = window.setTimeout(() => mountDriverAtStep(activeTourId, currentTourStep), 200);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authenticated, activeTourId, currentTourStep, isTourAwaitingRoute]);

  useEffect(() => () => destroyDriver(), []);

  function destroyDriver() {
    try {
      driverRef.current?.destroy();
    } catch {
      /* already destroyed */
    }
    driverRef.current = null;
  }

  function mountDriverAtStep(tourId: NonNullable<typeof activeTourId>, stepIndex: number) {
    const steps = getWorkflowTour(tourId);
    const step = steps[stepIndex];
    if (!step) {
      clearTour();
      return;
    }
    if (!document.querySelector(step.element)) {
      // Anchor not mounted yet — retry briefly.
      window.setTimeout(() => {
        if (document.querySelector(step.element)) {
          mountDriverAtStep(tourId, stepIndex);
        }
      }, 400);
      return;
    }

    destroyDriver();

    const d = driver({
      showProgress: true,
      animate: true,
      overlayOpacity: 0.45,
      stagePadding: 6,
      popoverClass: 'invsys-driver-popover',
      steps: [
        {
          element: step.element,
          popover: {
            title: step.title,
            description: step.description,
            onNextClick: (_el, _step, opts) => {
              advanceFrom(tourId, stepIndex, steps);
              opts.driver.destroy();
            },
            onCloseClick: (_el, _step, opts) => {
              opts.driver.destroy();
              clearTour();
            },
          },
        },
      ],
      onDestroyStarted: () => {
        d.destroy();
      },
    });
    driverRef.current = d;
    d.drive(0);
  }

  function advanceFrom(tourId: NonNullable<typeof activeTourId>, stepIndex: number, steps: WorkflowTourStep[]) {
    const nextIndex = stepIndex + 1;
    if (nextIndex >= steps.length) {
      clearTour();
      return;
    }
    const next = steps[nextIndex];
    setTourStep(nextIndex);
    destroyDriver();
    if (!routeMatches(location.pathname, next.route)) {
      setAwaitingRoute(next.route);
      navigate(next.route);
      return;
    }
    window.setTimeout(() => mountDriverAtStep(tourId, nextIndex), 200);
  }

  return null;
}
