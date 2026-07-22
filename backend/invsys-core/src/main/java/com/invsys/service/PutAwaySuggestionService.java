package com.invsys.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Backward-compatible facade over {@link PutawayStrategyService}.
 */
@Service
public class PutAwaySuggestionService {

    private final PutawayStrategyService putawayStrategyService;

    public PutAwaySuggestionService(PutawayStrategyService putawayStrategyService) {
        this.putawayStrategyService = putawayStrategyService;
    }

    @Transactional(readOnly = true)
    public PutAwaySuggestion suggest(UUID variantId) {
        PutawayStrategyService.PutawayDirective directive = putawayStrategyService.suggest(variantId);
        return new PutAwaySuggestion(
                directive.locationId(),
                directive.path(),
                directive.code(),
                directive.strategy());
    }

    public record PutAwaySuggestion(UUID locationId, String path, String code, String strategy) {
    }
}
