# Object Browser Parity Plan (MinIO-aligned)

## Purpose

This plan defines practical parity targets between IronBucket UI object browsing flows and the reference behavior observed in `/tmp/object-browser/web-app`.

## What We Learned From /tmp/object-browser

The reference implementation is not just a single-page list. It has a stateful object manager with explicit lifecycle handling for transfers and a broader API surface than basic CRUD.

High-value patterns identified:
- Transfer lifecycle manager (`transferManager.ts`) with explicit progress, completion, fail, cancel handling.
- Stateful browser slice (`objectBrowserSlice.ts`) with search, version mode, deleted-object mode, preview/share modals, and rewind state.
- Async actions (`objectBrowserThunks.ts`) for single/multi downloads, share flow, and browser-native download handling.
- API capabilities in `consoleApi.ts` beyond CRUD:
  - `deleteMultipleObjects`
  - `downloadMultipleObjects`
  - `shareObject`
  - object `tags`
  - object `metadata`
  - bucket `versioning`
  - bucket `rewind`

## Current IronBucket Coverage (Implemented + Green)

- Bucket browse
- Object list
- Search
- Sort
- Upload
- Download
- Delete
- Empty state
- Actor-based trace headers

Validation evidence:
- `ironbucket-app-nextjs/tests/object-browser-baseline.spec.ts`
- `test-results/ui-e2e-traces/object-browser-baseline-trace.json`

## Parity Gaps To Close

### Wave 1 (Core Browser UX parity)
- Multi-select objects
- Multi-delete
- Multi-download as archive
- Transfer manager panel (queued/running/completed/failed/cancelled)
- Cancel transfer behavior

### Wave 2 (Object collaboration and metadata)
- Share link generation with expiration limits
- Object metadata read view
- Object tags update/read view
- Preview/open flows for supported content types

### Wave 3 (Versioning and restore semantics)
- Bucket versioning status view and toggle
- Show deleted objects mode
- Object versions timeline
- Rewind-by-date object listing
- Restore from version/delete-marker handling

### Wave 4 (Advanced hardening)
- Long filename rename/download fallback behavior
- Anonymous/public access paths for shared objects
- Capability-aware UI controls by role/tenant policy

## Test Contract Strategy

For each wave:
1. Add explicit Playwright scenarios first (red).
2. Implement UI feature + GraphQL/API contract.
3. Gate in `npm run test:e2e:ui`.
4. Keep evidence artifacts under `test-results/ui-e2e-traces/`.

## Exit Criteria: “Real Parity”

Parity is considered achieved when all waves are green in CI and each feature is covered by:
- at least one UI flow assertion,
- at least one behavioral assertion on request payload/headers,
- and one evidence artifact proving execution.
