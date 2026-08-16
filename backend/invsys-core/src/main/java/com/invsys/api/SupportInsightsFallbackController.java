package com.invsys.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Present only when the optional chatbot module is disabled so the WMS copilot
 * can poll insights without a 404. Chat/execute stay unmapped (404) in that mode.
 */
@RestController
@RequestMapping("/api/v1/support")
@ConditionalOnProperty(name = "invsys.features.chatbot.enabled", havingValue = "false")
public class SupportInsightsFallbackController {

    @GetMapping("/insights")
    public Map<String, Object> insights() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("proactiveInsight", null);
        return body;
    }
}
