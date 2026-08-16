package com.invsys.service;

/**
 * Fired when Super Admin mutates a commercial tier bundle so WMS nodes can
 * drop their {@code TierDefinitionsCache} entries.
 */
public record PlatformTierDefinitionUpdatedEvent(String tierCode) {
}
