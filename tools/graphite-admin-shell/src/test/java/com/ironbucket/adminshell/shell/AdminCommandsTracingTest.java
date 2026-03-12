package com.ironbucket.adminshell.shell;

import com.ironbucket.adminshell.service.BackfillService;
import com.ironbucket.adminshell.service.BackfillStatus;
import com.ironbucket.adminshell.service.InspectService;
import com.ironbucket.adminshell.service.OrphanPartService;
import com.ironbucket.adminshell.service.ReconcileResult;
import com.ironbucket.adminshell.service.ReconcileService;
import com.ironbucket.adminshell.service.ScriptResult;
import com.ironbucket.adminshell.service.ScriptRunnerService;
import com.ironbucket.adminshell.audit.AuditService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ironbucket.adminshell.catalog.CatalogProvider;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCommandsTracingTest {

    ReconcileService reconcileService;
    BackfillService backfillService;
    OrphanPartService orphanPartService;
    InspectService inspectService;
    AuditService auditService;
    ScriptRunnerService scriptRunnerService;
    CatalogProvider catalogProvider;

    AdminCommands commands;
    InMemorySpanExporter spanExporter;
    OpenTelemetrySdk openTelemetrySdk;

    @BeforeEach
    void setUp() {
        reconcileService = new FakeReconcileService();
        backfillService = new FakeBackfillService();
        orphanPartService = new FakeOrphanPartService();
        inspectService = new FakeInspectService();
        auditService = new FakeAuditService();
        scriptRunnerService = new FakeScriptRunnerService();
        catalogProvider = new CatalogProvider();

        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(Resource.getDefault())
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        openTelemetrySdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(io.opentelemetry.context.propagation.ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

        commands = new AdminCommands(
            reconcileService,
            backfillService,
            orphanPartService,
            inspectService,
            auditService,
            scriptRunnerService,
            openTelemetrySdk,
            true,
            catalogProvider
        );

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        spanExporter.reset();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        openTelemetrySdk.getSdkTracerProvider().close();
    }

    @Test
    void reconcileCommandEmitsSpan() {
        commands.reconcileBucket("demo", true);
        assertFalse(spanExporter.getFinishedSpanItems().isEmpty(), "Expected span export for command execution");
        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertTrue(spans.stream().anyMatch(span -> "command.reconcile-bucket".equals(span.getName())));
    }

    private static final class FakeReconcileService implements ReconcileService {
        @Override
        public ReconcileResult run(String bucket, boolean force) {
            return new ReconcileResult(bucket, true, List.of("none"));
        }
    }

    private static final class FakeBackfillService implements BackfillService {
        @Override
        public BackfillStatus startBackfill(String tenantId, String bucket, boolean dryRun, Duration throttle) {
            return new BackfillStatus(tenantId, bucket, true, "scheduled");
        }
    }

    private static final class FakeOrphanPartService implements OrphanPartService {
        @Override
        public List<String> listOrphanParts(String bucket) {
            return List.of();
        }

        @Override
        public int cleanupOrphanParts(String bucket, boolean force) {
            return 0;
        }
    }

    private static final class FakeInspectService implements InspectService {
        @Override
        public String inspectObject(String bucket, String key, String versionId) {
            return "ok";
        }
    }

    private static final class FakeAuditService implements AuditService {
        private final List<String> entries = new ArrayList<>();

        @Override
        public void record(String command, String argsSummary) {
            entries.add(command + ":" + argsSummary);
        }
    }

    private static final class FakeScriptRunnerService implements ScriptRunnerService {
        @Override
        public ScriptResult run(Path scriptPath, boolean force) {
            return new ScriptResult(scriptPath.toString(), true, "ok");
        }
    }
}
