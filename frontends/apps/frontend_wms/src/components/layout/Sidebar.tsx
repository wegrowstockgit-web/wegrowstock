import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  ChevronDown,
  ChevronRight,
  ChevronUp,
  Pin,
  UserCircle,
  type LucideIcon,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { useSessionStore, useEnabledModules } from '@/stores/session';
import { useRailStore } from '@/stores/rail';
import { useEntitlement } from '@/hooks/useEntitlement';
import { useCoarsePointer } from '@/hooks/useCoarsePointer';
import { useMediaQuery } from '@/hooks/useMediaQuery';
import {
  NAV_MATRIX,
  type NavCategoryConfig,
  type NavLeafConfig,
} from './navConfig';
import { BrandLogo } from './BrandLogo';
import '@/lib/router/appModules';
import { isNavPathEnabled } from '@/lib/router/moduleRegistry';

function leafVisible(
  item: NavLeafConfig,
  hasRole: (...roles: string[]) => boolean,
  hasModule: (module: string) => boolean,
  isPickerOnly: boolean,
  isViewerOnly: boolean,
  entitlements: readonly string[],
): boolean {
  if (!isNavPathEnabled(item.to, entitlements)) return false;
  if (item.roles && !hasRole(...item.roles)) return false;
  if (item.requiredModule && !hasModule(item.requiredModule)) return false;
  if (item.modules?.length && !item.modules.every((module) => hasModule(module))) return false;
  if (isPickerOnly && item.hideForPicker) return false;
  if (isViewerOnly && item.hideForViewer) return false;
  return true;
}

function pathInGroup(pathname: string, items: NavLeafConfig[]): boolean {
  return items.some(
    (item) => pathname === item.to || pathname.startsWith(`${item.to}/`),
  );
}

const railTransition =
  'transition-[width,padding,gap,transform] duration-[var(--rail-duration)] ease-[var(--rail-ease)]';

function LabelSpan({
  visible,
  children,
  className,
}: {
  visible: boolean;
  children: ReactNode;
  className?: string;
}) {
  return (
    <span
      className={cn(
        'truncate text-sm font-medium',
        'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
        visible
          ? 'max-w-[10rem] translate-x-0 opacity-100 delay-75'
          : 'pointer-events-none max-w-0 -translate-x-1 opacity-0 delay-0',
        className,
      )}
    >
      {children}
    </span>
  );
}

function SoloLink({
  to,
  label,
  icon: Icon,
  labelsVisible,
  coarsePointer,
  showOverlay,
  linkClass,
  testId,
}: {
  to: string;
  label: string;
  icon: LucideIcon;
  labelsVisible: boolean;
  coarsePointer: boolean;
  showOverlay: boolean;
  linkClass: (args: { isActive: boolean }) => string;
  testId?: string;
}) {
  return (
    <NavLink
      to={to}
      title={coarsePointer || showOverlay ? undefined : label}
      aria-label={label}
      className={linkClass}
      data-testid={testId}
    >
      <Icon className="h-4 w-4 shrink-0" />
      <LabelSpan visible={labelsVisible}>{label}</LabelSpan>
    </NavLink>
  );
}

/**
 * Expandable icon-rail driven by {@link NAV_MATRIX}.
 * Mobile (≤1023px): overlay drawer. Desktop: hover peek + pin.
 */
