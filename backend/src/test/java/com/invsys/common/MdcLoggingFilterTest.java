package com.invsys.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MdcLoggingFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void putsRequestIdFromHeaderAndClearsAfter() throws Exception {
        MdcLoggingFilter filter = new MdcLoggingFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(MdcLoggingFilter.HEADER)).thenReturn("req-abc-123");

        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(MdcSupport.REQUEST_ID));

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEqualTo("req-abc-123");
        assertThat(MDC.get(MdcSupport.REQUEST_ID)).isNull();
        verify(response).setHeader(MdcLoggingFilter.HEADER, "req-abc-123");
    }

    @Test
    void generatesUuidWhenHeaderMissing() throws Exception {
        MdcLoggingFilter filter = new MdcLoggingFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(MdcLoggingFilter.HEADER)).thenReturn(null);

        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(MdcSupport.REQUEST_ID));

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isNotBlank();
        assertThat(MDC.getCopyOfContextMap()).isNull();
    }
}
