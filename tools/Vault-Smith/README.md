# Vault-Smith

**Cloud-agnostic S3-compatible object storage backend for IronBucket**

Vault-Smith smiths secure, abstracted blob storage operations across AWS S3, MinIO, Ceph RGW, and any S3-compatible service. It provides a unified interface (`S3StorageBackend`) enabling IronBucket's identity-driven storage policy enforcement without tight coupling to specific cloud providers.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│            IronBucket Service Mesh                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Sentinel-    │  │ Brazz-       │  │ Claimspindel │  │
│  │ Gear         │  │ Nossel       │  │              │  │
│  │ (Identity)   │  │ (S3 Proxy)   │  │ (Routing)    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                  │          │
└─────────┼─────────────────┼──────────────────┼──────────┘
          │                 │                  │
          └─────────────────┼──────────────────┘
                            │
                   ┌────────▼────────┐
                   │  Vault-Smith    │
                   │  (This Module)  │
                   │                 │
                   │ ┌──────────────┐│
                   │ │StorageBackend││ Interface
                   │ │   Interface  ││
                   │ └──────────────┘│
                   │        │        │
                   │ ┌──────▼──┐     │
                   │ │ AWS SDK │     │
                   │ │   v2    │     │
                   │ └────┬────┘     │
                   └─────┼──────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
    ┌───▼──┐         ┌───▼──┐        ┌───▼───┐
    │AWS S3│         │MinIO │        │Ceph RGW│
    └──────┘         └──────┘        └────────┘
```

## Core Components

### 1. **S3StorageBackend Interface**
Defines cloud-agnostic contract for all S3-compatible operations:

**Bucket Operations:**
- `createBucket(String bucketName)` - Create new bucket
- `bucketExists(String bucketName)` - Check bucket existence  
- `deleteBucket(String bucketName)` - Delete empty bucket
- `listBuckets()` - List all buckets

**Object Operations:**
- `uploadObject(...)` - Upload object with metadata
- `downloadObject(String bucket, String key)` - Download object stream
- `deleteObject(String bucket, String key)` - Delete object
- `listObjects(String bucket, String prefix)` - List objects with optional prefix
- `getObjectMetadata(...)` - Retrieve object metadata without download
- `copyObject(...)` - Server-side object copy

**Multipart Upload:**
- `initiateMultipartUpload(...)` - Start multipart session
- `uploadPart(...)` - Upload individual part
- `completeMultipartUpload(...)` - Finalize multipart upload
- `abortMultipartUpload(...)` - Cancel multipart session

**Lifecycle:**
- `initialize()` - Initialize backend connection
- `testConnectivity()` - Verify backend availability
- `shutdown()` - Close connections and release resources

### 2. **AwsS3Backend Implementation**
Production implementation using AWS SDK v2:
- Native support for AWS S3
- Path-style access for S3-compatible services (MinIO, Ceph)
- Custom endpoint configuration for on-premises deployments
- Comprehensive error handling with typed exceptions
- Request/response logging via SLF4J

### 3. **Configuration Model: S3BackendConfig**
```java
S3BackendConfig config = new S3BackendConfig(
    "aws-s3",                              // provider
    "us-east-1",                           // region
    "https://s3.amazonaws.com",            // endpoint (or MinIO, Ceph)
    "AKIA2JH7GFLXKEXAMPLE",                // accessKey
    "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLE" // secretKey
);
config.setPathStyleAccess(true);           // For S3-compatible services
config.setSocketTimeoutMs(30000);          // Connection timeout
```

### 4. **Data Models**
- `S3ObjectMetadata` - Object properties (size, content-type, ETag, last-modified)
- `S3UploadResult` - Upload operation response
- `S3CopyResult` - Copy operation response

## Usage Examples

### Initialize Backend
```java
S3BackendConfig config = new S3BackendConfig(
    "aws-s3", "us-east-1", 
    "http://minio:9000",  // MinIO local endpoint
    "minioadmin", "minioadmin"
);
config.setPathStyleAccess(true);

S3StorageBackend backend = new AwsS3Backend(config);
backend.initialize();
```

### Bucket Operations
```java
// Create bucket
backend.createBucket("data-vault");

// List buckets
Set<String> buckets = backend.listBuckets();

// Check existence
boolean exists = backend.bucketExists("data-vault");

// Delete bucket
backend.deleteBucket("data-vault");
```

### Object Operations
```java
// Upload object
byte[] data = "Hello, Vault-Smith!".getBytes();
S3UploadResult result = backend.uploadObject(
    "data-vault", 
    "documents/secret.txt",
    new ByteArrayInputStream(data),
    data.length,
    "text/plain"
);

// Download object
InputStream stream = backend.downloadObject("data-vault", "documents/secret.txt");

// List objects with prefix
Set<String> objects = backend.listObjects("data-vault", "documents/");

// Copy object
S3CopyResult copy = backend.copyObject(
    "data-vault", "source.txt",
    "data-vault", "backup/source.txt"
);

// Delete object
backend.deleteObject("data-vault", "documents/secret.txt");
```

### Multipart Upload
```java
String uploadId = backend.initiateMultipartUpload("data-vault", "large-file.zip");

