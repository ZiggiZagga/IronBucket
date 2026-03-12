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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        reconcileService = mock(ReconcileService.class);
        backfillService = mock(BackfillService.class);
        orphanPartService = mock(OrphanPartService.class);
        inspectService = mock(InspectService.class);
        auditService = mock(AuditService.class);
        scriptRunnerService = mock(ScriptRunnerService.class);
        catalogProvider = spy(new CatalogProvider());
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
        verifyNoInteractions(reconcileService, auditService);
    }

    @Test
    void reconcileRunsWhenForced() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        when(reconcileService.run("demo", true)).thenReturn(new ReconcileResult("demo", true, List.of()));

        String result = commands.reconcileBucket("demo", true);

        assertTrue(result.contains("Reconcile result for bucket demo"));
        verify(reconcileService).run("demo", true);
        verify(auditService).record(eq("reconcile-bucket"), contains("demo"));
    }

    @Test
    void listOrphanPartsRequiresOperatorRole() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("operator", "pass", "ROLE_OPERATOR"));
        when(orphanPartService.listOrphanParts("bkt")).thenReturn(List.of("part-1", "part-2"));

        String response = commands.listOrphanParts("bkt");

        assertTrue(response.contains("part-1"));
        verify(auditService).record(eq("list-orphan-parts"), contains("bkt"));
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
        when(scriptRunnerService.run(Path.of("/tmp/demo.sh"), true))
            .thenReturn(new ScriptResult("/tmp/demo.sh", true, "ok"));

        String response = commands.runScript(Path.of("/tmp/demo.sh"), true);

        assertTrue(response.contains("/tmp/demo.sh"));
        verify(auditService).record(eq("run-script"), contains("/tmp/demo.sh"));
    }

    @Test
    void startBackfillUsesThrottleAndAudit() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        when(backfillService.startBackfill("t1", "b1", true, Duration.ZERO))
            .thenReturn(new BackfillStatus("t1", "b1", true, "scheduled"));

        String response = commands.startBackfill("t1", "b1", true, Duration.ZERO, true);

        assertTrue(response.contains("scheduled"));
        ArgumentCaptor<String> argsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(eq("start-backfill"), argsCaptor.capture());
        assertTrue(argsCaptor.getValue().contains("t1"));
    }

    @Test
    void inspectObjectRequiresReadOnlyRole() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("reader", "pass", "ROLE_READONLY"));
        when(inspectService.inspectObject("b1", "key1", null)).thenReturn("ok");

        String response = commands.inspectObject("b1", "key1", null);

        assertEquals("ok", response);
        verify(auditService).record(eq("inspect-object"), contains("key1"));
    }

    @Test
    void destructiveAvailabilityRespectsFlag() {
        AdminCommands lockedCommands = new AdminCommands(reconcileService, backfillService, orphanPartService, inspectService,
            auditService, scriptRunnerService, OpenTelemetrySdk.builder().build(), false, catalogProvider);
        assertFalse(lockedCommands.destructiveCommandsAvailability().isAvailable());
    }
}
