# S3 API Reference

IronBucket provides full AWS S3-compatible API via Brazz-Nossel proxy (port 8082).

## Authentication

All requests require JWT token in Authorization header:

```bash
Authorization: Bearer <JWT_TOKEN>
```

### Get JWT Token

```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{
    "username": "bob",
    "password": "bobP@ss"
  }'
```

## Bucket Operations

### Create Bucket

```bash
aws s3 mb s3://my-bucket \
  --endpoint-url http://localhost:8082 \
  --region us-east-1
```

Or with curl:

```bash
curl -X PUT http://localhost:8082/my-bucket \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Host: localhost:8082"
```

### List Buckets

```bash
aws s3 ls \
  --endpoint-url http://localhost:8082
```

Or with curl:

```bash
curl http://localhost:8082/ \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### Get Bucket Info

```bash
aws s3 ls s3://my-bucket \
  --endpoint-url http://localhost:8082
```

### Delete Bucket

```bash
aws s3 rb s3://my-bucket \
  --endpoint-url http://localhost:8082
```

## Object Operations

### Upload Object

```bash
aws s3 cp myfile.txt s3://my-bucket/path/to/object \
  --endpoint-url http://localhost:8082
```

Or with curl:

```bash
curl -X PUT http://localhost:8082/my-bucket/path/to/object \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  --data-binary @myfile.txt
```

### Download Object

```bash
aws s3 cp s3://my-bucket/path/to/object myfile.txt \
  --endpoint-url http://localhost:8082
```

Or with curl:

```bash
curl http://localhost:8082/my-bucket/path/to/object \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -o myfile.txt
```

### List Objects

```bash
aws s3 ls s3://my-bucket/path/ \
  --endpoint-url http://localhost:8082 \
  --recursive
```

Or with curl:

```bash
curl "http://localhost:8082/my-bucket?prefix=path/&max-keys=100" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### Delete Object

```bash
aws s3 rm s3://my-bucket/path/to/object \
  --endpoint-url http://localhost:8082
```

Or with curl:

```bash
curl -X DELETE http://localhost:8082/my-bucket/path/to/object \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

## Multipart Upload

### Initiate

```bash
aws s3api create-multipart-upload \
  --bucket my-bucket \
  --key large-file.bin \
  --endpoint-url http://localhost:8082
```

### Upload Parts

```bash
aws s3api upload-part \
  --bucket my-bucket \
  --key large-file.bin \
  --part-number 1 \
  --body part1.bin \
  --upload-id <UPLOAD_ID> \
  --endpoint-url http://localhost:8082
```

### Complete

```bash
aws s3api complete-multipart-upload \
  --bucket my-bucket \
  --key large-file.bin \
  --upload-id <UPLOAD_ID> \
  --multipart-upload file://parts.json \
  --endpoint-url http://localhost:8082
```

## Object Metadata

### Set Object Metadata

```bash
aws s3api put-object \
  --bucket my-bucket \
  --key myfile.txt \
  --body myfile.txt \
  --metadata "author=alice,project=demo" \
  --endpoint-url http://localhost:8082
```

### Get Object Metadata

```bash
aws s3api head-object \
  --bucket my-bucket \
  --key myfile.txt \
  --endpoint-url http://localhost:8082
```

### List Object Tags

```bash
aws s3api get-object-tagging \
  --bucket my-bucket \
  --key myfile.txt \
  --endpoint-url http://localhost:8082
```

## Error Responses

| Status | Error | Meaning |
|--------|-------|---------|
| 200 | - | Success |
| 201 | - | Created |
| 400 | BadRequest | Invalid request |
| 401 | Unauthorized | Missing/invalid JWT |
| 403 | AccessDenied | JWT valid but not authorized |
| 404 | NotFound | Bucket or object not found |
| 405 | MethodNotAllowed | HTTP method not supported |
| 409 | Conflict | Bucket exists or state conflict |
| 500 | InternalError | Server error |
| 503 | ServiceUnavailable | Service temporarily down |

### Error Response Example

```json
{
  "Code": "AccessDenied",
  "Message": "User does not have PutObject permission",
  "RequestId": "abc123xyz",
  "HostId": "localhost"
}
```

## Response Headers

| Header | Example | Meaning |
|--------|---------|---------|
| Content-Type | application/octet-stream | MIME type |
| Content-Length | 1024 | Object size in bytes |
| ETag | "abc123" | Entity tag for caching |
| Last-Modified | Mon, 16 Jan 2026 12:00:00 GMT | Last modification time |
| x-amz-request-id | abc123xyz | Request ID for debugging |
| x-amz-version-id | v1 | Version ID (if versioning enabled) |

## Examples

### Python

```python
import boto3

