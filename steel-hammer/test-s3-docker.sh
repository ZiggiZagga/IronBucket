#!/bin/bash

# S3 Operations Test - Direct Docker Access
# Tests Upload, Update, and Delete through Brazz-Nossel S3 Proxy
# Accessed from within Docker network

echo "=================================="
echo "IronBucket S3 Operations Test"
echo "Direct Docker Network Access"
echo "=================================="
echo ""

# Create test files inside container
docker exec steel-hammer-test sh -c '
echo "TEST 1: UPLOAD"
echo "Creating test document..."
echo "Original content - Test Document v1 - Created $(date)" > /tmp/test-doc.txt
cat /tmp/test-doc.txt

echo ""
echo "Uploading to S3 proxy..."
curl -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary @/tmp/test-doc.txt \
  http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n"

sleep 1

echo ""
echo "TEST 2: VERIFY UPLOAD"
echo "Downloading and checking uploaded file..."
curl -s http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt

sleep 1

echo ""
echo ""
echo "TEST 3: UPDATE"
echo "Creating updated version..."
echo "Updated content - Test Document v2 - Modified $(date)" > /tmp/test-doc-updated.txt
cat /tmp/test-doc-updated.txt

echo ""
echo "Uploading updated file..."
curl -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary @/tmp/test-doc-updated.txt \
  http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n"

sleep 1

echo ""
echo "TEST 4: VERIFY UPDATE"
echo "Confirming updated content..."
curl -s http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt

sleep 1

echo ""
echo ""
echo "TEST 5: DELETE"
echo "Removing file..."
curl -X DELETE http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n"

sleep 1

echo ""
echo "TEST 6: VERIFY DELETION"
echo "Confirming file is deleted (should get 404)..."
curl -s -w "\nHTTP Status: %{http_code}\n" http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt || true

echo ""
echo "=================================="
echo "S3 Operations Test Complete!"
echo "=================================="
' 2>&1
