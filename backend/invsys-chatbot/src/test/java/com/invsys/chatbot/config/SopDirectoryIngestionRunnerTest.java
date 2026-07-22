package com.invsys.chatbot.config;

import com.invsys.chatbot.service.DocumentIngestionService;
import com.invsys.chatbot.service.DocumentIngestionService.IngestRequest;
import com.invsys.chatbot.service.DocumentIngestionService.IngestResult;
import com.invsys.support.SupportGraphRepository;
import com.invsys.support.SupportKnowledgeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SopDirectoryIngestionRunnerTest {

    @Mock DocumentIngestionService documentIngestionService;
    @Mock SupportKnowledgeRepository knowledgeRepository;
    @Mock SupportGraphRepository graphRepository;

    @TempDir Path tempDir;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() throws InterruptedException {
        System.clearProperty("invsys.support.sop.directory");
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void ingestsMarkdownFromConfiguredDirectoryAndUpsertsGraphNode() throws Exception {
        Path sopDir = tempDir.resolve("docs").resolve("sops");
        Files.createDirectories(sopDir);
        Files.writeString(sopDir.resolve("01_demo.md"), """
                ---
                title: "Demo SOP"
                slug: "sop-demo"
                sourcePath: "docs/sops/01_demo.md"
                audienceRoles: ["PICKER", "WAREHOUSE_MANAGER"]
                routeHints: ["/fulfillment"]
                ---

                ## Pick

                Tap **Release to floor**.
                """);

        System.setProperty("invsys.support.sop.directory", sopDir.toAbsolutePath().toString());
        when(knowledgeRepository.hasSourceContentSha(anyString(), anyString())).thenReturn(false);
        when(documentIngestionService.ingest(any())).thenReturn(new IngestResult(
                "sop-demo", 2, List.of("PICKER"), List.of("/fulfillment"), "summary"));

        SopDirectoryIngestionRunner runner = new SopDirectoryIngestionRunner(
                documentIngestionService, knowledgeRepository, graphRepository, executor);
        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<IngestRequest> captor = ArgumentCaptor.forClass(IngestRequest.class);
        verify(documentIngestionService, timeout(5_000)).ingest(captor.capture());
        IngestRequest req = captor.getValue();
        assertThat(req.slug()).isEqualTo("sop-demo");
        assertThat(req.title()).isEqualTo("Demo SOP");
        assertThat(req.content()).contains("Release to floor");
        assertThat(req.audienceRoles()).contains("PICKER");
        assertThat(req.contentSha()).isNotBlank();
        assertThat(req.llmEnrichment()).isFalse();

        verify(graphRepository, timeout(5_000))
                .upsertNode(eq("doc-sop-demo"), eq("DOC"), eq("Demo SOP"), eq("sop-demo-p0"));
    }

    @Test
    void skipsWhenContentShaAlreadyPresent() throws Exception {
        Path sopDir = tempDir.resolve("docs").resolve("sops");
        Files.createDirectories(sopDir);
        Files.writeString(sopDir.resolve("01_demo.md"), """
                ---
                title: "Demo SOP"
                slug: "sop-demo"
                sourcePath: "docs/sops/01_demo.md"
                audienceRoles: ["PICKER"]
                routeHints: ["/fulfillment"]
                ---

                Body
                """);

        System.setProperty("invsys.support.sop.directory", sopDir.toAbsolutePath().toString());
        when(knowledgeRepository.hasSourceContentSha(anyString(), anyString())).thenReturn(true);

        SopDirectoryIngestionRunner runner = new SopDirectoryIngestionRunner(
                documentIngestionService, knowledgeRepository, graphRepository, executor);
        runner.run(new DefaultApplicationArguments());

        // Allow async task to finish, then assert ingest was never called.
        Thread.sleep(300);
        verify(documentIngestionService, never()).ingest(any());
        verify(graphRepository, never()).upsertNode(anyString(), anyString(), anyString(), anyString());
    }
}
