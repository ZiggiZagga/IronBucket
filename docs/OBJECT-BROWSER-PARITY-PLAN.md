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

Important scope note:
- Current live UI persistence proof validates the Sentinel-Gear data path.
- Graphite-Forge runtime integration is not yet part of the steel-hammer runtime stack and is therefore not yet proven by UI E2E.

Validation evidence:
- `ironbucket-app-nextjs/tests/ui-live-upload-persistence.spec.ts`
- `test-results/ui-e2e-traces/ui-live-upload-persistence.json`

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

Additionally, management-plane parity requires Graphite-Forge to be integrated in runtime (not just module/unit scope), with live E2E evidence for Graphite-Forge-backed browser flows.
