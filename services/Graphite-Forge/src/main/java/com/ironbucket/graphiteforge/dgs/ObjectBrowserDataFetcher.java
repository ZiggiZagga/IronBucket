package com.ironbucket.graphiteforge.dgs;

import com.ironbucket.graphiteforge.model.ProviderRoutingDecision;
import com.ironbucket.graphiteforge.model.S3Bucket;
import com.ironbucket.graphiteforge.model.S3Object;
import com.ironbucket.graphiteforge.service.IronBucketS3Service;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;
import java.util.List;
import java.util.Map;

@DgsComponent
public class ObjectBrowserDataFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectBrowserDataFetcher.class);

    private final IronBucketS3Service s3Service;

    public ObjectBrowserDataFetcher() {
        this(new IronBucketS3Service());
    }

    public ObjectBrowserDataFetcher(IronBucketS3Service s3Service) {
        this.s3Service = s3Service;
    }

    @PostConstruct
    void ensureBaselineBuckets() {
        LOG.info("Skipping local baseline bucket bootstrap; S3 operations are routed via Sentinel-Gear to Claimspindel");
    }

    @DgsQuery(field = "listBuckets")
    public Mono<List<S3Bucket>> listBuckets(@InputArgument String jwtToken) {
        return fromBlocking(() -> {
            LOG.info("graphql listBuckets invoked");
            return s3Service.listBuckets(resolveJwt(jwtToken));
        });
    }

    @DgsQuery(field = "getBucket")
    public Mono<S3Bucket> getBucket(
        @InputArgument String jwtToken,
        @InputArgument String bucketName
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql getBucket invoked bucketName={}", bucketName);
            return s3Service.getBucket(resolveJwt(jwtToken), bucketName);
        });
    }

    @DgsQuery(field = "listObjects")
    public Mono<List<S3Object>> listObjects(
        @InputArgument String jwtToken,
        @InputArgument String bucket,
        @InputArgument String prefix,
        @InputArgument String query,
        @InputArgument String sortBy,
        @InputArgument String sortDirection
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql listObjects invoked bucket={} query={} prefix={}", bucket, query, prefix);
            String effectivePrefix = prefix;
            if ((effectivePrefix == null || effectivePrefix.isBlank()) && query != null && !query.isBlank()) {
                effectivePrefix = query;
            }

            List<S3Object> listed = s3Service.listObjects(resolveJwt(jwtToken), bucket, effectivePrefix);

            if ("desc".equalsIgnoreCase(sortDirection)) {
                return listed.stream().sorted((a, b) -> b.key().compareToIgnoreCase(a.key())).toList();
            }
            return listed.stream().sorted((a, b) -> a.key().compareToIgnoreCase(b.key())).toList();
        });
    }

    @DgsQuery(field = "getObject")
    public Mono<S3Object> getObject(
        @InputArgument String jwtToken,
        @InputArgument String bucketName,
        @InputArgument String objectKey
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql getObject invoked bucketName={} objectKey={}", bucketName, objectKey);
            return s3Service.getObject(resolveJwt(jwtToken), bucketName, objectKey);
        });
    }

    @DgsQuery(field = "getBucketRoutingDecision")
    public Mono<ProviderRoutingDecision> getBucketRoutingDecision(
        @InputArgument String jwtToken,
        @InputArgument String tenantId,
        @InputArgument String bucketName,
        @InputArgument String requiredCapability
    ) {
        return fromBlocking(() -> {
            LOG.info(
                "graphql getBucketRoutingDecision invoked tenantId={} bucketName={} requiredCapability={}",
                tenantId,
                bucketName,
                requiredCapability
            );
            return s3Service.getBucketRoutingDecision(resolveJwt(jwtToken), tenantId, bucketName, requiredCapability);
        });
    }

    @DgsMutation(field = "createBucket")
    public Mono<S3Bucket> createBucket(
        @InputArgument String jwtToken,
        @InputArgument String bucketName,
        @InputArgument String ownerTenant
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql createBucket invoked bucketName={} ownerTenant={}", bucketName, ownerTenant);
            return s3Service.createBucket(resolveJwt(jwtToken), bucketName, ownerTenant);
        });
    }

    @DgsMutation(field = "deleteBucket")
    public Mono<Boolean> deleteBucket(
        @InputArgument String jwtToken,
        @InputArgument String bucketName
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql deleteBucket invoked bucketName={}", bucketName);
            return s3Service.deleteBucket(resolveJwt(jwtToken), bucketName);
        });
    }

    @DgsMutation(field = "uploadObject")
    public Mono<Map<String, Object>> uploadObject(
        @InputArgument String jwtToken,
        @InputArgument String bucket,
        @InputArgument String key,
        @InputArgument String content,
        @InputArgument String contentType
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql uploadObject invoked bucket={} key={} contentType={}", bucket, key, contentType);
            String jwt = resolveJwt(jwtToken);
            ensureBucketExists(jwt, bucket, tenantFromBucket(bucket));

            S3Object uploaded = s3Service.uploadObject(
                jwt,
                bucket,
                key,
                content,
                contentType == null || contentType.isBlank() ? "text/plain" : contentType
            );

            return Map.of(
                "key", uploaded.key(),
                "bucket", uploaded.bucket(),
                "size", uploaded.size()
            );
        });
    }

    @DgsMutation(field = "deleteObject")
    public Mono<Boolean> deleteObject(
        @InputArgument String jwtToken,
        @InputArgument String bucket,
        @InputArgument String key
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql deleteObject invoked bucket={} key={}", bucket, key);
            return s3Service.deleteObject(resolveJwt(jwtToken), bucket, key);
        });
    }

    @DgsMutation(field = "downloadObject")
    public Mono<Map<String, String>> downloadObject(
        @InputArgument String jwtToken,
        @InputArgument String bucket,
        @InputArgument String key
    ) {
        return fromBlocking(() -> {
            LOG.info("graphql downloadObject invoked bucket={} key={}", bucket, key);
            String url = s3Service.getPresignedUrl(resolveJwt(jwtToken), bucket, key, 300);
            return Map.of("url", url);
        });
    }

    private <T> Mono<T> fromBlocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    private String resolveJwt(String jwtToken) {
        if (jwtToken == null || jwtToken.isBlank()) {
            throw new IllegalArgumentException("jwtToken is required");
        }
        return jwtToken;
    }

    private void ensureBucketExists(String jwtToken, String bucketName, String tenant) {
        boolean exists = s3Service.listBuckets(jwtToken).stream().anyMatch(bucket -> bucket.name().equals(bucketName));
        if (!exists) {
            s3Service.createBucket(jwtToken, bucketName, tenant);
        }
    }

    private String tenantFromBucket(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            return "default";
        }
        int delimiter = bucketName.indexOf('-');
        if (delimiter > 0) {
            return bucketName.substring(0, delimiter);
        }
        return "default";
    }
}