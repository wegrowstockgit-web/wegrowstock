import { useEffect } from 'react';

/** Mobile list pages stay on the 25-row server page size so RAM stays bounded. */
export const MOBILE_PAGE_SIZE = 25;

export function useCapMobilePageSize(
  isMobile: boolean,
  size: number,
  setSize: (size: number) => void,
) {
  useEffect(() => {
    if (isMobile && size > MOBILE_PAGE_SIZE) {
      setSize(MOBILE_PAGE_SIZE);
    }
  }, [isMobile, size, setSize]);
}
