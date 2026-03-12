package com.ironbucket.adminshell.shell;

import com.ironbucket.adminshell.service.BackfillService;
import com.ironbucket.adminshell.service.InspectService;
import com.ironbucket.adminshell.service.OrphanPartService;
import com.ironbucket.adminshell.service.ReconcileResult;
import com.ironbucket.adminshell.service.ReconcileService;
import com.ironbucket.adminshell.service.ScriptRunnerService;
import com.ironbucket.adminshell.audit.AuditService;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ironbucket.adminshell.catalog.CatalogProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

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
        // Initialize mocks
        reconcileService = mock(ReconcileService.class);
        backfillService = mock(BackfillService.class);
        orphanPartService = mock(OrphanPartService.class);
        inspectService = mock(InspectService.class);
        auditService = mock(AuditService.class);
        scriptRunnerService = mock(ScriptRunnerService.class);
        catalogProvider = mock(CatalogProvider.class);

        // Setup OTEL
        spanExporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .setResource(Resource.getDefault())
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build();
        openTelemetrySdk = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(io.opentelemetry.context.propagation.ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

        // Create commands instance with mocks and OTEL
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

        // Setup security context
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("admin", "pass", "ROLE_ADMIN"));
        when(reconcileService.run("demo", true)).thenReturn(new ReconcileResult("demo", true, java.util.List.of()));
        spanExporter.reset();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reconcileCommandEmitsSpan() {
        commands.reconcileBucket("demo", true);
        assertFalse(spanExporter.getFinishedSpanItems().isEmpty(), "Expected span export for command execution");
    }
}
