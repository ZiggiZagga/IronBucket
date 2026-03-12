package com.ironbucket.adminshell.shell;

import com.ironbucket.adminshell.audit.AuditService;
import com.ironbucket.adminshell.catalog.CatalogProvider;
import com.ironbucket.adminshell.security.SecurityUtil;
import com.ironbucket.adminshell.service.BackfillService;
import com.ironbucket.adminshell.service.BackfillStatus;
import com.ironbucket.adminshell.service.InspectService;
import com.ironbucket.adminshell.service.OrphanPartService;
import com.ironbucket.adminshell.service.ReconcileResult;
import com.ironbucket.adminshell.service.ReconcileService;
import com.ironbucket.adminshell.service.ScriptResult;
import com.ironbucket.adminshell.service.ScriptRunnerService;
import com.ironbucket.adminshell.shell.provider.AdapterValueProvider;
import com.ironbucket.adminshell.shell.provider.BucketValueProvider;
import com.ironbucket.adminshell.shell.provider.ScriptPathValueProvider;
import com.ironbucket.adminshell.shell.provider.TenantValueProvider;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import java.time.Duration;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@ShellComponent
@ShellCommandGroup("admin")
public class AdminCommands {

    private final ReconcileService reconcileService;
    private final BackfillService backfillService;
    private final OrphanPartService orphanPartService;
    private final InspectService inspectService;
    private final AuditService auditService;
    private final ScriptRunnerService scriptRunnerService;
    private final Tracer tracer;
    private final boolean destructiveEnabled;
    private final CatalogProvider catalogProvider;

    public AdminCommands(ReconcileService reconcileService,
                         BackfillService backfillService,
                         OrphanPartService orphanPartService,
                         InspectService inspectService,
                         AuditService auditService,
                         ScriptRunnerService scriptRunnerService,
                         OpenTelemetry openTelemetry,
                         @Value("${admin.shell.destructive-enabled:false}") boolean destructiveEnabled,
                         CatalogProvider catalogProvider) {
        this.reconcileService = reconcileService;
        this.backfillService = backfillService;
        this.orphanPartService = orphanPartService;
        this.inspectService = inspectService;
        this.auditService = auditService;
        this.scriptRunnerService = scriptRunnerService;
        this.tracer = openTelemetry.getTracer("graphite-admin-shell");
        this.destructiveEnabled = destructiveEnabled;
        this.catalogProvider = catalogProvider;
    }

    @ShellMethod(key = "reconcile-bucket", value = "Run reconciliation for a bucket")
    public String reconcileBucket(
        @ShellOption(help = "Bucket name", valueProvider = BucketValueProvider.class) String bucket,
        @ShellOption(help = "Require explicit --force for destructive reconcile", defaultValue = "false") boolean force) {

        return inSpan("command.reconcile-bucket", () -> {
            SecurityUtil.requireRoleWithForce("ROLE_ADMIN", force);
            auditService.record("reconcile-bucket", "bucket=" + bucket + " force=" + force);
            ReconcileResult result = reconcileService.run(bucket, force);
            return new AttributedStringBuilder()
                .append("Reconcile result for bucket ")
                .append(bucket)
                .append(" -> ")
                .append(result.succeeded() ? "OK" : "FAILED",
                    result.succeeded() ? AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN) : AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                .append(" | diffs: ")
                .append(String.join(", ", result.diffs()))
                .toAnsi();
        });
    }

    @ShellMethod(key = "start-backfill", value = "Start backfill for tenant/bucket")
    public String startBackfill(
        @ShellOption(help = "Tenant ID", valueProvider = TenantValueProvider.class) String tenantId,
        @ShellOption(help = "Bucket name", valueProvider = BucketValueProvider.class) String bucket,
        @ShellOption(help = "Dry run", defaultValue = "true") boolean dryRun,
        @ShellOption(help = "Throttle duration like PT1S", defaultValue = "PT0S") Duration throttle,
        @ShellOption(help = "Require --force for live runs", defaultValue = "false") boolean force) {

        return inSpan("command.start-backfill", () -> {
            SecurityUtil.requireRoleWithForce("ROLE_ADMIN", force || dryRun);
            auditService.record("start-backfill", "tenant=" + tenantId + " bucket=" + bucket + " dryRun=" + dryRun + " throttle=" + throttle);
            BackfillStatus status = backfillService.startBackfill(tenantId, bucket, dryRun, throttle);
            return status.summary();
        });
    }

