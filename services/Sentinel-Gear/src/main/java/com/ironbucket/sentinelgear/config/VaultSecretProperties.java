package com.ironbucket.sentinelgear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ironbucket.security.vault")
public class VaultSecretProperties {

    private boolean enabled = false;
    private String uri = "https://127.0.0.1:8200";
    private String token;
    private String kvPath = "secret/data/ironbucket/sentinel-gear";
    private String secretKey = "presignedSecret";
    private Duration timeout = Duration.ofSeconds(2);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getKvPath() {
        return kvPath;
    }

    public void setKvPath(String kvPath) {
        this.kvPath = kvPath;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}