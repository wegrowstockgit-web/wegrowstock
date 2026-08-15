package com.invsys.admin.api;

import com.invsys.admin.service.AdminKnowledgeIngestService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/control-plane/knowledge")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ControlPlaneKnowledgeController {

    private final AdminKnowledgeIngestService adminKnowledgeIngestService;

    public ControlPlaneKnowledgeController(AdminKnowledgeIngestService adminKnowledgeIngestService) {
        this.adminKnowledgeIngestService = adminKnowledgeIngestService;
    }

    @PostMapping(value = "/ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminKnowledgeIngestService.KnowledgeDocumentView ingest(@RequestPart("file") MultipartFile file) {
        return adminKnowledgeIngestService.ingest(file);
    }

    @GetMapping
    public List<AdminKnowledgeIngestService.KnowledgeDocumentView> list() {
        return adminKnowledgeIngestService.listDocuments();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        adminKnowledgeIngestService.deleteDocument(id);
    }
}
