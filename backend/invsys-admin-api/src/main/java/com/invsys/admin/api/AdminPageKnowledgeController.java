package com.invsys.admin.api;

import com.invsys.admin.audit.PlatformAudit;
import com.invsys.api.dto.PageKnowledgeDto;
import com.invsys.api.dto.PageKnowledgeWriteRequest;
import com.invsys.service.PageKnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/admin/page-knowledge", "/api/v1/control-plane/page-knowledge"})
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminPageKnowledgeController {

    private final PageKnowledgeService pageKnowledgeService;

    public AdminPageKnowledgeController(PageKnowledgeService pageKnowledgeService) {
        this.pageKnowledgeService = pageKnowledgeService;
    }

    @GetMapping
    public List<PageKnowledgeDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category) {
        return pageKnowledgeService.search(search, category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PlatformAudit(action = "PAGE_KNOWLEDGE_CREATE")
    public PageKnowledgeDto create(@Valid @RequestBody PageKnowledgeWriteRequest request, Authentication auth) {
        return pageKnowledgeService.create(request, actor(auth));
    }

    @PutMapping("/{id}")
    @PlatformAudit(action = "PAGE_KNOWLEDGE_UPDATE")
    public PageKnowledgeDto update(
            @PathVariable UUID id,
            @Valid @RequestBody PageKnowledgeWriteRequest request,
            Authentication auth) {
        return pageKnowledgeService.update(id, request, actor(auth));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PlatformAudit(action = "PAGE_KNOWLEDGE_DELETE")
    public void delete(@PathVariable UUID id) {
        pageKnowledgeService.delete(id);
    }

    private static String actor(Authentication auth) {
        return auth == null || auth.getName() == null || auth.getName().isBlank()
                ? "super-admin"
                : auth.getName();
    }
}