export function Sidebar() {
  const { t } = useTranslation();
  const location = useLocation();
  const hasRole = useSessionStore((s) => s.hasRole);
  const { hasModule } = useEntitlement();
  const isPickerOnly = useSessionStore((s) => s.isPickerOnly);
  const isViewerOnly = useSessionStore((s) => s.isViewerOnly);
  const entitlements = useEnabledModules();
  const pinned = useRailStore((s) => s.pinned);
  const setPinned = useRailStore((s) => s.setPinned);
  const mobileOpen = useRailStore((s) => s.mobileOpen);
  const setMobileOpen = useRailStore((s) => s.setMobileOpen);
  const canScrollUp = useRailStore((s) => s.canScrollUp);
  const canScrollDown = useRailStore((s) => s.canScrollDown);
  const setScrollFold = useRailStore((s) => s.setScrollFold);

  const coarsePointer = useCoarsePointer();
  const isTabletOrBelow = useMediaQuery('(max-width: 1023px)');

  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [peekLocked, setPeekLocked] = useState(false);
  /** Localized open/closed state per category id. */
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({});

  const navRef = useRef<HTMLElement>(null);
  const pickerOnly = isPickerOnly();
  const viewerOnly = isViewerOnly();

  const visibleSolos = useMemo(
    () =>
      NAV_MATRIX.solos.filter((item) =>
        leafVisible(item, hasRole, hasModule, pickerOnly, viewerOnly, entitlements),
      ),
    [hasRole, hasModule, pickerOnly, viewerOnly, entitlements],
  );

  const visibleCategories = useMemo(() => {
    return NAV_MATRIX.categories
      .map(
        (group): NavCategoryConfig => ({
          ...group,
          items: group.items.filter((item) =>
            leafVisible(item, hasRole, hasModule, pickerOnly, viewerOnly, entitlements),
          ),
        }),
      )
      .filter((group) => group.items.length > 0);
  }, [hasRole, hasModule, pickerOnly, viewerOnly, entitlements]);

  const activeGroupId = useMemo(() => {
    for (const group of visibleCategories) {
      if (pathInGroup(location.pathname, group.items)) return group.id;
    }
    return null;
  }, [location.pathname, visibleCategories]);

  useEffect(() => {
    if (!activeGroupId) return;
    setOpenGroups((prev) =>
      prev[activeGroupId] ? prev : { ...prev, [activeGroupId]: true },
    );
  }, [activeGroupId]);

  /** Copilot NAVIGATE chips expand the matching parent accordion before/while routing. */
  useEffect(() => {
    const onExpand = (event: Event) => {
      const path = (event as CustomEvent<{ path?: string }>).detail?.path;
      if (!path) return;
      for (const group of visibleCategories) {
        if (pathInGroup(path, group.items)) {
          setOpenGroups((prev) => ({ ...prev, [group.id]: true }));
          break;
        }
      }
    };
    window.addEventListener('invsys:expand-nav', onExpand);
    return () => window.removeEventListener('invsys:expand-nav', onExpand);
  }, [visibleCategories]);

  const updateScrollFold = useCallback(() => {
    const el = navRef.current;
    if (!el) {
      setScrollFold(false, false);
      return;
    }
    const { scrollTop, scrollHeight, clientHeight } = el;
    const overflow = scrollHeight > clientHeight + 1;
    setScrollFold(overflow && scrollTop > 4, overflow && scrollTop + clientHeight < scrollHeight - 4);
  }, [setScrollFold]);

  useEffect(() => {
    const el = navRef.current;
    if (!el) return;
    updateScrollFold();
    el.addEventListener('scroll', updateScrollFold, { passive: true });
    const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(updateScrollFold) : null;
    ro?.observe(el);
    return () => {
      el.removeEventListener('scroll', updateScrollFold);
      ro?.disconnect();
    };
  }, [updateScrollFold, mobileOpen, pinned, openGroups]);

  useEffect(() => {
    if (!isTabletOrBelow) setMobileOpen(false);
  }, [isTabletOrBelow, setMobileOpen]);

  useEffect(() => {
    if (isTabletOrBelow) setMobileOpen(false);
  }, [location.pathname, isTabletOrBelow, setMobileOpen]);

  const peeking = !isTabletOrBelow && !coarsePointer && !peekLocked && (hovered || focused);
  const expanded = isTabletOrBelow ? true : pinned || peeking;

  useEffect(() => {
    if (isTabletOrBelow) {
      document.documentElement.style.setProperty('--rail-width', '0px');
      return;
    }
    document.documentElement.style.setProperty(
      '--rail-width',
      expanded ? 'var(--rail-width-expanded)' : 'var(--rail-width-collapsed)',
    );
  }, [expanded, isTabletOrBelow]);

  const handlePinToggle = (event: React.MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
    event.stopPropagation();
    if (pinned) {
      setPinned(false);
      setPeekLocked(true);
      setHovered(false);
      setFocused(false);
      event.currentTarget.blur();
      return;
    }
    setPeekLocked(false);
    setPinned(true);
  };

  const toggleGroup = (groupId: string) => {
    setOpenGroups((prev) => ({ ...prev, [groupId]: !prev[groupId] }));
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'group relative flex min-h-11 shrink-0 items-center rounded-xl touch-target',
      'transition-[width,background-color,color,padding,transform] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
      'motion-safe:active:scale-[0.97]',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
      expanded ? 'w-full px-3 gap-3' : 'w-11 justify-center px-0',
      isActive
        ? 'bg-accent-muted text-accent'
        : 'text-text-muted hover:bg-surface-overlay hover:text-text',
    );

  const childLinkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'group relative flex min-h-10 shrink-0 items-center rounded-lg touch-target',
      'transition-[background-color,color,transform] duration-150 ease-out',
      'motion-safe:active:scale-[0.97]',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
      expanded || isTabletOrBelow ? 'w-full gap-2.5 px-3 pl-4' : 'w-11 justify-center px-0',
      isActive
        ? 'bg-accent-muted text-accent'
        : 'text-text-muted hover:bg-surface-overlay hover:text-text',
    );

  const showOverlay = isTabletOrBelow;
  const railVisible = !showOverlay || mobileOpen;
  const hasOverflowMask = canScrollUp || canScrollDown;
  const labelsVisible = expanded || showOverlay;

  return (
    <>
      {showOverlay && mobileOpen && (
        <button
          type="button"
          aria-label={t('nav.closeNavigation')}
          className="fixed inset-0 z-40 bg-slate-900/40 backdrop-blur-[1px] lg:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      <aside
        data-testid="icon-rail"
        data-expanded={expanded ? 'true' : 'false'}
        data-pinned={pinned ? 'true' : 'false'}
        data-mobile-open={mobileOpen ? 'true' : 'false'}
        onMouseEnter={() => {
          if (!peekLocked && !coarsePointer && !isTabletOrBelow) setHovered(true);
        }}
        onMouseLeave={() => {
          setHovered(false);
          setPeekLocked(false);
        }}
        onFocusCapture={() => {
          if (!peekLocked && !coarsePointer && !isTabletOrBelow) setFocused(true);
        }}
        onBlurCapture={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
            setFocused(false);
          }
        }}
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex flex-col items-stretch py-3 pl-3',
          railTransition,
          showOverlay
            ? cn(
                'w-[min(18rem,calc(100vw-2rem))] pointer-events-auto',
                railVisible ? 'translate-x-0' : '-translate-x-full pointer-events-none',
              )
            : cn(
                'pointer-events-none',
                expanded ? 'w-[var(--rail-width-expanded)]' : 'w-[var(--rail-width-collapsed)]',
                !pinned && expanded && 'z-50',
              ),
        )}
      >
        <div
          className={cn(
            'pointer-events-auto flex h-full flex-col gap-1 rounded-2xl',
            'border border-border/80 bg-surface-raised/95 py-3 shadow-elevated backdrop-blur-md',
            'supports-[backdrop-filter]:bg-surface-raised/80',
            railTransition,
            showOverlay || expanded ? 'w-full px-2' : 'w-14 items-center px-0',
            !showOverlay && expanded && 'w-[calc(var(--rail-width-expanded)-0.75rem)]',
          )}
        >
          <div
            className={cn(
              'relative mb-1 flex w-full items-center',
              railTransition,
              labelsVisible ? 'justify-between gap-2 px-1' : 'justify-center',
            )}
          >
            <div className={cn('flex items-center', labelsVisible ? 'shrink-0' : 'justify-center')}>
              <BrandLogo compact={!labelsVisible} size="sm" />
            </div>

            {!showOverlay && expanded && (
              <button
                type="button"
                aria-pressed={pinned}
                aria-label={pinned ? t('nav.unpinNavigation') : t('nav.pinNavigation')}
                title={coarsePointer ? undefined : pinned ? t('nav.unpinNavigation') : t('nav.pinNavigation')}
                onMouseDown={(event) => event.preventDefault()}
                onClick={handlePinToggle}
                className={cn(
                  'relative flex h-9 w-9 shrink-0 items-center justify-center rounded-lg',
                  'text-text-muted transition-[background-color,color,transform] duration-150 ease-out',
                  'hover:bg-surface-overlay hover:text-text motion-safe:active:scale-[0.96]',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
                  pinned && 'bg-accent-muted text-accent',
                )}
              >
                <Pin
                  className={cn(
                    'h-3.5 w-3.5 transition-transform duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                    pinned ? 'rotate-0 fill-current' : 'rotate-45',
                  )}
                />
              </button>
            )}
          </div>

          {!showOverlay && coarsePointer && !expanded && (
            <button
              type="button"
              aria-label={t('nav.expandNavigation')}
              onClick={() => {
                setPeekLocked(false);
                setPinned(true);
              }}
              className={cn(
                'flex h-11 w-11 shrink-0 items-center justify-center rounded-xl touch-target',
                'text-text-muted hover:bg-surface-overlay hover:text-text',
                'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
                'motion-safe:active:scale-[0.97]',
              )}
            >
              <Pin className="h-3.5 w-3.5 rotate-45" />
            </button>
          )}

          <div className="relative min-h-0 min-w-0 flex-1">
            {canScrollUp && (
              <div
                className="pointer-events-none absolute inset-x-0 top-0 z-10 flex justify-center pt-0.5"
                aria-hidden
              >
                <ChevronUp className="h-3.5 w-3.5 text-text-muted/80" />
              </div>
            )}
            {canScrollDown && (
              <div
                className="pointer-events-none absolute inset-x-0 bottom-0 z-10 flex justify-center pb-0.5"
                aria-hidden
              >
                <ChevronDown className="h-3.5 w-3.5 text-text-muted/80" />
              </div>
            )}

            <nav
              ref={navRef}
              className={cn(
                'flex h-full min-w-0 flex-col gap-0.5 overflow-y-auto overflow-x-hidden scrollbar-none',
                hasOverflowMask && 'rail-scroll-mask',
                railTransition,
                labelsVisible ? 'items-stretch px-0' : 'items-center px-1',
              )}
              aria-label="Primary"
            >
              {visibleSolos.map((solo) => (
                <SoloLink
                  key={solo.id}
                  to={solo.to}
                  label={t(solo.labelKey, solo.label)}
                  icon={solo.icon}
                  labelsVisible={labelsVisible}
                  coarsePointer={coarsePointer}
                  showOverlay={showOverlay}
                  linkClass={linkClass}
                  testId={solo.testId}
                />
              ))}

              {visibleCategories.map((group) => {
                const GroupIcon = group.icon;
                const isOpen = Boolean(openGroups[group.id]);
                const groupActive = activeGroupId === group.id;

                return (
                  <div
                    key={group.id}
                    className="flex min-w-0 flex-col gap-0.5"
                    data-testid={`nav-group-${group.id}`}
                    data-open={isOpen ? 'true' : 'false'}
                  >
                    <button
                      type="button"
                      aria-expanded={isOpen}
                      aria-controls={`nav-group-panel-${group.id}`}
                      data-testid={`nav-category-${group.id}`}
                      title={coarsePointer || showOverlay ? undefined : t(group.labelKey, group.category)}
                      aria-label={t(group.labelKey, group.category)}
                      onClick={() => {
                        if (!labelsVisible) {
                          setPeekLocked(false);
                          setPinned(true);
                          setOpenGroups((prev) => ({ ...prev, [group.id]: true }));
                          return;
                        }
                        toggleGroup(group.id);
                      }}
                      className={cn(
                        'group relative flex min-h-11 shrink-0 items-center rounded-xl touch-target',
                        'transition-[width,background-color,color,padding,transform] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                        'motion-safe:active:scale-[0.97]',
                        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/40',
                        labelsVisible ? 'w-full px-3 gap-3' : 'w-11 justify-center px-0',
                        groupActive
                          ? 'bg-surface-overlay text-text'
                          : 'text-text-muted hover:bg-surface-overlay hover:text-text',
                      )}
                    >
                      <GroupIcon className="h-4 w-4 shrink-0" />
                      <span
                        className={cn(
                          'min-w-0 flex-1 truncate text-left text-sm font-semibold',
                          'transition-[opacity,transform,max-width] duration-[var(--rail-duration)] ease-[var(--rail-ease)]',
                          labelsVisible
                            ? 'max-w-[10rem] translate-x-0 opacity-100 delay-75'
                            : 'pointer-events-none max-w-0 -translate-x-1 opacity-0 delay-0',
                        )}
                      >
                        {t(group.labelKey, group.category)}
                      </span>
                      {labelsVisible && (
                        <ChevronRight
                          className={cn(
                            'h-3.5 w-3.5 shrink-0 text-text-muted transition-transform duration-150',
                            isOpen && 'rotate-90',
                          )}
                          aria-hidden
                        />
                      )}
                    </button>

                    {isOpen && (
                      <div
                        id={`nav-group-panel-${group.id}`}
                        role="group"
                        aria-label={t(group.labelKey, group.category)}
                        className={cn(
                          'flex flex-col gap-0.5',
                          labelsVisible && 'ml-5 border-l border-border/70 pl-1',
                        )}
                      >
                        {group.items.map(({ to, label, labelKey, icon: Icon, tourAnchor, testId }) => {
                          const translated = t(labelKey, label);
                          return (
                          <NavLink
                            key={to}
                            to={to}
                            title={coarsePointer || showOverlay ? undefined : translated}
                            aria-label={translated}
                            className={childLinkClass}
                            data-tour={tourAnchor}
                            data-testid={testId}
                            onClick={() => {
                              if (showOverlay) setMobileOpen(false);
                            }}
                          >
                            <Icon className="h-3.5 w-3.5 shrink-0" />
                            <LabelSpan visible={labelsVisible} className="max-w-[9.5rem]">
                              {translated}
                            </LabelSpan>
                          </NavLink>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}

            </nav>
          </div>

          <SoloLink
            to="/settings/profile"
            label={t('nav.profile', t('nav.Profile'))}
            icon={UserCircle}
            labelsVisible={labelsVisible}
            coarsePointer={coarsePointer}
            showOverlay={showOverlay}
            linkClass={linkClass}
            testId="nav-personal-profile"
          />
        </div>
      </aside>
    </>
  );
}
