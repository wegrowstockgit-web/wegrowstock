import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { driver, type Driver } from 'driver.js';
import 'driver.js/dist/driver.css';
import {
  installPreferencesTestHook,
  registerTourDriverDestroy,
  usePreferencesStore,
} from '@/stores/preferencesStore';
import { useIsAuthenticated } from '@/stores/session';
import { getWorkflowTour, routeMatches, type WorkflowTourStep } from './tourSteps';

type DriverHookOpts = { driver: Driver };

/**
 * Router-aware driver.js orchestrator for cross-page workflow tours.
 * Uses preferencesStore.isTourMovingRoutes / targetRoute for inter-page hops.
 */
export function TourOrchestrator() {
  const location = useLocation();
  const navigate = useNavigate();
  const authenticated = useIsAuthenticated();
  const driverRef = useRef<Driver | null>(null);
  const resumeRaf = useRef<number | null>(null);
  /** Skip clearTour in onDestroyed while advancing or changing routes mid-tour. */
  const retainTourOnDestroyRef = useRef(false);

  useEffect(() => {
    installPreferencesTestHook();
  }, []);

  const activeTourId = usePreferencesStore((s) => s.activeTourId);
  const currentTourStep = usePreferencesStore((s) => s.currentTourStep);
  const isTourMovingRoutes = usePreferencesStore((s) => s.isTourMovingRoutes);
  const targetRoute = usePreferencesStore((s) => s.targetRoute);
  const setTourStep = usePreferencesStore((s) => s.setTourStep);
  const transitionToSubpage = usePreferencesStore((s) => s.transitionToSubpage);
  const clearRouteTransition = usePreferencesStore((s) => s.clearRouteTransition);
  const clearTour = usePreferencesStore((s) => s.clearTour);

  useEffect(() => {
    registerTourDriverDestroy(() => {
      retainTourOnDestroyRef.current = true;
      destroyDriver();
    });
    return () => registerTourDriverDestroy(null);
  }, []);

  useEffect(() => {
    if (authenticated || !activeTourId) return;
    retainTourOnDestroyRef.current = false;
    destroyDriver();
    clearTour();
  }, [authenticated, activeTourId, clearTour]);

  // Resume after inter-page transition once the destination pathname matches.
  // rAF-poll until the step's DOM anchor exists, then clear the gate and drive.
  useEffect(() => {
    if (!authenticated || !activeTourId || !isTourMovingRoutes || !targetRoute) return;
    if (!routeMatches(location.pathname, targetRoute)) return;

    const steps = getWorkflowTour(activeTourId);
    const step = steps[currentTourStep];
    if (!step) {
      clearTour();
      return;
    }

    let cancelled = false;
    let attempt = 0;
    const maxAttempts = 90; // ~1.5s at 60fps

    const resumeWhenReady = () => {
      if (cancelled) return;
      if (document.querySelector(step.element)) {
        clearRouteTransition();
        mountDriverAtStep(activeTourId, currentTourStep);
        return;
      }
      if (attempt++ > maxAttempts) {
        // Anchor never appeared — still clear the gate so the machine cannot stick.
        clearRouteTransition();
        return;
      }
      resumeRaf.current = requestAnimationFrame(resumeWhenReady);
    };

    if (resumeRaf.current != null) cancelAnimationFrame(resumeRaf.current);
    resumeRaf.current = requestAnimationFrame(resumeWhenReady);

    return () => {
      cancelled = true;
      if (resumeRaf.current != null) cancelAnimationFrame(resumeRaf.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    authenticated,
    location.pathname,
    activeTourId,
    isTourMovingRoutes,
    targetRoute,
    currentTourStep,
  ]);

  // Fresh tour start (not mid-transition): drive when step route matches.
  useEffect(() => {
    if (!authenticated || !activeTourId || isTourMovingRoutes) return;
    const steps = getWorkflowTour(activeTourId);
    const step = steps[currentTourStep];
    if (!step) {
      clearTour();
      return;
    }
    if (!routeMatches(location.pathname, step.route)) {
      retainTourOnDestroyRef.current = true;
      destroyDriver();
      transitionToSubpage(step.route, currentTourStep);
      navigate(step.route);
      return;
    }
    const timer = window.setTimeout(() => mountDriverAtStep(activeTourId, currentTourStep), 200);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authenticated, activeTourId, currentTourStep, isTourMovingRoutes]);

  useEffect(() => () => destroyDriver(), []);

  function destroyDriver() {
    try {
      driverRef.current?.destroy();
    } catch {
      /* already destroyed */
    }
    driverRef.current = null;
  }

  function syncProgressLabel(
    popover: { progress?: HTMLElement | null },
    stepIndex: number,
    total: number,
  ) {
    if (popover.progress) {
      popover.progress.textContent = `Step ${stepIndex + 1} of ${total}`;
    }
  }

  function waitForElement(selector: string, attempt = 0): void {
    if (document.querySelector(selector)) {
      const tourId = usePreferencesStore.getState().activeTourId;
      const step = usePreferencesStore.getState().currentTourStep;
      if (tourId != null) mountDriverAtStep(tourId, step);
      return;
    }
    if (attempt > 20) return;
    requestAnimationFrame(() => waitForElement(selector, attempt + 1));
  }

  function mountDriverAtStep(tourId: NonNullable<typeof activeTourId>, stepIndex: number) {
    const steps = getWorkflowTour(tourId);
    const step = steps[stepIndex];
    if (!step) {
      clearTour();
      return;
    }
    if (!document.querySelector(step.element)) {
      waitForElement(step.element);
      return;
    }

    retainTourOnDestroyRef.current = true;
    destroyDriver();
    retainTourOnDestroyRef.current = false;

    const isLast = stepIndex >= steps.length - 1;
    const total = steps.length;
    const doneLabel = step.doneBtnText ?? (isLast ? 'Finish Onboarding' : 'Next');

    const handleAdvance = (_el: Element | undefined, _step: unknown, opts: DriverHookOpts) => {
      retainTourOnDestroyRef.current = true;
      advanceFrom(tourId, stepIndex, steps);
      opts.driver.destroy();
    };

    const handleFinish = (_el: Element | undefined, _step: unknown, opts: DriverHookOpts) => {
      retainTourOnDestroyRef.current = false;
      opts.driver.destroy();
      clearTour();
    };

    const handleDismiss = (_el: Element | undefined, _step: unknown, opts: DriverHookOpts) => {
      retainTourOnDestroyRef.current = false;
      opts.driver.destroy();
      clearTour();
    };

    const d = driver({
      showProgress: true,
      progressText: 'Step {{current}} of {{total}}',
      animate: true,
      smoothScroll: true,
      overlayOpacity: 0.45,
      stagePadding: 10,
      doneBtnText: doneLabel,
      nextBtnText: 'Next',
      popoverClass: 'invsys-driver-popover',
      steps: [
        {
          element: step.element,
          popover: {
            title: step.title,
            description: step.description,
            showProgress: true,
            progressText: 'Step {{current}} of {{total}}',
            doneBtnText: doneLabel,
            onNextClick: isLast ? handleFinish : handleAdvance,
            onCloseClick: handleDismiss,
            onPopoverRender: (popover: { progress?: HTMLElement | null }) => {
              syncProgressLabel(popover, stepIndex, total);
            },
            onDoneClick: isLast ? handleFinish : handleAdvance,
          },
        },
      ],
      onPopoverRender: (popover) => {
        syncProgressLabel(popover, stepIndex, total);
      },
      onDestroyed: () => {
        driverRef.current = null;
        if (!retainTourOnDestroyRef.current) {
          clearTour();
        }
        retainTourOnDestroyRef.current = false;
      },
      onDestroyStarted: (_el, _step, opts) => {
        opts.driver.destroy();
      },
    });
    driverRef.current = d;
    d.drive(0);
  }

  function advanceFrom(
    tourId: NonNullable<typeof activeTourId>,
    stepIndex: number,
    steps: WorkflowTourStep[],
  ) {
    const step = steps[stepIndex];
    if (step?.transition) {
      const { route, nextStep, href } = step.transition;
      retainTourOnDestroyRef.current = true;
      transitionToSubpage(route, nextStep);
      navigate(href ?? route);
      return;
    }

    const nextIndex = stepIndex + 1;
    if (nextIndex >= steps.length) {
      retainTourOnDestroyRef.current = false;
      clearTour();
      return;
    }
    const next = steps[nextIndex];
    setTourStep(nextIndex);
    retainTourOnDestroyRef.current = true;
    destroyDriver();
    if (!routeMatches(location.pathname, next.route)) {
      transitionToSubpage(next.route, nextIndex);
      navigate(next.route);
      return;
    }
    window.setTimeout(() => mountDriverAtStep(tourId, nextIndex), 200);
  }

  return null;
}
