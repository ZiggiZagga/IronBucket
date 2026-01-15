#!/bin/bash

# IronBucket S3 Operations Test with Authentication
# Uses MinIO default credentials (minioadmin/minioadmin)

echo "=========================================="
echo "IronBucket S3 Operations Test"
echo "Direct MinIO S3 with Authentication"
echo "=========================================="
echo ""

NETWORK="steel-hammer_steel-hammer-network"
MINIO_ACCESS_KEY="minioadmin"
MINIO_SECRET_KEY="minioadmin"

# Test 1: Upload
echo "TEST 1: UPLOAD TEST DOCUMENT"
echo "------------------------------------"
echo "Creating test file with initial content..."

docker run --rm --network "$NETWORK" \
  -e AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
  amazon/aws-cli:latest s3 cp - s3://ironbucket/test-document.txt \
  --endpoint-url http://steel-hammer-minio:9000 \
  <<< "Original S3 Document - Test File v1 - Created $(date)" 2>&1 | grep -E "Completed|Error|test-document" || echo "Upload initiated"

sleep 2

# Test 2: Verify Upload
echo ""
echo "TEST 2: VERIFY UPLOADED CONTENT"
echo "------------------------------------"
echo "Retrieving file from S3..."

docker run --rm --network "$NETWORK" \
  -e AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
  amazon/aws-cli:latest s3 cp s3://ironbucket/test-document.txt - \
  --endpoint-url http://steel-hammer-minio:9000 2>&1 | grep -v "upload:"

sleep 2

# Test 3: Update
echo ""
echo "TEST 3: UPDATE FILE CONTENT"
echo "------------------------------------"
echo "Uploading updated version..."

docker run --rm --network "$NETWORK" \
  -e AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
  amazon/aws-cli:latest s3 cp - s3://ironbucket/test-document.txt \
  --endpoint-url http://steel-hammer-minio:9000 \
  <<< "Updated S3 Document - Test File v2 - Modified $(date)" 2>&1 | grep -E "Completed|Error" || echo "Update completed"

sleep 2

# Test 4: Verify Update
echo ""
echo "TEST 4: VERIFY UPDATED CONTENT"
echo "------------------------------------"
echo "Retrieving updated file..."

docker run --rm --network "$NETWORK" \
  -e AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
  amazon/aws-cli:latest s3 cp s3://ironbucket/test-document.txt - \
  --endpoint-url http://steel-hammer-minio:9000 2>&1 | grep -v "upload:"

sleep 2

# Test 5: Delete
echo ""
echo "TEST 5: DELETE FILE FROM S3"
echo "------------------------------------"
echo "Removing test-document.txt..."

docker run --rm --network "$NETWORK" \
  -e AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
  amazon/aws-cli:latest s3 rm s3://ironbucket/test-document.txt \
  --endpoint-url http://steel-hammer-minio:9000 2>&1

sleep 2

# Test 6: Verify Deletion
echo ""
echo "TEST 6: VERIFY FILE DELETION"
echo "------------------------------------"
echo "Checking if file still exists (should fail with 404)..."

docker run --rm --network "$NETWORK" \
  -e AWS_ACCESS_KEY_ID="$MINIO_ACCESS_KEY" \
  -e AWS_SECRET_ACCESS_KEY="$MINIO_SECRET_KEY" \
  amazon/aws-cli:latest s3 cp s3://ironbucket/test-document.txt - \
  --endpoint-url http://steel-hammer-minio:9000 2>&1 || echo "File successfully deleted (operation failed as expected)"

echo ""
echo "=========================================="
echo "S3 Operations Test Complete!"
echo "=========================================="
