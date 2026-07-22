package com.invsys.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SupportChatRequestImageAliasTest {

    @Test
    void base64ImageAliasFillsImageBase64WhenPrimaryMissing() {
        SupportChatController.ChatRequest request = new SupportChatController.ChatRequest(
                "Inspect this label",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of("PICKER"),
                null,
                "iVBORw0KGgo=",
                "image/png");

        assertThat(request.imageBase64()).isEqualTo("iVBORw0KGgo=");
        assertThat(request.base64Image()).isEqualTo("iVBORw0KGgo=");
        assertThat(request.imageMimeType()).isEqualTo("image/png");
    }

    @Test
    void primaryImageBase64WinsOverAlias() {
        SupportChatController.ChatRequest request = new SupportChatController.ChatRequest(
                "Inspect this label",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of("PICKER"),
                "primaryBytes",
                "aliasBytes",
                "image/jpeg");

        assertThat(request.imageBase64()).isEqualTo("primaryBytes");
    }
}
