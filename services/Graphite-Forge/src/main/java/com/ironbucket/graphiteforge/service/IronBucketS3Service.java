package com.ironbucket.graphiteforge.service;

import com.ironbucket.graphiteforge.exception.BucketNotFoundException;
import com.ironbucket.graphiteforge.model.ProviderRoutingDecision;
import com.ironbucket.graphiteforge.model.S3Bucket;
import com.ironbucket.graphiteforge.model.S3Object;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class IronBucketS3Service {

    private final List<S3Bucket> buckets = new CopyOnWriteArrayList<>();
    private final List<S3Object> objects = new CopyOnWriteArrayList<>();
    private final Map<String, RoutingProvider> tenantDefaultProviders = new ConcurrentHashMap<>();
    private final Map<String, RoutingProvider> bucketOverrideProviders = new ConcurrentHashMap<>();

    public List<S3Bucket> listBuckets(String jwtToken) {
        return buckets.stream()
            .map(bucket -> {
                ProviderRoutingDecision decision = resolveRouting(
                    bucket.ownerTenant(),
                    bucket.name(),
                    RoutingCapability.OBJECT_READ
                );
                return new S3Bucket(
                    bucket.name(),
                    bucket.creationDate(),
                    bucket.ownerTenant(),
                    decision.selectedProvider(),
                    decision.reason()
                );
            })
            .toList();
    }

    public S3Bucket getBucket(String jwtToken, String bucketName) {
        S3Bucket bucket = buckets.stream()
            .filter(existingBucket -> existingBucket.name().equals(bucketName))
            .findFirst()
            .orElseThrow(() -> new BucketNotFoundException(bucketName));

        ProviderRoutingDecision decision = resolveRouting(
            bucket.ownerTenant(),
            bucket.name(),
            RoutingCapability.OBJECT_READ
        );

        return new S3Bucket(
            bucket.name(),
            bucket.creationDate(),
            bucket.ownerTenant(),
            decision.selectedProvider(),
            decision.reason()
        );
    }

    public S3Bucket createBucket(String jwtToken, String bucketName, String ownerTenant) {
        S3Bucket persisted = new S3Bucket(bucketName, Instant.now(), ownerTenant);
        buckets.add(persisted);

        ProviderRoutingDecision decision = resolveRouting(ownerTenant, bucketName, RoutingCapability.OBJECT_WRITE);
        return new S3Bucket(
            persisted.name(),
            persisted.creationDate(),
            persisted.ownerTenant(),
            decision.selectedProvider(),
            decision.reason()
        );
    }

    public boolean deleteBucket(String jwtToken, String bucketName) {
        objects.removeIf(object -> object.bucketName().equals(bucketName));
        return buckets.removeIf(bucket -> bucket.name().equals(bucketName));
    }

    public List<S3Object> listObjects(String jwtToken, String bucketName, String prefix) {
        ProviderRoutingDecision decision = resolveRouting(
            tenantForBucket(bucketName),
            bucketName,
            RoutingCapability.OBJECT_READ
        );

        return objects.stream()
            .filter(object -> object.bucketName().equals(bucketName))
            .filter(object -> prefix == null || prefix.isBlank() || object.key().startsWith(prefix))
            .map(object -> new S3Object(
                object.key(),
                object.bucketName(),
                object.size(),
                object.lastModified(),
                object.contentType(),
                object.metadata(),
                decision.selectedProvider(),
                decision.reason()
            ))
            .toList();
    }

    public S3Object getObject(String jwtToken, String bucketName, String objectKey) {
        ProviderRoutingDecision decision = resolveRouting(
            tenantForBucket(bucketName),
            bucketName,
            RoutingCapability.OBJECT_READ
        );

        return objects.stream()
            .filter(object -> object.bucketName().equals(bucketName) && object.key().equals(objectKey))
            .findFirst()
            .map(object -> new S3Object(
                object.key(),
                object.bucketName(),
                object.size(),
                object.lastModified(),
                object.contentType(),
                object.metadata(),
                decision.selectedProvider(),
                decision.reason()
            ))
            .orElse(new S3Object(
                objectKey,
                bucketName,
                0L,
                Instant.now(),
                "application/octet-stream",
                Map.of(),
                decision.selectedProvider(),
                decision.reason()
            ));
    }

    public S3Object uploadObject(String jwtToken, String bucketName, String objectKey, long size, String contentType) {
        S3Object object = new S3Object(objectKey, bucketName, size, Instant.now(), contentType, Map.of());
        objects.removeIf(existing -> existing.bucketName().equals(bucketName) && existing.key().equals(objectKey));
        objects.add(object);

        ProviderRoutingDecision decision = resolveRouting(
            tenantForBucket(bucketName),
            bucketName,
            RoutingCapability.OBJECT_WRITE
        );

        return new S3Object(
            object.key(),
            object.bucketName(),
            object.size(),
            object.lastModified(),
            object.contentType(),
            object.metadata(),
            decision.selectedProvider(),
            decision.reason()
        );
    }

    public boolean deleteObject(String jwtToken, String bucketName, String objectKey) {
        return objects.removeIf(object -> object.bucketName().equals(bucketName) && object.key().equals(objectKey));
    }

    public String getPresignedUrl(String jwtToken, String bucketName, String objectKey, int expiresIn) {
        return "https://s3.local/" + bucketName + "/" + objectKey + "?expires=" + expiresIn;
    }

    public void setTenantDefaultProvider(String tenantId, String provider) {
        tenantDefaultProviders.put(tenantId, parseProvider(provider));
    }

    public void setBucketOverrideProvider(String tenantId, String bucketName, String provider) {
        bucketOverrideProviders.put(tenantId + "/" + bucketName, parseProvider(provider));
    }

    public ProviderRoutingDecision getBucketRoutingDecision(
        String jwtToken,
        String tenantId,
        String bucketName,
        String requiredCapability
    ) {
        return resolveRouting(tenantId, bucketName, parseCapability(requiredCapability));
    }

    private RoutingProvider parseProvider(String provider) {
        return RoutingProvider.valueOf(provider.trim().toUpperCase(Locale.ROOT));
    }

    private RoutingCapability parseCapability(String capability) {
        return RoutingCapability.valueOf(capability.trim().toUpperCase(Locale.ROOT));
    }

    private ProviderRoutingDecision resolveRouting(
        String tenantId,
        String bucketName,
        RoutingCapability capability
    ) {
        List<String> attempted = new ArrayList<>();
        String bucketKey = tenantId + "/" + bucketName;

        RoutingProvider bucketOverride = bucketOverrideProviders.get(bucketKey);
        if (bucketOverride != null) {
            if (supports(bucketOverride, capability)) {
                return new ProviderRoutingDecision(
                    tenantId,
                    bucketName,
                    capability.name(),
                    bucketOverride.name(),
                    "bucket-override"
                );
            }
            attempted.add("bucket-override-unsupported:" + bucketOverride.name());
        }

        RoutingProvider tenantDefault = tenantDefaultProviders.get(tenantId);
        if (tenantDefault != null) {
            if (supports(tenantDefault, capability)) {
                return new ProviderRoutingDecision(
                    tenantId,
                    bucketName,
                    capability.name(),
                    tenantDefault.name(),
                    "tenant-default"
                );
            }
            attempted.add("tenant-default-unsupported:" + tenantDefault.name());
        }

        for (RoutingProvider fallback : RoutingProvider.values()) {
            if ((bucketOverride != null && fallback == bucketOverride)
                || (tenantDefault != null && fallback == tenantDefault)) {
                continue;
            }

            if (supports(fallback, capability)) {
                String reason = attempted.isEmpty()
                    ? "fallback:default-order"
                    : "fallback:default-order;previous=" + String.join(",", attempted);
                return new ProviderRoutingDecision(
                    tenantId,
                    bucketName,
                    capability.name(),
                    fallback.name(),
                    reason
                );
            }
        }

        throw new IllegalStateException(
            "No provider available for capability " + capability + " on tenant " + tenantId + " bucket " + bucketName
        );
    }

    private boolean supports(RoutingProvider provider, RoutingCapability capability) {
        return switch (provider) {
            case AWS_S3 -> true;
            case GCS -> true;
            case AZURE_BLOB -> capability != RoutingCapability.VERSIONING;
            case LOCAL_FILESYSTEM -> EnumSet.of(
                RoutingCapability.OBJECT_READ,
                RoutingCapability.OBJECT_WRITE,
                RoutingCapability.OBJECT_DELETE
            ).contains(capability);
        };
    }

    private String tenantForBucket(String bucketName) {
        return buckets.stream()
            .filter(bucket -> bucket.name().equals(bucketName))
            .map(S3Bucket::ownerTenant)
            .findFirst()
            .orElseGet(() -> {
                int separator = bucketName.indexOf('-');
                if (separator > 0) {
                    return bucketName.substring(0, separator);
                }
                return "default";
            });
    }

    private enum RoutingProvider {
        AWS_S3,
        GCS,
        AZURE_BLOB,
        LOCAL_FILESYSTEM
    }

    private enum RoutingCapability {
        OBJECT_READ,
        OBJECT_WRITE,
        OBJECT_DELETE,
        MULTIPART_UPLOAD,
        VERSIONING
    }
}
