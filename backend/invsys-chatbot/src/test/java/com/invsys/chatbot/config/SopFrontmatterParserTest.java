package com.invsys.chatbot.config;

import com.invsys.chatbot.config.SopFrontmatterParser.ParsedDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SopFrontmatterParserTest {

    @Test
    void parsesTitleSlugRolesRoutesAndBody() {
        String md = """
                ---
                title: "Inbound Receiving & Procurement SOP"
                slug: "sop-inbound-procurement"
                sourcePath: "docs/sops/01_inbound_and_procurement.md"
                audienceRoles: ["OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER"]
                routeHints: ["/purchase-orders", "/inbound/receive"]
                ---

                # Body

                Step one.
                """;
        ParsedDocument parsed = SopFrontmatterParser.parse(md);
        assertThat(parsed.frontmatter().title()).isEqualTo("Inbound Receiving & Procurement SOP");
        assertThat(parsed.frontmatter().slug()).isEqualTo("sop-inbound-procurement");
        assertThat(parsed.frontmatter().sourcePath()).isEqualTo("docs/sops/01_inbound_and_procurement.md");
        assertThat(parsed.frontmatter().audienceRoles()).containsExactly(
                "OWNER", "ADMIN", "WAREHOUSE_MANAGER", "PICKER");
        assertThat(parsed.frontmatter().routeHints()).containsExactly("/purchase-orders", "/inbound/receive");
        assertThat(parsed.body()).contains("# Body").contains("Step one.");
    }

    @Test
    void rejectsMissingFrontmatter() {
        assertThatThrownBy(() -> SopFrontmatterParser.parse("# just a title\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frontmatter");
    }
}
