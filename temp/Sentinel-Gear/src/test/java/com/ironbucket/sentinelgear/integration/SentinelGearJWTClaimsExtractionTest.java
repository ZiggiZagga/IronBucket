package com.ironbucket.sentinelgear.integration;

import com.ironbucket.sentinelgear.fixtures.JWTFixtures;
import com.ironbucket.sentinelgear.identity.JWTValidator;
import com.ironbucket.sentinelgear.identity.JWTValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Issue #51: Integrate JWT parser to extract claims: `region`, `groups`, `services`;
 * fail-fast on malformed tokens
 *
 * Pattern: RED → GREEN → REFACTOR
 * ✗ Write failing test
 * ✓ Implement code to make test pass
 * ✓ Verify test passes
 */
@SpringBootTest
@DisplayName("Issue #51: JWT Claims Extraction")
class SentinelGearJWTClaimsExtractionTest {

    @Autowired
    private JWTValidator jwtValidator;

    @Autowired
    private JWTFixtures jwtFixtures;

    /**
     * TEST 1: Parse valid JWT with all required claims (region, groups, services)
     *
     * GIVEN: A valid JWT token signed with HS256
     * WHEN: JWT is validated
     * THEN: All claims (region, groups, services) should be extracted successfully
     */
    @Test
    @DisplayName("✗ test_extractValidJWT_parsesAllClaims")
    void test_extractValidJWT_parsesAllClaims() {
        // GIVEN: A valid JWT with all claims
        String jwtToken = jwtFixtures.generateAliceACMEJWT();
        assertNotNull(jwtToken);

        // WHEN: JWT is validated
        JWTValidationResult result = jwtValidator.validateWithSymmetricKey(
                jwtToken,
                "test-secret-key-that-is-long-enough-for-hs256"
        );

        // THEN: Validation succeeds
        assertTrue(result.isValid(), "JWT should be valid");

        // AND: Claims are extracted
        assertNotNull(result.getClaims());
        assertEquals("alice@acme-corp", result.getClaims().get("sub"));

        // AND: region claim is present
        Object regionObj = result.getClaims().get("region");
        assertNotNull(regionObj, "region claim should be present");
        assertEquals("us-east-1", regionObj);

        // AND: groups claim is present and is a list
        Object groupsObj = result.getClaims().get("groups");
        assertNotNull(groupsObj, "groups claim should be present");
        assertTrue(groupsObj instanceof List, "groups should be a list");
        List<?> groups = (List<?>) groupsObj;
        assertTrue(groups.contains("acme-corp:admins"), "should contain acme-corp:admins");
        assertTrue(groups.contains("acme-corp:devs"), "should contain acme-corp:devs");

        // AND: services claim is present and is a list
        Object servicesObj = result.getClaims().get("services");
        assertNotNull(servicesObj, "services claim should be present");
        assertTrue(servicesObj instanceof List, "services should be a list");
        List<?> services = (List<?>) servicesObj;
        assertTrue(services.contains("s3"), "should contain s3");
        assertTrue(services.contains("kms"), "should contain kms");
    }

    /**
     * TEST 2: Malformed JWT should fail fast
     *
     * GIVEN: A malformed JWT token (invalid format)
     * WHEN: JWT is validated
     * THEN: Validation should fail immediately with clear error message
     */
    @Test
    @DisplayName("✗ test_malformedJWT_failsFast")
    void test_malformedJWT_failsFast() {
        // GIVEN: A malformed JWT (not 3 parts)
        String malformedJWT = jwtFixtures.generateMalformedJWT();

        // WHEN: JWT is validated
        JWTValidationResult result = jwtValidator.validateWithSymmetricKey(
                malformedJWT,
                "test-secret-key-that-is-long-enough-for-hs256"
        );

        // THEN: Validation fails
        assertFalse(result.isValid(), "Malformed JWT should be invalid");

        // AND: Error message is descriptive
        assertNotNull(result.getErrorMessage());
        assertTrue(
                result.getErrorMessage().toLowerCase().contains("malformed") ||
                result.getErrorMessage().toLowerCase().contains("invalid") ||
                result.getErrorMessage().toLowerCase().contains("format"),
                "Error should mention malformed/invalid/format"
        );
    }

    /**
     * TEST 3: Unsigned JWT (alg=none) should be rejected
     *
     * GIVEN: An unsigned JWT token
     * WHEN: JWT is validated with signature checking enabled
     * THEN: Validation should fail
     */
    @Test
    @DisplayName("✗ test_missingSignature_rejectToken")
    void test_missingSignature_rejectToken() {
        // GIVEN: An unsigned JWT
        String unsignedJWT = jwtFixtures.generateUnsignedJWT("alice@acme-corp");

        // WHEN: JWT is validated
        JWTValidationResult result = jwtValidator.validateWithSymmetricKey(
                unsignedJWT,
                "test-secret-key-that-is-long-enough-for-hs256"
        );

        // THEN: Validation fails (unsigned tokens should not be accepted)
        assertFalse(result.isValid(), "Unsigned JWT should be rejected");
        assertNotNull(result.getErrorMessage());
    }

    /**
     * TEST 4: Expired JWT should be rejected
     *
     * GIVEN: An expired JWT token
     * WHEN: JWT is validated
     * THEN: Validation should fail with expiration error
     */
    @Test
    @DisplayName("✗ test_expiredToken_rejectToken")
    void test_expiredToken_rejectToken() {
        // GIVEN: An expired JWT
        String expiredJWT = jwtFixtures.generateExpiredJWT("alice@acme-corp");

        // WHEN: JWT is validated
        JWTValidationResult result = jwtValidator.validateWithSymmetricKey(
                expiredJWT,
                "test-secret-key-that-is-long-enough-for-hs256"
        );

        // THEN: Validation fails
        assertFalse(result.isValid(), "Expired JWT should be rejected");

        // AND: Error indicates expiration
        assertNotNull(result.getErrorMessage());
        assertTrue(
                result.getErrorMessage().toLowerCase().contains("expired") ||
                result.getErrorMessage().toLowerCase().contains("expiration"),
                "Error should mention expiration"
        );
    }

    /**
     * TEST 5: JWT with wrong issuer should be rejected
     *
     * GIVEN: A JWT signed with wrong issuer
     * WHEN: JWT is validated against whitelist
     * THEN: Validation should fail with issuer error
     */
    @Test
    @DisplayName("✗ test_invalidIssuer_rejectToken")
    void test_invalidIssuer_rejectToken() {
        // GIVEN: A JWT with wrong issuer
        String wrongIssuerJWT = jwtFixtures.generateWrongIssuerJWT("alice@acme-corp");

        // WHEN: JWT is validated with issuer whitelist
        JWTValidationResult result = jwtValidator.validateWithSymmetricKey(
                wrongIssuerJWT,
                "test-secret-key-that-is-long-enough-for-hs256"
        );

        // THEN: Validation fails
        assertFalse(result.isValid(), "JWT with wrong issuer should be rejected");

        // AND: Error indicates issuer problem
        assertNotNull(result.getErrorMessage());
        assertTrue(
                result.getErrorMessage().toLowerCase().contains("issuer") ||
                result.getErrorMessage().toLowerCase().contains("invalid"),
                "Error should mention issuer or validation failure"
        );
    }

}
