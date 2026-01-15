#!/bin/bash

# IronBucket S3 Operations Test
# Demonstrates Upload, Update, and Delete functionality
# Uses S3-compatible MinIO backend accessed through docker network

echo "=========================================="
echo "IronBucket S3 Operations Test"
echo "Direct MinIO S3 Backend Access"
echo "=========================================="
echo ""

NETWORK="steel-hammer_steel-hammer-network"

# Test 1: Upload
echo "TEST 1: UPLOAD TEST DOCUMENT"
echo "------------------------------------"
echo "Creating test file with initial content..."

docker run --rm --network "$NETWORK" alpine/curl:latest sh -c '
echo "Original S3 Document - Test File v1" > /tmp/test-doc.txt
echo "Uploading test-document.txt to MinIO S3..."
curl -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary @/tmp/test-doc.txt \
  http://steel-hammer-minio:9000/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n" 2>&1 | tail -5
'

sleep 2

# Test 2: Verify Upload
echo ""
echo "TEST 2: VERIFY UPLOADED CONTENT"
echo "------------------------------------"
echo "Retrieving file from S3..."

docker run --rm --network "$NETWORK" alpine/curl:latest sh -c '
curl -s http://steel-hammer-minio:9000/ironbucket/test-document.txt 2>&1
'

sleep 2

# Test 3: Update
echo ""
echo ""
echo "TEST 3: UPDATE FILE CONTENT"
echo "------------------------------------"
echo "Creating updated version of file..."

docker run --rm --network "$NETWORK" alpine/curl:latest sh -c '
echo "Updated S3 Document - Test File v2 - Modified $(date)" > /tmp/test-doc-v2.txt
echo "Uploading updated version..."
curl -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary @/tmp/test-doc-v2.txt \
  http://steel-hammer-minio:9000/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n" 2>&1 | tail -5
'

sleep 2

# Test 4: Verify Update
echo ""
echo "TEST 4: VERIFY UPDATED CONTENT"
echo "------------------------------------"
echo "Retrieving updated file..."

docker run --rm --network "$NETWORK" alpine/curl:latest sh -c '
curl -s http://steel-hammer-minio:9000/ironbucket/test-document.txt 2>&1
'

sleep 2

# Test 5: Delete
echo ""
echo ""
echo "TEST 5: DELETE FILE FROM S3"
echo "------------------------------------"
echo "Removing test-document.txt..."

docker run --rm --network "$NETWORK" alpine/curl:latest sh -c '
curl -X DELETE http://steel-hammer-minio:9000/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n" 2>&1 | tail -5
'

sleep 2

# Test 6: Verify Deletion
echo ""
echo "TEST 6: VERIFY FILE DELETION"
echo "------------------------------------"
echo "Attempting to retrieve deleted file (should get 404 Not Found)..."

docker run --rm --network "$NETWORK" alpine/curl:latest sh -c '
curl -s -w "\nHTTP Status: %{http_code}\n" http://steel-hammer-minio:9000/ironbucket/test-document.txt 2>&1 | tail -3 || echo "File successfully deleted"
'

echo ""
echo "=========================================="
echo "S3 Operations Test Complete!"
echo "=========================================="