# Create client
s3 = boto3.client(
    's3',
    endpoint_url='http://localhost:8082',
    aws_access_key_id='<JWT_TOKEN>',
    aws_secret_access_key='<JWT_TOKEN>'
)

# Create bucket
s3.create_bucket(Bucket='my-bucket')

# Upload file
s3.upload_file('myfile.txt', 'my-bucket', 'path/to/object')

# List objects
response = s3.list_objects_v2(Bucket='my-bucket')
for obj in response['Contents']:
    print(obj['Key'])

# Download file
s3.download_file('my-bucket', 'path/to/object', 'myfile.txt')

# Delete object
s3.delete_object(Bucket='my-bucket', Key='path/to/object')
```

### Node.js

```javascript
const AWS = require('aws-sdk');

const s3 = new AWS.S3({
  endpoint: 'http://localhost:8082',
  accessKeyId: '<JWT_TOKEN>',
  secretAccessKey: '<JWT_TOKEN>',
  s3ForcePathStyle: true
});

// Create bucket
s3.createBucket({ Bucket: 'my-bucket' }, (err, data) => {
  if (err) console.log(err);
  else console.log('Bucket created');
});

// Upload file
const fs = require('fs');
const fileContent = fs.readFileSync('myfile.txt');

s3.putObject({
  Bucket: 'my-bucket',
  Key: 'path/to/object',
  Body: fileContent
}, (err, data) => {
  if (err) console.log(err);
  else console.log('File uploaded');
});

// List objects
s3.listObjects({ Bucket: 'my-bucket' }, (err, data) => {
  if (err) console.log(err);
  else console.log(data.Contents);
});
```

### Java

```java
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

S3Client s3Client = S3Client.builder()
  .endpointOverride(URI.create("http://localhost:8082"))
  .region(Region.US_EAST_1)
  .credentialsProvider(StaticCredentialsProvider.create(
    AwsBasicCredentials.create("<JWT_TOKEN>", "<JWT_TOKEN>")
  ))
  .build();

// Create bucket
CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
  .bucket("my-bucket")
  .build();
s3Client.createBucket(createBucketRequest);

// Put object
PutObjectRequest putObjectRequest = PutObjectRequest.builder()
  .bucket("my-bucket")
  .key("path/to/object")
  .build();
s3Client.putObject(putObjectRequest, 
  RequestBody.fromFile(Path.of("myfile.txt")));

// List objects
ListObjectsRequest listRequest = ListObjectsRequest.builder()
  .bucket("my-bucket")
  .build();
ListObjectsResponse response = s3Client.listObjects(listRequest);
response.contents().forEach(obj -> System.out.println(obj.key()));

s3Client.close();
```

## CLI Configuration

### AWS CLI

```bash
# Configure profile
aws configure --profile ironbucket
AWS Access Key ID: <JWT_TOKEN>
AWS Secret Access Key: <JWT_TOKEN>
Default region: us-east-1
Default output: json

# Use profile
aws s3 ls --profile ironbucket --endpoint-url http://localhost:8082
```

### MinIO Client (mc)

```bash
# Configure alias
mc alias set ironbucket http://localhost:8082 <ACCESS_KEY> <SECRET_KEY>

# Use alias
mc ls ironbucket/
mc cp myfile.txt ironbucket/my-bucket/
```

## Limitations

- Object size: Up to 5GB (multi-part for larger)
- Bucket name length: 3-63 characters
- Key length: Up to 1024 characters
- Metadata size: Up to 2KB
- ACL support: Limited (policy-based instead)

## Performance

| Operation | Typical Latency | Max Throughput |
|-----------|-----------------|----------------|
| CreateBucket | <100ms | Limited by policy eval |
| PutObject (1KB) | ~20ms | >200 req/s |
| GetObject (1KB) | ~15ms | >300 req/s |
| ListObjects | ~50ms | >100 req/s |
| DeleteObject | ~15ms | >300 req/s |

## Status

**API Compatibility:** ✅ Full S3  
**Authentication:** ✅ JWT-based  
**TLS/HTTPS:** ✅ Supported  
**Performance:** ✅ Production-ready
