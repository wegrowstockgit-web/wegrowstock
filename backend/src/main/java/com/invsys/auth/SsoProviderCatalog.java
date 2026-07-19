package com.invsys.auth;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Well-known OIDC IdP presets for Google Workspace, Microsoft Entra ID, and Okta.
 */
@Component
public class SsoProviderCatalog {

    public List<ProviderPreset> presets() {
        return List.of(
                new ProviderPreset(
                        "GOOGLE",
                        "Google Workspace",
                        "OIDC",
                        "https://accounts.google.com",
                        "Login with Google"),
                new ProviderPreset(
                        "ENTRA",
                        "Microsoft Entra ID",
                        "OIDC",
                        "https://login.microsoftonline.com/{tenant}/v2.0",
                        "Login with Microsoft"),
                new ProviderPreset(
                        "OKTA",
                        "Okta",
                        "OIDC",
                        "https://{yourOktaDomain}/oauth2/default",
                        "Login with Okta"));
    }

    public String inferProvider(String issuerUrl) {
        if (issuerUrl == null || issuerUrl.isBlank()) {
            return "CUSTOM";
        }
        String lower = issuerUrl.toLowerCase(Locale.ROOT);
        if (lower.contains("accounts.google.com") || lower.contains("google.com/o/oauth2")) {
            return "GOOGLE";
        }
        if (lower.contains("login.microsoftonline.com") || lower.contains("sts.windows.net")) {
            return "ENTRA";
        }
        if (lower.contains("okta.com") || lower.contains("oktapreview.com") || lower.contains("okta-emea.com")) {
            return "OKTA";
        }
        return "CUSTOM";
    }

    public record ProviderPreset(
            String id,
            String displayName,
            String protocol,
            String issuerTemplate,
            String loginButtonLabel
    ) {
    }
}
