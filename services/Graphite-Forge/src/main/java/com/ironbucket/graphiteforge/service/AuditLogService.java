package com.ironbucket.graphiteforge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironbucket.graphiteforge.model.AuditLogEntry;
import com.ironbucket.graphiteforge.model.AuditStatistics;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CopyOnWriteArrayList;

public class AuditLogService {

    private static final Pattern USER_PATTERN = Pattern.compile("(?:user|username|preferredUsername|actor)[=:]\\s*([A-Za-z0-9._-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUCKET_KEY_PATTERN = Pattern.compile("([a-z0-9][a-z0-9.-]{1,62})/([^\\s\"]+)");
    private static final int DEFAULT_FETCH_LIMIT = 1000;
    private static final int MAX_LOCAL_ENTRIES = 5_000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<AuditLogEntry> entries = new CopyOnWriteArrayList<>();
    private final WebClient webClient;

    public AuditLogService() {
        this(resolveLokiBaseUrl());
    }

    public AuditLogService(String lokiBaseUrl) {
        this(
            WebClient.builder().baseUrl(normalizeBaseUrl(lokiBaseUrl)).build()
        );
    }

    AuditLogService(WebClient webClient) {
        this.webClient = webClient;
    }

    public List<AuditLogEntry> getAuditLogs(String jwtToken, int limit, int offset) {
        List<AuditLogEntry> source = fetchRemoteAuditLogs(Math.max(DEFAULT_FETCH_LIMIT, limit + offset));
        if (source.isEmpty()) {
            source = List.copyOf(entries);
        } else if (!entries.isEmpty()) {
            List<AuditLogEntry> merged = new ArrayList<>(source);
            merged.addAll(entries);
            source = merged.stream()
                .collect(java.util.stream.Collectors.toMap(AuditLogEntry::id, entry -> entry, (left, right) -> left))
                .values().stream()
                .sorted(Comparator.comparing(AuditLogEntry::timestamp).reversed())
                .toList();
        }

        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(0, limit);
        if (safeOffset >= source.size()) {
            return List.of();
        }
        int toIndex = Math.min(source.size(), safeOffset + safeLimit);
        return source.subList(safeOffset, toIndex);
    }

    public List<AuditLogEntry> getAuditLogsByBucket(String jwtToken, String bucket) {
        return getAuditLogs(jwtToken, DEFAULT_FETCH_LIMIT, 0).stream()
            .filter(entry -> bucket.equals(entry.bucket()))
            .toList();
    }

    public AuditStatistics getAuditStatistics(String jwtToken, Instant startDate, Instant endDate) {
        List<AuditLogEntry> inRange = getAuditLogs(jwtToken, DEFAULT_FETCH_LIMIT, 0).stream()
            .filter(entry -> !entry.timestamp().isBefore(startDate) && !entry.timestamp().isAfter(endDate))
            .toList();
        long success = inRange.stream().filter(entry -> "SUCCESS".equals(entry.result())).count();
        long failures = inRange.size() - success;
        long users = inRange.stream().map(AuditLogEntry::user).distinct().count();
        return new AuditStatistics(inRange.size(), success, failures, users);
    }

    public void append(AuditLogEntry entry) {
        entries.add(entry);
        if (entries.size() > MAX_LOCAL_ENTRIES) {
            entries.remove(0);
        }
    }

    private List<AuditLogEntry> fetchRemoteAuditLogs(int limit) {
        try {
            String encodedQuery = "%7Bjob%3D~%22steel-hammer-(sentinel-gear%7Cclaimspindel%7Cbrazz-nossel)%22%7D";
            String response = webClient.get()
                .uri("/loki/api/v1/query_range?query=" + encodedQuery + "&limit=" + Math.max(1, limit))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseLokiResponse(response == null ? "" : response);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<AuditLogEntry> parseLokiResponse(String body) {
        if (body.isBlank()) {
            return List.of();
        }

        List<AuditLogEntry> parsed = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode results = root.path("data").path("result");
            if (!results.isArray()) {
                return List.of();
            }

            int index = 0;
            for (JsonNode streamNode : results) {
                JsonNode values = streamNode.path("values");
                if (!values.isArray()) {
                    continue;
                }

                for (JsonNode value : values) {
                    if (!value.isArray() || value.size() < 2) {
                        continue;
                    }

                    String tsNanosRaw = value.get(0).asText("");
                    String line = value.get(1).asText("");
                    if (!looksLikeAuditLine(line)) {
                        continue;
                    }

                    parsed.add(toAuditEntry(tsNanosRaw, line, index++));
                }
            }
        } catch (Exception parseFailure) {
            return List.of();
        }

        return parsed.stream()
            .sorted(Comparator.comparing(AuditLogEntry::timestamp).reversed())
            .toList();
    }

    private boolean looksLikeAuditLine(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.contains("audit")
            || normalized.contains("putobject")
            || normalized.contains("getobject")
            || normalized.contains("deleteobject")
            || normalized.contains("listobjects")
            || normalized.contains("createbucket")
            || normalized.contains("access denied");
    }

    private AuditLogEntry toAuditEntry(String tsNanosRaw, String line, int index) {
        long tsNanos;
        try {
            tsNanos = Long.parseLong(tsNanosRaw);
        } catch (NumberFormatException numberFormatException) {
            tsNanos = Instant.now().toEpochMilli() * 1_000_000L;
        }

        Instant timestamp = Instant.ofEpochSecond(tsNanos / 1_000_000_000L, tsNanos % 1_000_000_000L);
        String user = extractUser(line);
        String action = inferAction(line);
        String result = inferResult(line);
        String bucket = extractBucket(line);
        String objectKey = extractObjectKey(line);

        return new AuditLogEntry(
            "loki-" + tsNanosRaw + "-" + index,
            timestamp,
            user,
            action,
            bucket,
            objectKey,
            result,
            "observability"
        );
    }

    private String inferAction(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        if (normalized.contains("putobject") || normalized.contains("upload")) {
            return "PutObject";
        }
        if (normalized.contains("getobject") || normalized.contains("download")) {
            return "GetObject";
        }
        if (normalized.contains("deleteobject") || normalized.contains("delete")) {
            return "DeleteObject";
        }
        if (normalized.contains("listobjects") || normalized.contains("list objects")) {
            return "ListObjects";
        }
        if (normalized.contains("createbucket") || normalized.contains("create bucket")) {
            return "CreateBucket";
        }
        return "Unknown";
    }

    private String inferResult(String line) {
        String normalized = line.toLowerCase(Locale.ROOT);
        if (normalized.contains("deny") || normalized.contains("forbidden") || normalized.contains("error")) {
            return "FAILURE";
        }
        return "SUCCESS";
    }

    private String extractUser(String line) {
        Matcher userMatcher = USER_PATTERN.matcher(line);
        if (userMatcher.find()) {
            return userMatcher.group(1);
        }
        return "unknown";
    }

    private String extractBucket(String line) {
        Matcher matcher = BUCKET_KEY_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "unknown-bucket";
    }

    private String extractObjectKey(String line) {
        Matcher matcher = BUCKET_KEY_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return "unknown";
    }

    private static String resolveLokiBaseUrl() {
        String configured = System.getenv("OBSERVABILITY_LOKI_BASE_URL");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "https://steel-hammer-loki:3100";
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://steel-hammer-loki:3100";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
