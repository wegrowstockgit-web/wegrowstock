import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { act, render, screen } from '@testing-library/react';
import { Sidebar } from './Sidebar';
import { freezeUser, useSessionStore } from '@/stores/session';
import { useRailStore } from '@/stores/rail';

function mockMatchMedia(isTabletOrBelow: boolean) {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: query.includes('max-width: 1023px') ? isTabletOrBelow : false,
    media: query,
    onchange: null,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => false,
  }));
}

function renderSidebar() {
  return render(
    <MemoryRouter>
      <Sidebar />
    </MemoryRouter>,
  );
}

describe('Sidebar', () => {
  beforeEach(() => {
    mockMatchMedia(false);
    useSessionStore.setState({
      authenticated: true,
      user: freezeUser({
        id: 'u1',
        email: 'owner@demo.test',
        displayName: 'Owner',
        roles: ['OWNER'],
        enabledModules: ['WMS', 'MESH_NETWORK', 'MRP'],
      }),
    });
    useRailStore.setState({ mobileOpen: false, pinned: false });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('uses a fully opaque raised surface with no glass blur', () => {
    renderSidebar();
    const panel = screen.getByTestId('icon-rail-panel');
    expect(panel.className).toContain('bg-surface-raised');
    expect(panel.className).not.toMatch(/bg-surface-raised\//);
    expect(panel.className).not.toContain('backdrop-blur');
    expect(panel.className).not.toContain('bg-opacity');
  });

  it('hides the persistent rail on mobile and shows a solid overlay when opened', () => {
    mockMatchMedia(true);
    renderSidebar();

    expect(screen.getByTestId('icon-rail')).toHaveAttribute('data-mobile-open', 'false');
    expect(screen.queryByTestId('mobile-nav-backdrop')).not.toBeInTheDocument();
    expect(screen.getByTestId('icon-rail').className).toContain('lg:hidden');

    act(() => {
      useRailStore.setState({ mobileOpen: true });
    });

    expect(screen.getByTestId('icon-rail')).toHaveAttribute('data-mobile-open', 'true');
    expect(screen.getByTestId('mobile-nav-backdrop').className).toContain('bg-black/50');
    expect(screen.getByTestId('icon-rail-panel').className).toContain('bg-surface-raised');
    expect(screen.getByTestId('icon-rail-panel').className).not.toContain('backdrop-blur');
  });
});
