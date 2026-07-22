package com.invsys.api;

import com.invsys.chatbot.service.DocumentIngestionService;
import com.invsys.chatbot.service.DocumentIngestionService.IngestRequest;
import com.invsys.chatbot.service.DocumentIngestionService.IngestResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin SOP ingestion into pgvector ({@code support_knowledge_chunks}).
 */
@RestController
@RequestMapping("/api/v1/admin/chatbot")
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "true", matchIfMissing = true)
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
public class AdminChatbotIngestController {

    private final DocumentIngestionService documentIngestionService;

    public AdminChatbotIngestController(DocumentIngestionService documentIngestionService) {
        this.documentIngestionService = documentIngestionService;
    }

    public record IngestBody(
            String title,
            String slug,
            @NotBlank String content,
            String sourcePath,
            List<String> audienceRoles,
            List<String> routeHints
    ) {
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> ingest(@Valid @RequestBody IngestBody body) {
        IngestResult result = documentIngestionService.ingest(new IngestRequest(
                body.title(),
                body.slug(),
                body.content(),
                body.sourcePath(),
                body.audienceRoles(),
                body.routeHints()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("baseSlug", result.baseSlug());
        out.put("chunkCount", result.chunkCount());
        out.put("audienceRoles", result.audienceRoles());
        out.put("routeHints", result.routeHints());
        return out;
    }
}
