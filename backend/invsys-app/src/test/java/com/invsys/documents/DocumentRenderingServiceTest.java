package com.invsys.documents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentRenderingServiceTest {

    @Test
    void ensureXhtmlSelfClosesMetaAndBr() {
        String xhtml = DocumentRenderingService.ensureXhtml(
                "<html><head><meta charset=\"utf-8\"><title>x</title></head><body>a<br>b</body></html>");
        assertThat(xhtml).contains("xmlns=\"http://www.w3.org/1999/xhtml\"");
        assertThat(xhtml).contains("<meta");
        assertThat(xhtml).contains("/>");
        assertThat(xhtml).contains("<br/>");
    }

    @Test
    void ensureXhtmlRejectsBlank() {
        assertThatThrownBy(() -> DocumentRenderingService.ensureXhtml("  "))
                .isInstanceOf(com.invsys.core.common.ApiException.class)
                .extracting(ex -> ((com.invsys.core.common.ApiException) ex).getCode())
                .isEqualTo("EMPTY_TEMPLATE");
    }
}
