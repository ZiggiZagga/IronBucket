# Next To-dos Plan

## Ziel
Stabiler, voll containerisierter End-to-End-Run mit gruenem Status fuer GraphQL + UI + Observability.

## Status Snapshot
- Harmonisierung von `scripts/run-all-tests-complete.sh` mit dem E2E-Runner ist begonnen.
- Neue GraphQL-E2E-Flows (Policy, Identity, Tenant, Audit) sind implementiert.
- Graphite-Forge DGS Data Fetcher fuer Policy/Identity/Tenant/Audit sind angelegt.
- Dedizierter Playwright-Docker-Runner ist implementiert (`steel-hammer/DockerfilePlaywrightRunner`).
- Phase 7 in `scripts/run-all-tests-complete.sh` fuehrt Playwright jetzt containerisiert aus.
- Containerisierte Playwright-Validierung: 7/7 Specs gruen (GraphQL + S3 + Baseline + Live Upload).

## Prioritaet 1: Playwright sauber containerisieren
1. [DONE] Dedizierten Playwright Runner per Dockerfile angelegt.
2. [DONE] Browser-Binaries ueber Playwright-Base-Image bereitgestellt.
3. [DONE] Host-apt-Key-Probleme in Phase 7 eliminiert (kein `playwright install --with-deps` zur Laufzeit mehr).
4. [DONE] `scripts/run-all-tests-complete.sh` auf Container-Runner umgestellt.
5. [OPEN] `npm ci`/Lockfile-Drift bereinigen, damit kein Fallback auf `npm install` noetig ist.

## Prioritaet 2: Verbleibende Fehlsuiten beheben
1. `Vault_Minio_SSE_Encryption`
- SSE/KMS-Konfiguration fuer MinIO pruefen oder Test auf verfuegbares SSE-Mode anpassen.

2. `Jclouds_Minio_CRUD_Via_Vault`
- TLS Truststore fuer jclouds IT-Lauf vervollstaendigen, damit PKIX-Fehler entfallen.

3. `Observability_Phase2_Proof`
- Fehlende Correlation-Header/Graphite-Negativpfad-Assertions nachziehen, bis Report `Conclusion: complete` liefert.

4. `tools/Storage-Conductor` Maven Build
- Abhaengigkeit `com.ironbucket:vault-smith:4.0.1` lokal/monorepo korrekt aufloesen (Reactor/BOM/Install-Reihenfolge).

5. [DONE] Playwright Restsuiten
- `tests/ui-s3-methods-e2e.spec.ts` gefixt (Routing-tenant + screenshot-proof idempotent).
- `tests/object-browser-baseline.spec.ts` stabilisiert (seed + empty-state fallback).

6. [OPEN] Lockfile Drift beheben
- `npm ci` ist aktuell nicht lockfile-konsistent und faellt auf `npm install` zurueck.
- Ziel: `package-lock.json` mit `package.json` synchronisieren, damit CI strikt mit `npm ci` laeuft.

## Prioritaet 3: Runner-Konsistenz finalisieren
1. Rollen klar dokumentieren:
- `steel-hammer/test-scripts/run-e2e-complete.sh` = kanonischer Container-E2E-Gate-Runner.
- `scripts/run-all-tests-complete.sh` = Superset-Orchestrator.
2. Phasenbezeichnungen und Report-Texte dauerhaft synchron halten.

## Verifikation nach Fixes
1. `bash scripts/run-all-tests-complete.sh`
2. `bash steel-hammer/test-scripts/run-e2e-complete.sh`
3. Erwartung: 0 fehlgeschlagene Suiten und gruene Reports in `test-results/`.

## Artefakte
- Reports: `test-results/reports/`
- Logs: `test-results/logs/`
- Observability Evidence: `test-results/phase2-observability/`
- Playwright Artifacts: `test-results/playwright-artifacts/`
