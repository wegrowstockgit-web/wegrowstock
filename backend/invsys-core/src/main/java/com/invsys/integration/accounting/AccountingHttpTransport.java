package com.invsys.integration.accounting;

import java.util.Map;

/**
 * Thin HTTP port so QuickBooks / Xero adapters can be unit-tested without a live provider.
 */
public interface AccountingHttpTransport {

    record Response(int status, String body) {
        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    Response get(String url, Map<String, String> headers);

    Response post(String url, Map<String, String> headers, String jsonBody);
}
