import type { ReactNode } from 'react';
import type { RouteObject } from 'react-router-dom';

/**
 * Pluggable app module contract. Disabled modules omit routes + nav paths so
 * App/Sidebar never reference missing feature code at runtime.
 */
export type NavItem = {
  to: string;
  label: string;
  moduleId: string;
};

export type AppModule = {
  id: string;
  enabled: boolean;
  /** Nested office routes under AppShell (`path` relative, e.g. `products`). */
  officeRoutes: RouteObject[];
  /** Nested floor routes under WarehouseFloorShell. */
  floorRoutes?: RouteObject[];
  /** Absolute paths (with leading `/`) owned by this module for nav filtering. */
  navItems?: NavItem[];
  /** Extra absolute route elements rendered outside shells (rare). */
  standaloneRoutes?: RouteObject[];
};

function envEnabled(flag: string | undefined): boolean {
  return flag !== 'false';
}

export function isModuleBuildEnabled(envKey: keyof ImportMetaEnv | string): boolean {
  const value = (import.meta.env as Record<string, string | undefined>)[envKey as string];
  return envEnabled(value);
}

/** Active modules — populated by {@link registerAppModules}. */
let registeredModules: AppModule[] = [];

export function registerAppModules(modules: AppModule[]): void {
  registeredModules = modules;
}

export function getRegisteredModules(): AppModule[] {
  return registeredModules;
}

export function getEnabledModules(): AppModule[] {
  return registeredModules.filter((m) => m.enabled);
}

export function getEnabledOfficeRoutes(): RouteObject[] {
  return getEnabledModules().flatMap((m) => m.officeRoutes);
}

export function getEnabledFloorRoutes(): RouteObject[] {
  return getEnabledModules().flatMap((m) => m.floorRoutes ?? []);
}

export function getEnabledStandaloneRoutes(): RouteObject[] {
  return getEnabledModules().flatMap((m) => m.standaloneRoutes ?? []);
}

/** Paths that belong to a disabled module — Sidebar hides matching leaves. */
export function getDisabledNavPaths(): Set<string> {
  const disabled = new Set<string>();
  for (const mod of registeredModules) {
    if (mod.enabled) continue;
    for (const item of mod.navItems ?? []) {
      disabled.add(item.to);
    }
  }
  return disabled;
}

export function isNavPathEnabled(to: string): boolean {
  return !getDisabledNavPaths().has(to);
}

/** Helper for feature module authors. */
export function defineModule(module: AppModule): AppModule {
  return module;
}

export type ModuleRouteElement = ReactNode;
