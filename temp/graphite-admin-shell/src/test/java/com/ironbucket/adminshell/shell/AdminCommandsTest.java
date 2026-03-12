package com.ironbucket.adminshell.shell;

import com.ironbucket.adminshell.audit.AuditService;
import com.ironbucket.adminshell.catalog.CatalogProvider;
import com.ironbucket.adminshell.service.BackfillService;
import com.ironbucket.adminshell.service.BackfillStatus;
import com.ironbucket.adminshell.service.InspectService;
import com.ironbucket.adminshell.service.OrphanPartService;
import com.ironbucket.adminshell.service.ReconcileResult;
import com.ironbucket.adminshell.service.ReconcileService;
import com.ironbucket.adminshell.service.ScriptResult;
import com.ironbucket.adminshell.service.ScriptRunnerService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class AdminCommandsTest {

    private ReconcileService reconcileService;
    private BackfillService backfillService;
    private OrphanPartService orphanPartService;
    private InspectService inspectService;
    private AuditService auditService;
    private ScriptRunnerService scriptRunnerService;
    private CatalogProvider catalogProvider;
    private AdminCommands commands;

    @BeforeEach
    void setup() {
        reconcileService = new FakeReconcileService();
        backfillService = new FakeBackfillService();
        orphanPartService = new FakeOrphanPartService();
        inspectService = new FakeInspectService();
        auditService = new FakeAuditService();
        scriptRunnerService = new FakeScriptRunnerService();
        catalogProvider = new CatalogProvider();
        OpenTelemetry telemetry = OpenTelemetrySdk.builder().build();
        commands = new AdminCommands(reconcileService, backfillService, orphanPartService, inspectService,
            auditService, scriptRunnerService, telemetry, true, catalogProvider);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reconcileRequiresForce() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        assertThrows(AccessDeniedException.class, () -> commands.reconcileBucket("demo", false));
        FakeReconcileService fakeReconcile = (FakeReconcileService) reconcileService;
        FakeAuditService fakeAudit = (FakeAuditService) auditService;
        assertEquals(0, fakeReconcile.invocationCount);
        assertTrue(fakeAudit.records.isEmpty());
    }

    @Test
    void reconcileRunsWhenForced() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        String result = commands.reconcileBucket("demo", true);

        assertTrue(result.contains("Reconcile result for bucket demo"));
        FakeReconcileService fakeReconcile = (FakeReconcileService) reconcileService;
        FakeAuditService fakeAudit = (FakeAuditService) auditService;
        assertEquals(1, fakeReconcile.invocationCount);
        assertEquals("demo", fakeReconcile.lastBucket);
        assertTrue(fakeReconcile.lastForce);
        assertTrue(fakeAudit.contains("reconcile-bucket", "demo"));
    }

    @Test
    void listOrphanPartsRequiresOperatorRole() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("operator", "pass", "ROLE_OPERATOR"));
        String response = commands.listOrphanParts("bkt");

        assertTrue(response.contains("part-1"));
        FakeOrphanPartService fakeOrphanService = (FakeOrphanPartService) orphanPartService;
        FakeAuditService fakeAudit = (FakeAuditService) auditService;
        assertEquals("bkt", fakeOrphanService.lastListBucket);
        assertTrue(fakeAudit.contains("list-orphan-parts", "bkt"));
    }

    @Test
    void cleanupOrphanPartsRequiresForce() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        assertThrows(AccessDeniedException.class, () -> commands.cleanupOrphanParts("bkt", false));
    }

    @Test
    void runScriptRequiresForce() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        assertThrows(AccessDeniedException.class, () -> commands.runScript(Path.of("/tmp/demo.sh"), false));
    }

    @Test
    void runScriptAuditsAndReturnsSummary() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        String response = commands.runScript(Path.of("/tmp/demo.sh"), true);

        assertTrue(response.contains("/tmp/demo.sh"));
        FakeScriptRunnerService fakeScriptRunner = (FakeScriptRunnerService) scriptRunnerService;
        FakeAuditService fakeAudit = (FakeAuditService) auditService;
        assertEquals(Path.of("/tmp/demo.sh"), fakeScriptRunner.lastPath);
        assertTrue(fakeScriptRunner.lastForce);
        assertTrue(fakeAudit.contains("run-script", "/tmp/demo.sh"));
    }

    @Test
    void startBackfillUsesThrottleAndAudit() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        String response = commands.startBackfill("t1", "b1", true, Duration.ZERO, true);

        assertTrue(response.contains("scheduled"));
        FakeBackfillService fakeBackfill = (FakeBackfillService) backfillService;
        FakeAuditService fakeAudit = (FakeAuditService) auditService;
        assertEquals("t1", fakeBackfill.lastTenantId);
        assertEquals("b1", fakeBackfill.lastBucket);
        assertTrue(fakeBackfill.lastDryRun);
        assertEquals(Duration.ZERO, fakeBackfill.lastThrottle);
        assertTrue(fakeAudit.contains("start-backfill", "t1"));
    }

    @Test
    void inspectObjectRequiresReadOnlyRole() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("reader", "pass", "ROLE_READONLY"));
        String response = commands.inspectObject("b1", "key1", null);

        assertTrue(response.contains("bucket=b1"));
        assertTrue(response.contains("key=key1"));
        FakeInspectService fakeInspect = (FakeInspectService) inspectService;
        FakeAuditService fakeAudit = (FakeAuditService) auditService;
        assertEquals("b1", fakeInspect.lastBucket);
        assertEquals("key1", fakeInspect.lastKey);
        assertTrue(fakeAudit.contains("inspect-object", "key1"));
    }

    @Test
    void destructiveAvailabilityRespectsFlag() {
        AdminCommands lockedCommands = new AdminCommands(reconcileService, backfillService, orphanPartService, inspectService,
            auditService, scriptRunnerService, OpenTelemetrySdk.builder().build(), false, catalogProvider);
        assertFalse(lockedCommands.destructiveCommandsAvailability().isAvailable());
    }

    private static final class FakeReconcileService implements ReconcileService {
        private String lastBucket;
        private boolean lastForce;
        private int invocationCount;

        @Override
        public ReconcileResult run(String bucket, boolean force) {
            this.lastBucket = bucket;
            this.lastForce = force;
            this.invocationCount++;
            return new ReconcileResult(bucket, true, List.of());
        }
    }

    private static final class FakeBackfillService implements BackfillService {
        private String lastTenantId;
        private String lastBucket;
        private boolean lastDryRun;
        private Duration lastThrottle;

        @Override
        public BackfillStatus startBackfill(String tenantId, String bucket, boolean dryRun, Duration throttle) {
            this.lastTenantId = tenantId;
            this.lastBucket = bucket;
            this.lastDryRun = dryRun;
            this.lastThrottle = throttle;
            return new BackfillStatus(tenantId, bucket, true, "scheduled");
        }
    }

    private static final class FakeOrphanPartService implements OrphanPartService {
        private String lastListBucket;

        @Override
        public List<String> listOrphanParts(String bucket) {
            this.lastListBucket = bucket;
            return List.of("part-1", "part-2");
        }

        @Override
        public int cleanupOrphanParts(String bucket, boolean force) {
            return force ? 2 : 0;
        }
    }

    private static final class FakeInspectService implements InspectService {
        private String lastBucket;
        private String lastKey;

        @Override
        public String inspectObject(String bucket, String key, String versionId) {
            this.lastBucket = bucket;
            this.lastKey = key;
            return "bucket=%s key=%s version=%s".formatted(bucket, key, versionId);
        }
    }

    private static final class FakeScriptRunnerService implements ScriptRunnerService {
        private Path lastPath;
        private boolean lastForce;

        @Override
        public ScriptResult run(Path scriptPath, boolean force) {
            this.lastPath = scriptPath;
            this.lastForce = force;
            return new ScriptResult(scriptPath.toString(), true, "ok");
        }
    }

    private static final class FakeAuditService implements AuditService {
        private final List<AuditRecord> records = new ArrayList<>();

        @Override
        public void record(String command, String argsSummary) {
            records.add(new AuditRecord(command, argsSummary));
        }

        private boolean contains(String command, String argsFragment) {
            return records.stream().anyMatch(record ->
                    Objects.equals(record.command(), command) && record.argsSummary().contains(argsFragment));
        }
    }

    private record AuditRecord(String command, String argsSummary) {}
}
