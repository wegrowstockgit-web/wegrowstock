package com.invsys.dns;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JndiDnsTxtLookupTest {

    @Test
    void unwrapTxtStripsQuotesAndConcatChunks() {
        assertThat(JndiDnsTxtLookup.unwrapTxt("\"growstock-verification=abc\"")).isEqualTo("growstock-verification=abc");
        assertThat(JndiDnsTxtLookup.unwrapTxt("\"growstock-\" \"verification=abc\""))
                .isEqualTo("growstock-verification=abc");
    }
}
