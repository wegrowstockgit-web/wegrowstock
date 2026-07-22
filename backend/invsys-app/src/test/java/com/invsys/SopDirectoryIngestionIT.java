package com.invsys;

import com.invsys.chatbot.config.SopDirectoryIngestionRunner;
import com.invsys.support.SupportGraphRepository;
import com.invsys.support.SupportKnowledgeChunk;
import com.invsys.support.SupportKnowledgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies startup SOP directory ingestion populates chunks + graph DOC nodes.
 */
@TestPropertySource(properties = {
        "invsys.support.sop.directory-ingestion.enabled=true"
})
class SopDirectoryIngestionIT extends AbstractIntegrationTest {

    @Autowired SopDirectoryIngestionRunner sopDirectoryIngestionRunner;
    @Autowired SupportKnowledgeRepository knowledgeRepository;
    @Autowired SupportGraphRepository graphRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void packagedOrFilesystemSopsLandInChunksAndGraphNodes() throws Exception {
        // Prefer repo docs/sops when present; otherwise classpath resources packaged in chatbot jar.
        Path repoSops = Path.of("").toAbsolutePath();
        Path cursor = repoSops;
        Path found = null;
        for (int i = 0; i < 6; i++) {
            Path candidate = cursor.resolve("docs").resolve("sops");
            if (Files.isDirectory(candidate)) {
                found = candidate;
                break;
            }
            Path parent = cursor.getParent();
            if (parent == null) {
                break;
            }
            cursor = parent;
        }
        if (found != null) {
            System.setProperty("invsys.support.sop.directory", found.toAbsolutePath().toString());
        }
        try {
            // Force a re-run even if ApplicationRunner already executed at context start:
            // clear contentSha markers for SOP sources so ingest proceeds.
            jdbcTemplate.update("""
                    UPDATE support_knowledge_chunks
                       SET enriched_metadata = COALESCE(enriched_metadata, '{}'::jsonb) - 'contentSha',
                           metadata = (COALESCE(metadata::jsonb, '{}'::jsonb) - 'contentSha')::json
                     WHERE source_path LIKE 'docs/sops/%'
                        OR slug LIKE 'sop-%'
                    """);

            sopDirectoryIngestionRunner.ingestAll();

            List<SupportKnowledgeChunk> inbound = knowledgeRepository.findBySlugPrefix("sop-inbound-procurement");
            assertThat(inbound).isNotEmpty();
            assertThat(inbound.stream().anyMatch(c -> c.parentChunkId() != null
                    || (c.enrichedMetadataJson() != null && c.enrichedMetadataJson().contains("CHILD"))
                    || (c.enrichedMetadataJson() != null && c.enrichedMetadataJson().contains("PARENT"))))
                    .isTrue();

            Integer nodes = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM support_knowledge_nodes
                     WHERE slug LIKE 'doc-sop-%' OR chunk_slug LIKE 'sop-%-p0'
                    """, Integer.class);
            assertThat(nodes).isGreaterThan(0);
            assertThat(graphRepository.nodeCount()).isGreaterThan(0);
        } finally {
            System.clearProperty("invsys.support.sop.directory");
        }
    }
}