    @ShellMethod(key = "list-orphan-parts", value = "List orphan multipart parts for a bucket")
    public String listOrphanParts(@ShellOption(help = "Bucket name", valueProvider = BucketValueProvider.class) String bucket) {
        return inSpan("command.list-orphan-parts", () -> {
            SecurityUtil.requireRole("ROLE_OPERATOR");
            auditService.record("list-orphan-parts", "bucket=" + bucket);
            List<String> parts = orphanPartService.listOrphanParts(bucket);
            if (parts.isEmpty()) {
                return "No orphan parts found for bucket " + bucket;
            }
            return parts.stream().collect(Collectors.joining(System.lineSeparator()));
        });
    }

    @ShellMethod(key = "cleanup-orphan-parts", value = "Cleanup orphan multipart parts")
    public String cleanupOrphanParts(
        @ShellOption(help = "Bucket name", valueProvider = BucketValueProvider.class) String bucket,
        @ShellOption(help = "Require explicit force", defaultValue = "false") boolean force) {
        return inSpan("command.cleanup-orphan-parts", () -> {
            SecurityUtil.requireRoleWithForce("ROLE_ADMIN", force);
            auditService.record("cleanup-orphan-parts", "bucket=" + bucket + " force=" + force);
            int deleted = orphanPartService.cleanupOrphanParts(bucket, force);
            return "Deleted " + deleted + " orphan parts for bucket " + bucket;
        });
    }

    @ShellMethod(key = "inspect-object", value = "Inspect object metadata/version")
    public String inspectObject(
        @ShellOption(help = "Bucket name", valueProvider = BucketValueProvider.class) String bucket,
        @ShellOption(help = "Object key") String key,
        @ShellOption(help = "Version ID", defaultValue = ShellOption.NULL) String versionId) {
        return inSpan("command.inspect-object", () -> {
            SecurityUtil.requireRole("ROLE_READONLY");
            auditService.record("inspect-object", "bucket=" + bucket + " key=" + key + " version=" + versionId);
            return inspectService.inspectObject(bucket, key, versionId);
        });
    }

    @ShellMethod(key = "run-script", value = "Run admin script in batch mode")
    public String runScript(
        @ShellOption(help = "Script path", valueProvider = ScriptPathValueProvider.class) Path scriptPath,
        @ShellOption(help = "Require force for destructive scripts", defaultValue = "false") boolean force) {
        return inSpan("command.run-script", () -> {
            SecurityUtil.requireRoleWithForce("ROLE_ADMIN", force);
            auditService.record("run-script", "script=" + scriptPath + " force=" + force);
            ScriptResult result = scriptRunnerService.run(scriptPath, force);
            return result.summary();
        });
    }

    @ShellMethod(key = "list-adapters", value = "List registered adapters")
    public String listAdapters(@ShellOption(help = "Adapter filter", defaultValue = ShellOption.NULL, valueProvider = AdapterValueProvider.class) String adapter) {
        return inSpan("command.list-adapters", () -> {
            SecurityUtil.requireRole("ROLE_READONLY");
            List<String> adapters = catalogProvider.adapters();
            if (adapter != null) {
                return adapters.stream().filter(a -> a.contains(adapter)).collect(Collectors.joining(","));
            }
            return String.join(",", adapters);
        });
    }

    @ShellMethodAvailability({"reconcile-bucket", "start-backfill", "cleanup-orphan-parts", "run-script"})
    public Availability destructiveCommandsAvailability() {
        if (!destructiveEnabled) {
            return Availability.unavailable("Destructive commands are disabled. Set admin.shell.destructive-enabled=true to enable.");
        }
        return Availability.available();
    }

    private <T> T inSpan(String spanName, java.util.function.Supplier<T> supplier) {
        Span span = tracer.spanBuilder(spanName).startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return supplier.get();
        } finally {
            span.end();
        }
    }
}
