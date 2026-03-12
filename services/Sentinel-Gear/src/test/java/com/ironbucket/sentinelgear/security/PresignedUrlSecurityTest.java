package com.ironbucket.sentinelgear.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresignedUrlSecurityTest {

    private static final String TTL_CLAIM = "ttl";
    private static final String REPLAY_GUARD = "replay";
    private static final String SIGNATURE_FIELD = "signature";

    @Test
    void acceptsValidSignatureOnce() {
        TamperReplayDetector detector = new TamperReplayDetector(
            "presigned-secret",
            Duration.ofMinutes(2),
            Clock.fixed(Instant.parse("2026-03-12T12:00:00Z"), ZoneOffset.UTC)
        );

        String nonce = "nonce-001";
        String payload = "GET\n/bucket/object\n" + TTL_CLAIM + "=1741781100";
        String signature = detector.sign(payload);

        assertTrue(detector.validateSignedRequest(nonce, payload, signature));
    }

    @Test
    void rejectsReplayOfSameNonceWithinTtl() {
        TamperReplayDetector detector = new TamperReplayDetector("presigned-secret", Duration.ofMinutes(5), Clock.systemUTC());

        String nonce = "nonce-duplicate-" + REPLAY_GUARD;
        String payload = "PUT\n/bucket/object\ncontent-type=text/plain\nheader=" + SIGNATURE_FIELD;
        String signature = detector.sign(payload);

        assertTrue(detector.validateSignedRequest(nonce, payload, signature));
        assertFalse(detector.validateSignedRequest(nonce, payload, signature));
    }

    @Test
    void rejectsTamperedPayloadWithForeignSignature() {
        TamperReplayDetector signer = new TamperReplayDetector("trusted-secret", Duration.ofMinutes(5), Clock.systemUTC());
        TamperReplayDetector verifier = new TamperReplayDetector("trusted-secret", Duration.ofMinutes(5), Clock.systemUTC());

        String nonce = "nonce-tamper";
        String originalPayload = "DELETE\n/bucket/object\nexpires=1741781200";
        String tamperedPayload = "DELETE\n/bucket/other-object\nexpires=1741781200";
        String signature = signer.sign(originalPayload);

        assertFalse(verifier.validateSignedRequest(nonce, tamperedPayload, signature));
    }
}
