package com.ironbucket.graphiteforge.resolver;

import com.ironbucket.graphiteforge.model.ProviderCapabilityProfile;
import com.ironbucket.graphiteforge.model.ProviderRoutingDecision;
import com.ironbucket.graphiteforge.model.S3Bucket;
import com.ironbucket.graphiteforge.service.IronBucketS3Service;

import java.util.List;

public class S3BucketResolver {

    private final IronBucketS3Service s3Service;

    public S3BucketResolver() {
        this(new IronBucketS3Service());
    }

    public S3BucketResolver(IronBucketS3Service s3Service) {
        this.s3Service = s3Service;
    }

    public List<S3Bucket> listBuckets(String jwtToken) {
        return s3Service.listBuckets(jwtToken);
    }

    public S3Bucket getBucket(String jwtToken, String bucketName) {
        return s3Service.getBucket(jwtToken, bucketName);
    }

    public S3Bucket createBucket(String jwtToken, String bucketName, String ownerTenant) {
        return s3Service.createBucket(jwtToken, bucketName, ownerTenant);
    }

    public boolean deleteBucket(String jwtToken, String bucketName) {
        return s3Service.deleteBucket(jwtToken, bucketName);
    }

    public ProviderRoutingDecision getBucketRoutingDecision(
        String jwtToken,
        String tenantId,
        String bucketName,
        String requiredCapability
    ) {
        return s3Service.getBucketRoutingDecision(jwtToken, tenantId, bucketName, requiredCapability);
    }

    public List<ProviderCapabilityProfile> getProviderCapabilityMatrix(String jwtToken) {
        return s3Service.getProviderCapabilityMatrix(jwtToken);
    }

    public List<String> providersSupportingCapabilities(String jwtToken, List<String> requiredCapabilities) {
        return s3Service.providersSupportingCapabilities(jwtToken, requiredCapabilities);
    }

    public ProviderRoutingDecision getCapabilityAwareRoutingDecision(
        String jwtToken,
        String tenantId,
        String bucketName,
        List<String> requiredCapabilities,
        List<String> deniedProviders
    ) {
        return s3Service.getCapabilityAwareRoutingDecision(
            jwtToken,
            tenantId,
            bucketName,
            requiredCapabilities,
            deniedProviders
        );
    }
}
