# Next To-dos Plan

## Ziel
Stabiler, voll containerisierter End-to-End-Run mit gruenem Status fuer GraphQL + UI + Observability.

## Status Snapshot
- Harmonisierung von `scripts/run-all-tests-complete.sh` mit dem E2E-Runner ist begonnen.
- Neue GraphQL-E2E-Flows (Policy, Identity, Tenant, Audit) sind implementiert.
- Graphite-Forge DGS Data Fetcher fuer Policy/Identity/Tenant/Audit sind angelegt.
- Full run laeuft durch, hat aber verbleibende Fehl-Suites.

## Prioritaet 1: Playwright sauber containerisieren
1. Dedizierten Playwright Runner per Dockerfile anlegen (oder `steel-hammer/DockerfileTestRunner` erweitern).
2. Browser-Binaries im Image vorinstallieren (`npx playwright install chromium`).
3. System-Abhaengigkeiten im Image fixieren, damit kein apt-key/yarn-key Fehler zur Laufzeit auftritt.
4. `scripts/run-all-tests-complete.sh` auf den Container-Runner umstellen (statt Host-Install).

## Prioritaet 2: Verbleibende Fehlsuiten beheben
1. `Vault_Minio_SSE_Encryption`
- SSE/KMS-Konfiguration fuer MinIO pruefen oder Test auf verfuegbares SSE-Mode anpassen.

2. `Jclouds_Minio_CRUD_Via_Vault`
- TLS Truststore fuer jclouds IT-Lauf vervollstaendigen, damit PKIX-Fehler entfallen.

3. `Observability_Phase2_Proof`
- Fehlende Correlation-Header/Graphite-Negativpfad-Assertions nachziehen, bis Report `Conclusion: complete` liefert.

4. `tools/Storage-Conductor` Maven Build
- Abhaengigkeit `com.ironbucket:vault-smith:4.0.1` lokal/monorepo korrekt aufloesen (Reactor/BOM/Install-Reihenfolge).

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
