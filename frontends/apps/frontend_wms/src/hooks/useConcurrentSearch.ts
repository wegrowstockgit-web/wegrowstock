import { useDeferredValue, useState, useTransition, type TransitionStartFunction } from 'react';

export interface ConcurrentSearch {
  /** Bound to the input — updates synchronously for tactile feedback. */
  inputValue: string;
  /**
   * Deferred / transitioned value for expensive work (query keys, client
   * filters, virtualized grid props). Lags behind {@link inputValue} under load.
   */
  deferredValue: string;
  /** True while a transition that updates the query value is pending. */
  isPending: boolean;
  setInputValue: (value: string) => void;
  /** Prefer for filter dropdowns / non-text controls that still trigger heavy grids. */
  startTransition: TransitionStartFunction;
}

/**
 * React 19 concurrent filter pattern: keep keystrokes on the urgent path while
 * grid query params and list filtering ride `useDeferredValue` + `startTransition`.
 */
export function useConcurrentSearch(initial = ''): ConcurrentSearch {
  const [inputValue, setInputValueState] = useState(initial);
  const [isPending, startTransition] = useTransition();
  const deferredValue = useDeferredValue(inputValue);

  const setInputValue = (value: string) => {
    setInputValueState(value);
  };

  return {
    inputValue,
    deferredValue,
    isPending,
    setInputValue,
    startTransition,
  };
}
