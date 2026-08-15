package com.invsys.admin.api;

import com.invsys.admin.service.AdminDeadLetterService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/control-plane/queues")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminQueueController {

    private final AdminDeadLetterService adminDeadLetterService;

    public AdminQueueController(AdminDeadLetterService adminDeadLetterService) {
        this.adminDeadLetterService = adminDeadLetterService;
    }

    @GetMapping("/dead-letters")
    public List<AdminDeadLetterService.DeadLetterGroup> listDeadLetters() {
        return adminDeadLetterService.listGrouped();
    }

    @GetMapping("/dead-letters/{id}")
    public AdminDeadLetterService.DeadLetterDetail get(@PathVariable UUID id) {
        return adminDeadLetterService.get(id);
    }

    @PostMapping("/dead-letters/{id}/retry")
    public AdminDeadLetterService.DeadLetterDetail retry(@PathVariable UUID id) {
        return adminDeadLetterService.retry(id);
    }
}
