#!/bin/bash

set -euo pipefail

# S3 Operations Test - Direct Docker Access
# Tests Upload, Update, and Delete through Brazz-Nossel S3 Proxy
# Accessed from within Docker network

TEMP_DIR="${TEMP_DIR:-/tmp/ironbucket-s3}"

curl_opts=(--fail --silent --show-error --max-time 10 --retry 3 --retry-delay 2)

echo "=================================="
echo "IronBucket S3 Operations Test"
echo "Direct Docker Network Access"
echo "=================================="
echo ""

# Create test files inside container
docker exec -e TEMP_DIR="$TEMP_DIR" steel-hammer-test sh -c '
mkdir -p "$TEMP_DIR"

echo "TEST 1: UPLOAD"
echo "Creating test document..."
echo "Original content - Test Document v1 - Created $(date)" > "$TEMP_DIR/test-doc.txt"
cat "$TEMP_DIR/test-doc.txt"

echo ""
echo "Uploading to S3 proxy..."
curl "${curl_opts[@]}" -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary @"$TEMP_DIR/test-doc.txt" \
  http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n"

sleep 1

echo ""
echo "TEST 2: VERIFY UPLOAD"
echo "Downloading and checking uploaded file..."
curl "${curl_opts[@]}" http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt

sleep 1

echo ""
echo ""
echo "TEST 3: UPDATE"
echo "Creating updated version..."
echo "Updated content - Test Document v2 - Modified $(date)" > "$TEMP_DIR/test-doc-updated.txt"
cat "$TEMP_DIR/test-doc-updated.txt"

echo ""
echo "Uploading updated file..."
curl "${curl_opts[@]}" -X PUT \
  -H "Content-Type: text/plain" \
  --data-binary @"$TEMP_DIR/test-doc-updated.txt" \
  http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n"

sleep 1

echo ""
echo "TEST 4: VERIFY UPDATE"
echo "Confirming updated content..."
curl "${curl_opts[@]}" http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt

sleep 1

echo ""
echo ""
echo "TEST 5: DELETE"
echo "Removing file..."
curl "${curl_opts[@]}" -X DELETE http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt \
  -w "\nHTTP Status: %{http_code}\n"

sleep 1

echo ""
echo "TEST 6: VERIFY DELETION"
echo "Confirming file is deleted (should get 404)..."
curl "${curl_opts[@]}" -w "\nHTTP Status: %{http_code}\n" http://steel-hammer-brazz-nossel:8082/ironbucket/test-document.txt || true

echo ""
echo "=================================="
echo "S3 Operations Test Complete!"
echo "=================================="
' 2>&1
