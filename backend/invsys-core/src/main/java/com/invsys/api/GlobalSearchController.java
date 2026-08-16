package com.invsys.api;

import com.invsys.api.dto.SearchResultDto;
import com.invsys.service.GlobalSearchService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
public class GlobalSearchController {

    private final GlobalSearchService globalSearchService;

    public GlobalSearchController(GlobalSearchService globalSearchService) {
        this.globalSearchService = globalSearchService;
    }

    @GetMapping("/global")
    @PreAuthorize("isAuthenticated()")
    public List<SearchResultDto> search(@RequestParam(name = "q", defaultValue = "") String q) {
        return globalSearchService.search(q);
    }
}
