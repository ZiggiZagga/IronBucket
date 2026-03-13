package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.exception.PolicyNotFoundException;
import com.ironbucket.graphiteforge.model.AuditLogEntry;
import com.ironbucket.graphiteforge.model.PolicyEvaluationContext;
import com.ironbucket.graphiteforge.model.PolicyEvaluationResult;
import com.ironbucket.graphiteforge.model.PolicyInput;
import com.ironbucket.graphiteforge.model.PolicyRule;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAndAuditServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void policyLifecycleAndEvaluationBehaveLikeClaimspindelRouting() {
        PolicyManagementService service = new PolicyManagementService();
        PolicyInput input = new PolicyInput(
            "tenant-a",
            List.of("devrole", "adminrole"),
            List.of("tenant-a-files"),
            List.of("critical"),
            List.of("s3:PutObject", "s3:GetObject")
        );

        PolicyRule created = service.createPolicy("jwt", input);

        PolicyEvaluationResult allowResult = service.evaluatePolicy(
            "jwt",
            created.id(),
            new PolicyEvaluationContext(
                "tenant-a",
                List.of("devrole"),
                "s3:PutObject",
                "tenant-a-files/readme.txt"
            )
        );

        PolicyEvaluationResult denyResult = service.evaluatePolicy(
            "jwt",
            created.id(),
            new PolicyEvaluationContext(
                "tenant-a",
                List.of("devrole"),
                "s3:PutObject",
                "tenant-a-secrets/top-secret.txt"
            )
        );

        assertTrue(allowResult.decision().name().equals("ALLOW"));
        assertTrue(denyResult.decision().name().equals("DENY"));
        assertTrue(service.deletePolicy("jwt", created.id()));
        assertThrows(PolicyNotFoundException.class, () -> service.getPolicyById("jwt", created.id()));
    }

    @Test
    void auditLogServiceReadsFromLokiAndFallsBackToLocalEntries() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/loki/api/v1/query_range", exchange -> {
            String body = """
                {"status":"success","data":{"result":[{"stream":{"job":"steel-hammer-brazz-nossel"},"values":[["1710330000000000000","AUDIT user=alice action=PutObject default-alice-files/live-ui.txt"],["1710330001000000000","AUDIT ERROR user=bob action=DeleteObject default-bob-files/secret.txt"]]}]}}
                """;
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });
        server.start();

        AuditLogService remoteService = new AuditLogService("http://localhost:" + server.getAddress().getPort());
        List<AuditLogEntry> remoteEntries = remoteService.getAuditLogs("jwt", 10, 0);

        assertEquals(2, remoteEntries.size());
        assertTrue(remoteEntries.stream().anyMatch(entry -> "alice".equals(entry.user())));
        assertTrue(remoteEntries.stream().anyMatch(entry -> "default-alice-files".equals(entry.bucket())));

        server.stop(0);
        server = null;

        AuditLogService fallbackService = new AuditLogService("http://localhost:1");
        fallbackService.append(new AuditLogEntry(
            "local-1",
            Instant.now(),
            "graphite",
            "SUBSCRIBE",
            "tenant-a-audit",
            "subscription",
            "SUCCESS",
            "127.0.0.1"
        ));

        List<AuditLogEntry> fallbackEntries = fallbackService.getAuditLogs("jwt", 10, 0);
        assertFalse(fallbackEntries.isEmpty());
        assertEquals("local-1", fallbackEntries.getFirst().id());
    }
}
