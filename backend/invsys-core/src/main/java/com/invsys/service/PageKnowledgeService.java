package com.invsys.service;

import com.invsys.api.dto.MistakeFixDto;
import com.invsys.api.dto.PageKnowledgeDto;
import com.invsys.api.dto.PageKnowledgeWriteRequest;
import com.invsys.core.common.ApiException;
import com.invsys.domain.PageKnowledgeConfig;
import com.invsys.repository.PageKnowledgeConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PageKnowledgeService {

    private final PageKnowledgeConfigRepository repository;

    public PageKnowledgeService(PageKnowledgeConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PageKnowledgeDto> listAll() {
        return repository.findAllByOrderByRoutePatternAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<PageKnowledgeDto> search(String query, String category) {
        String q = query == null ? "" : query.strip();
        String cat = category == null ? "" : category.strip();
        return repository.search(q, cat).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PageKnowledgeDto findByRoute(String route) {
        List<PageKnowledgeConfig> catalog = repository.findAllByOrderByRoutePatternAsc();
        return PageKnowledgeRouteMatcher.match(route, catalog)
                .map(this::toDto)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "No page knowledge for route " + route));
    }

    @Transactional
    public PageKnowledgeDto create(PageKnowledgeWriteRequest request, String actor) {
        String pattern = normalizePattern(request.routePattern());
        if (repository.findByRoutePattern(pattern).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Route pattern already exists");
        }
        PageKnowledgeConfig entity = new PageKnowledgeConfig();
        apply(entity, request, pattern, actor);
        return toDto(repository.save(entity));
    }

    @Transactional
    public PageKnowledgeDto update(UUID id, PageKnowledgeWriteRequest request, String actor) {
        PageKnowledgeConfig entity = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Page knowledge not found"));
        String pattern = normalizePattern(request.routePattern());
        repository.findByRoutePattern(pattern)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "Route pattern already exists");
                });
        apply(entity, request, pattern, actor);
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Page knowledge not found");
        }
        repository.deleteById(id);
    }

    private void apply(PageKnowledgeConfig entity, PageKnowledgeWriteRequest request, String pattern, String actor) {
        entity.setRoutePattern(pattern);
        entity.setCategory(request.category().strip());
        entity.setTitle(request.title().strip());
        entity.setSummary(request.summary().strip());
        entity.setRolePrivileges(request.rolePrivileges().strip());
        entity.setKeyActions(request.keyActions() == null ? List.of() : request.keyActions());
        entity.setCommonMistakes(request.commonMistakes() == null
                ? List.of()
                : request.commonMistakes().stream().map(this::toEntityMistake).toList());
        entity.setProTip(request.proTip() == null || request.proTip().isBlank() ? null : request.proTip().strip());
        entity.setUpdatedBy(actor);
    }

    private PageKnowledgeConfig.MistakeFix toEntityMistake(MistakeFixDto dto) {
        return new PageKnowledgeConfig.MistakeFix(dto.mistake().strip(), dto.solution().strip(), dto.requiredRole().strip());
    }

    private PageKnowledgeDto toDto(PageKnowledgeConfig entity) {
        List<MistakeFixDto> mistakes = entity.getCommonMistakes() == null
                ? List.of()
                : entity.getCommonMistakes().stream()
                        .map(item -> new MistakeFixDto(item.getMistake(), item.getSolution(), item.getRequiredRole()))
                        .toList();
        return new PageKnowledgeDto(
                entity.getId(),
                entity.getRoutePattern(),
                entity.getCategory(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getRolePrivileges(),
                entity.getKeyActions() == null ? List.of() : List.copyOf(entity.getKeyActions()),
                mistakes,
                entity.getProTip(),
                entity.getUpdatedAt());
    }

    static String normalizePattern(String raw) {
        return PageKnowledgeRouteMatcher.normalize(raw).fullKey();
    }
}
