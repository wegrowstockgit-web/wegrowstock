package com.invsys.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "invsys.jwt")
public class JwtProperties {
    private int accessTokenMinutes = 15;
    private int terminalSwitchTokenMinutes = 5;
    private int refreshTokenDays = 7;
    private String privateKeyPem;
    private String publicKeyPem;
    private String privateKeyFile;
    private String publicKeyFile;
    /** When true, missing PEMs generate an ephemeral RSA keypair (tests / explicit local only). */
    private boolean allowEphemeral = false;

    public int getAccessTokenMinutes() {
        return accessTokenMinutes;
    }

    public void setAccessTokenMinutes(int accessTokenMinutes) {
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public int getTerminalSwitchTokenMinutes() {
        return terminalSwitchTokenMinutes;
    }

    public void setTerminalSwitchTokenMinutes(int terminalSwitchTokenMinutes) {
        this.terminalSwitchTokenMinutes = terminalSwitchTokenMinutes;
    }

    public int getRefreshTokenDays() {
        return refreshTokenDays;
    }

    public void setRefreshTokenDays(int refreshTokenDays) {
        this.refreshTokenDays = refreshTokenDays;
    }

    public String getPrivateKeyPem() {
        return privateKeyPem;
    }

    public void setPrivateKeyPem(String privateKeyPem) {
        this.privateKeyPem = privateKeyPem;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    public void setPrivateKeyFile(String privateKeyFile) {
        this.privateKeyFile = privateKeyFile;
    }

    public String getPublicKeyFile() {
        return publicKeyFile;
    }

    public void setPublicKeyFile(String publicKeyFile) {
        this.publicKeyFile = publicKeyFile;
    }

    public boolean isAllowEphemeral() {
        return allowEphemeral;
    }

    public void setAllowEphemeral(boolean allowEphemeral) {
        this.allowEphemeral = allowEphemeral;
    }
}
