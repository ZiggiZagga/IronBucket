package com.ironbucket.sentinelgear.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TamperReplayDetectorTest {

    @Test
    void allowsNonceAgainAfterTtlExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-03-12T10:00:00Z"));
        TamperReplayDetector detector = new TamperReplayDetector("ttl-secret", Duration.ofSeconds(30), clock);

        String nonce = "nonce-ttl";
        String payload = "GET\n/bucket/a";
        String signature = detector.sign(payload);

        assertTrue(detector.validateSignedRequest(nonce, payload, signature));
        assertFalse(detector.validateSignedRequest(nonce, payload, signature));

        clock.advance(Duration.ofSeconds(31));
        assertTrue(detector.validateSignedRequest(nonce, payload, signature));
    }

    @Test
    void legacyNonceApiUsesNonceAsPayload() {
        TamperReplayDetector detector = new TamperReplayDetector("legacy-secret", Duration.ofMinutes(1), Clock.systemUTC());
        String nonce = "legacy-nonce";
        String hmac = detector.sign(nonce);

        assertTrue(detector.isValidRequest(nonce, hmac));
        assertFalse(detector.isValidRequest(nonce, hmac));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant initial) {
            this.now = initial;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advance(Duration delta) {
            now = now.plus(delta);
        }
    }
}
