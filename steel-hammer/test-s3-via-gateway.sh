#!/bin/bash

# IronBucket S3 Operations Test via Gateway
# Tests Upload, Update, and Delete through Sentinel-Gear Gateway

echo "=================================="
echo "IronBucket S3 Operations Test"
echo "Via Sentinel-Gear Gateway (port 8080)"
echo "=================================="
echo ""

# Test gateway health
echo "Checking gateway health..."
GATEWAY_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health)
if [ "$GATEWAY_HEALTH" = "200" ]; then
    echo "✓ Sentinel-Gear Gateway is responsive (HTTP $GATEWAY_HEALTH)"
else
    echo "Gateway status: HTTP $GATEWAY_HEALTH"
fi

echo ""
echo "Attempting S3 operations through gateway..."
echo ""

# Test 1: Upload
echo "TEST 1: UPLOAD"
echo "Creating test-document.txt..."
echo "Original content - Test Document v1 - Created $(date)" > /tmp/test-doc.txt
cat /tmp/test-doc.txt

echo ""
echo "Uploading to S3 via gateway..."
UPLOAD_RESULT=$(curl -s -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary @/tmp/test-doc.txt \
  http://localhost:8080/s3/ironbucket/test-document.txt \
  -w "%{http_code}")

echo "Upload Response: $UPLOAD_RESULT"

echo ""
echo "TEST 2: VERIFY UPLOAD"
echo "Downloading file to verify..."
curl -s http://localhost:8080/s3/ironbucket/test-document.txt 2>&1

echo ""
echo ""
echo "TEST 3: UPDATE"
echo "Creating updated version..."
echo "Updated content - Test Document v2 - Modified $(date)" > /tmp/test-doc-updated.txt
cat /tmp/test-doc-updated.txt

echo ""
echo "Uploading updated file..."
UPDATE_RESULT=$(curl -s -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary @/tmp/test-doc-updated.txt \
  http://localhost:8080/s3/ironbucket/test-document.txt \
  -w "%{http_code}")

echo "Update Response: $UPDATE_RESULT"

echo ""
echo "TEST 4: VERIFY UPDATE"
echo "Checking updated content..."
curl -s http://localhost:8080/s3/ironbucket/test-document.txt 2>&1

echo ""
echo ""
echo "TEST 5: DELETE"
echo "Deleting file..."
DELETE_RESULT=$(curl -s -X DELETE \
  http://localhost:8080/s3/ironbucket/test-document.txt \
  -w "%{http_code}")

echo "Delete Response: $DELETE_RESULT"

echo ""
echo "TEST 6: VERIFY DELETION"
echo "Confirming file is deleted..."
VERIFY_DELETE=$(curl -s -w "HTTP %{http_code}" http://localhost:8080/s3/ironbucket/test-document.txt 2>&1)
echo "$VERIFY_DELETE"

echo ""
echo "=================================="
echo "Test Complete!"
echo "=================================="
