package com.ironbucket.sentinelgear.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TamperReplayDetector {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final Set<String> seenNonce = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> nonceExpiry = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration nonceTtl;
    private final byte[] secret;

    public TamperReplayDetector(String sharedSecret, Duration nonceTtl, Clock clock) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalArgumentException("sharedSecret must be provided");
        }
        if (nonceTtl == null || nonceTtl.isZero() || nonceTtl.isNegative()) {
            throw new IllegalArgumentException("nonceTtl must be positive");
        }
        this.secret = sharedSecret.getBytes(StandardCharsets.UTF_8);
        this.nonceTtl = nonceTtl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public TamperReplayDetector() {
        this("ironbucket-default-hmac-secret", Duration.ofMinutes(5), Clock.systemUTC());
    }

    public boolean isValidRequest(String nonce, String hmac) {
        String payload = nonce == null ? "" : nonce;
        return validateSignedRequest(nonce, payload, hmac);
    }

    public boolean validateSignedRequest(String nonce, String payload, String providedHmac) {
        if (nonce == null || nonce.isBlank() || payload == null || providedHmac == null || providedHmac.isBlank()) {
            return false;
        }
        evictExpiredNonces();
        if (!seenNonce.add(nonce) || nonceExpiry.containsKey(nonce)) {
            return false;
        }

        String expectedHmac = sign(payload);
        boolean verified = MessageDigest.isEqual(
            expectedHmac.getBytes(StandardCharsets.UTF_8),
            providedHmac.getBytes(StandardCharsets.UTF_8)
        );
        if (!verified) {
            seenNonce.remove(nonce);
            return false;
        }

        nonceExpiry.put(nonce, Instant.now(clock).plus(nonceTtl));
        return true;
    }

    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute HMAC", ex);
        }
    }

    private void evictExpiredNonces() {
        Instant now = Instant.now(clock);
        nonceExpiry.entrySet().removeIf(entry -> {
            boolean expired = !entry.getValue().isAfter(now);
            if (expired) {
                seenNonce.remove(entry.getKey());
            }
            return expired;
        });
    }
}
