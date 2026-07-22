package com.invsys;

import com.invsys.core.security.AuthService;
import com.invsys.core.security.dto.LoginRequest;
import com.invsys.core.security.dto.SignupRequest;
import com.invsys.core.security.dto.TokenResponse;
import com.invsys.service.AuditArchiveStorageService;
import com.invsys.core.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditArchiveDownloadHttpTest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;
    @Autowired AuditArchiveStorageService archiveStorageService;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ownerDownloadsDecompressedNdjsonAttachment() throws Exception {
        String slug = "adl-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Archive DL Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        byte[] gzipped = gzipJsonl("{\"action\":\"ARCHIVE_TEST\",\"n\":1}\n");
        archiveStorageService.uploadArchive(
                owner.tenantId(), "fixture-" + UUID.randomUUID() + ".jsonl.gz", gzipped);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        MvcResult result = mockMvc.perform(get("/api/v1/office/audit/archives/download")
                        .param("startDate", today.minusMonths(1).toString())
                        .param("endDate", today.toString())
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit_archive.jsonl\""))
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/x-ndjson")))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"action\":\"ARCHIVE_TEST\"");
    }

    @Test
    void pickerForbiddenFromArchiveDownload() throws Exception {
        String slug = "adp-" + UUID.randomUUID().toString().substring(0, 8);
        TokenResponse owner = authService.signup(new SignupRequest(
                "Archive Deny Co", slug, "owner@" + slug + ".test", "password123", "Owner"));

        MvcResult invite = mockMvc.perform(post("/api/v1/users/invitations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"picker@%s.test","role":"PICKER"}
                                """.formatted(slug)))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(invite.getResponse().getContentAsString())
                .get("token").asString();
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayName":"Picker","password":"password123"}
                                """.formatted(token)))
                .andExpect(status().isOk());

        TokenResponse picker = authService.login(new LoginRequest(
                "picker@" + slug + ".test", "password123"));

        mockMvc.perform(get("/api/v1/office/audit/archives/download")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-01-31")
                        .header("Authorization", "Bearer " + picker.accessToken()))
                .andExpect(status().isForbidden());
    }

    private static byte[] gzipJsonl(String jsonl) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bos)) {
            gzip.write(jsonl.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }
}
