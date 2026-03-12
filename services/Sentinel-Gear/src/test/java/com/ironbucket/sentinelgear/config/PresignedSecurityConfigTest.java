package com.ironbucket.sentinelgear.config;

import com.ironbucket.sentinelgear.security.TamperReplayDetector;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PresignedSecurityConfigTest {

    private final PresignedSecurityConfig config = new PresignedSecurityConfig();

    @Test
    void createsDetectorWhenEnabledAndSecretProvided() {
        PresignedSecurityProperties properties = new PresignedSecurityProperties();
        properties.setEnabled(true);
        properties.setSecret("strong-secret-value");
        properties.setNonceTtl(Duration.ofMinutes(5));

        TamperReplayDetector detector = config.tamperReplayDetector(properties);

        assertNotNull(detector);
    }

    @Test
    void failsFastWhenEnabledWithoutSecret() {
        PresignedSecurityProperties properties = new PresignedSecurityProperties();
        properties.setEnabled(true);
        properties.setSecret(" ");
        properties.setNonceTtl(Duration.ofMinutes(5));

        assertThrows(IllegalStateException.class, () -> config.tamperReplayDetector(properties));
    }

    @Test
    void failsFastWhenNonceTtlInvalid() {
        PresignedSecurityProperties properties = new PresignedSecurityProperties();
        properties.setEnabled(false);
        properties.setNonceTtl(Duration.ZERO);

        assertThrows(IllegalStateException.class, () -> config.tamperReplayDetector(properties));
    }
}
