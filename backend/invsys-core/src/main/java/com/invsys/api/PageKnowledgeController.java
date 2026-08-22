package com.invsys.api;

import com.invsys.api.dto.PageKnowledgeDto;
import com.invsys.service.PageKnowledgeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/page-knowledge")
@PreAuthorize("isAuthenticated()")
public class PageKnowledgeController {

    private final PageKnowledgeService pageKnowledgeService;

    public PageKnowledgeController(PageKnowledgeService pageKnowledgeService) {
        this.pageKnowledgeService = pageKnowledgeService;
    }

    @GetMapping("/all")
    public List<PageKnowledgeDto> all() {
        return pageKnowledgeService.listAll();
    }

    @GetMapping
    public PageKnowledgeDto byRoute(@RequestParam String route) {
        return pageKnowledgeService.findByRoute(route);
    }
}
