/**
 * Bounded business feature packages (vertical slices).
 *
 * <p>Each submodule owns its domain model, repositories, application services, and HTTP API.
 * Prefer public DTO records and Spring {@code ApplicationEvent}/{@code OutboxService} for
 * cross-module collaboration. Core foundation ({@code com.invsys.core}) must not depend on
 * these packages.
 */
package com.invsys.modules;