// Upload parts
String etag1 = backend.uploadPart("data-vault", "large-file.zip", uploadId, 1,
    new ByteArrayInputStream(part1Data), part1Data.length);
String etag2 = backend.uploadPart("data-vault", "large-file.zip", uploadId, 2,
    new ByteArrayInputStream(part2Data), part2Data.length);

// Complete upload
Map<Integer, String> partETags = new HashMap<>();
partETags.put(1, etag1);
partETags.put(2, etag2);
backend.completeMultipartUpload("data-vault", "large-file.zip", uploadId, partETags);
```

## Supported Backends

| Backend | Endpoint | Credentials | Notes |
|---------|----------|-------------|-------|
| **AWS S3** | `https://s3.amazonaws.com` | IAM Access Key/Secret | Native S3 support, full feature parity |
| **MinIO** | `http://minio:9000` | minioadmin/minioadmin | Drop-in S3-compatible local storage |
| **Ceph RGW** | `http://ceph-rgw:7480` | Ceph S3 credentials | Enterprise on-premises storage |
| **DigitalOcean Spaces** | `https://nyc3.digitaloceanspaces.com` | API Key/Secret | S3-compatible cloud storage |
| **Backblaze B2** | Custom endpoint | B2 credentials | S3-compatible API |

## Integration with IronBucket

### Identity-Driven Storage Policy
Vault-Smith integrates with **Sentinel-Gear** identity framework:

1. **JWT Claims Normalization**: Identity service normalizes JWT claims (aud, sub, org)
2. **Tenant Isolation**: Each request includes normalized tenant context
3. **Storage Policy Enforcement**: Brazz-Nossel applies identity-based access control:
   ```
   GET /data-vault/org-123/secrets/* → Only org-123 users
   POST /data-vault/org-456/backup/* → Only org-456 admins
   ```
4. **Audit Logging**: All operations logged with identity context

### Service Discovery
Works with **Buzzle-Vane** (Eureka):
- Vault-Smith registers as discoverable service
- Clients resolve via service registry
- Load balancing across multiple instances

## Development

### Build
```bash
cd temp/Vault-Smith
mvn clean install
```

### Test Locally (with MinIO)
```bash
docker-compose -f ../../steel-hammer/docker-compose-steel-hammer.yml up minio

mvn clean test \
  -Ds3.endpoint=http://localhost:9000 \
  -Ds3.accessKey=minioadmin \
  -Ds3.secretKey=minioadmin \
  -Ds3.region=us-east-1
```

### Test Against AWS S3
```bash
export AWS_ACCESS_KEY_ID=your-key
export AWS_SECRET_ACCESS_KEY=your-secret

mvn clean test \
  -Ds3.endpoint=https://s3.amazonaws.com \
  -Ds3.region=us-east-1
```

## Error Handling

Vault-Smith uses AWS SDK's native exceptions:
- `NoSuchBucketException` - Bucket not found
- `NoSuchKeyException` - Object not found
- `ServiceException` - S3 service error (with detailed message)
- `SdkClientException` - Network/connection error

All operations include:
- Automatic retry with exponential backoff
- Comprehensive error logging via SLF4J
- Detailed error messages for debugging

## Performance Characteristics

- **Bucket Operations**: O(1) for single operations, O(n) for list
- **Object Upload**: Streaming upload, memory-efficient
- **Multipart Upload**: Optimal for >100MB objects, concurrent part uploads
- **Connection Pooling**: Built-in via AWS SDK
- **Timeout**: Configurable per backend instance (default 30s)

## Security Considerations

1. **Credential Management**
   - Never hardcode credentials in code
   - Use environment variables or external secret management
   - Rotate credentials regularly

2. **Network Security**
   - Use HTTPS/TLS for all remote backends (AWS, DigitalOcean)
   - Local MinIO can use HTTP for development only
   - Enable bucket versioning for data recovery

3. **Access Control**
   - IAM policies limit S3 access by principal
   - Bucket policies enforce organization isolation
   - Server-side encryption for sensitive data

4. **Audit Trail**
   - All operations logged with timestamp and identity context
   - S3 access logs enable compliance auditing
   - Integration with IronBucket audit pipeline

## Dependencies

- **AWS SDK v2** (2.24.1): S3 operations
- **Spring Boot** (4.0.1): Configuration, lifecycle management
- **Spring Cloud** (2025.1.0): Service discovery (optional)
- **SLF4J/Logback**: Structured logging
- **JUnit 5**, **Mockito**: Testing

## Contributing

See [CONTRIBUTING.md](../../CONTRIBUTING.md) for guidelines on:
- Adding new backend implementations
- Extending the `S3StorageBackend` interface
- Performance optimization
- Test coverage requirements

## License

Part of IronBucket project. See [LICENSE](../../LICENSE) for details.

## Related Modules

- **Sentinel-Gear** - Identity gateway and JWT validation
- **Brazz-Nossel** - S3 proxy with policy enforcement  
- **Storage-Conductor** - Comprehensive storage backend testing
- **Buzzle-Vane** - Service discovery and registration
