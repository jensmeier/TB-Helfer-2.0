# MCP-SERVER-001 – Nur TB-Helfer-Serverdiagnose

Stand: 14.10.2026. Freigabe: Nutzer möchte MCP ausschließlich für den TB-Helfer-Server und bestätigt die GitHub-Prüfung/Umsetzung mit „Okay“.

## Umfang
Optionale separate MCP-Schnittstelle für vorhandene interne GET-/health/live und /health/ready sowie bestehende Monitor-Snapshots aus /tb/diagnostics-v1. Tools: server_gesundheit, monitor_uebersicht, server_probleme.

Kein Android, keine Änderung der bestehenden FastAPI, kein DB-Schema, keine NAS-Installation in diesem Arbeitsblock. Keine dynamischen Dateipfade, SQL-Abfragen, Originalbilder, Rohlogs, Audit-/Kundendaten, Container-Steuerung, Shell oder Schreibfunktionen.

## Sicherheitsvertrag
Eigener Container statt Einbau in produktive API. Diagnostik nur als read-only Volume. Kein DB-Kennwort, kein Docker-Socket. MCP-HTTP nur lokal auf 127.0.0.1 freigeben und mit verpflichtendem langem Bearer-Token schützen. Antworten mit Positivliste aus Status, Zeitstempel und unkritischen nummerischen Diagnosedaten; keine ungefilterten Felder wie detail, last_error oder recent_events. Unbekannte oder veraltete Zustände dürfen nicht als OK gemeldet werden.

Für ChatGPT-Cloud muss später eine separate authentifizierte HTTPS-Anbindung samt kompatiblem Zugriffsschutz geprüft werden. MCP-Port niemals direkt öffentlich freigeben.

## Vorgehen und Akzeptanz
Änderung separat auf Feature-Branch von feature/tb-server-1.0 mit Draft-PR sichern. Bestehende Serverkomponenten und produktive Compose-Dateien unberührt lassen. Isolierte Tests für gesperrte Schreibwege, Authentisierung, Gesundheitscheck, Monitor-Snapshot, veraltete Werte und sensible Felder. Tests unterscheiden von NAS-Runtime-/ChatGPT-E2E. Installation erst später über GitHub Desktop, File Station und Container Manager.

Verbindliche Projektregeln unter projektsteuerung/00_START_HIER.md beachten; bestehende laufende Serverarbeiten und CI-Kostenschutz nicht verdrängen.

## GitHub-Umsetzung / Checkpoint (14.10.2026)

- Optionaler MCP-Sidecar-Quellstand auf `feature/tb-server-mcp-readonly` angelegt (Code-Commit `a336f2a0c855682f4729522351363962aad5a2ac`; robuster Umgang mit fehlerhaften Diagnosedaten `8eb1bd0d330be2d8745b543ee89c8cbee2356154`).
- Neue Quelldateien: `server/mcp_gateway/{service,diagnostics,auth}.py`, `Dockerfile`, `requirements.txt`, `README.md`, optionale `server/docker-compose.mcp.v1.yml` und eigenständiger Check `server/checks/151_verify_mcp_readonly.py`.
- Vergleich zum ursprünglichen Server-HEAD `d4008086989d919b7a24aa088c54e5c0bdeff329`: nur neue Dateien, bestehende Server-/Android-Dateien vollständig unverändert.
- Zehn statische Quell-/Konfigurationsprüfungen durchgeführt und bestanden: drei read-only-Tools, kein direkter Datenbank-/Shellzugriff, verpflichtender Token, Origin-Schutz, Feld-Positivliste, Freshness-Schutz, Loopback-Bindung, ro-Volume, eingeschränkte Docker-Rechte und Tests vorhanden.
- Python-Unit-Tests sind angelegt, aber in diesem Arbeitsblock **noch nicht ausgeführt**; kein installierter MCP-Python-SDK im lokalen Testlaufzeitraum und keine neue CI wegen des bestehenden GitHub-Actions-Minutenbudgets erzwungen.
- Noch offen: Python-Unit-/SDK-Integrationstest, Compose-/Container-Test, Prüfung des tatsächlichen NAS-Netz-/Verzeichnispfads und authentifizierter Streamable-HTTP-Handshake. Synology und aktive Container nicht verändert. ChatGPT ist noch nicht direkt mit diesem MCP verbunden.

## MCP-SERVER-001 Testauftrag – 14.10.2026

Benutzer: „Okay Testen“. Aktueller MCP-HEAD vor dem Test: `7656fe332f9ba074ed0cd8e8ab4c5ffb74995e84`, Server-Basis-HEAD `d4008086989d919b7a24aa088c54e5c0bdeff329`, Android-HEAD `6cec34423e34315c581b743668724abe17daa333`. PR #12 Draft.

Vorhandener GitHub-Actions-Lauf `38608166837` / Job `116004325972` ist fehlgeschlagen **bevor MCP-Tests liefen**: `android.yml` startet wegen des breiten Pathfilters `server/checks/1*.py`; Original-Log scheitert in `13_verify_android_btb_auto_sync_source.py` an BTB-Sync-Hook-Markern (5 Fehlermeldungen). Keine MCP-Ursache nachgewiesen, keine Android-Änderung.

Lokale Python-Umgebung hat kein installiertes `mcp`-SDK; pip-Installation scheiterte wegen abgeschalteter DNS/Netzanbindung (`Temporary failure in name resolution`). Ein erfolgreicher lokaler MCP-Handshaketest kann daher noch nicht behauptet werden.

Korrekturstrategie vor Codeänderung: MCP-Unit-Tests ausschließlich nach `server/mcp_gateway/tests/` verschieben (kein Android-CI-Auslöser durch `server/checks/1*.py`). Eigener eng begrenzter MCP-Workflow nur für den MCP-Pull-Request mit Python-Unit-Tests, authentisiertem HTTP-MCP-Handshake und optionaler Compose-Konfigurationsprüfung; keine Android-/DB-/NAS-/Gesamttests, keine bestehenden Fachtests abschwächen. Kein NAS-Zugriff. Fehler anhand Original-Logs beurteilen und Tests nachweisen.

## Erster isolierter Testlauf – 14.10.2026

GitHub Actions `38609734466`, Job `116009675389`: `mcp==1.12.4` + Testabhängigkeiten erfolgreich installiert. Von 5 Tests waren 4 Python-Unit-Tests PASS; `test_http.py` konnte wegen fehlendem `PYTHONPATH=server` nicht importieren (`ModuleNotFoundError: No module named 'mcp_gateway'`), deswegen Gesamtjob FAIL. Compose-Test übersprungen, HTTP-Handshake noch nicht geprüft. Nachgewiesene Maßnahme nur Test-Runner-Suchpfad setzen; kein Grund für Fachcode-/Sicherheitsänderungen. Keine NAS-Änderung.

## MCP-CI Importbefund – 14.10.2026

Zweiter realer Run `38609865386` / Job `116010117198`: Python-Suchpfad behoben, alle 4 Unit-Tests PASS. MCP-HTTP-Test noch beim Import fehlgeschlagen: `ImportError: cannot import name eval_type_backport from pydantic._internal._typing_extra` bei `mcp==1.12.4` zusammen mit automatisch installiertem `pydantic==2.14.1`. Ursache: ungepinnte inkompatible Pydantic-Minor-Version für diese SDK-Ausgabe. Gezielte Abhilfe: Pydantic auf bekannte 2.11er-Serie begrenzen, erneut mit echtem Handshake prüfen. Container/Compose/E2E noch nicht bestanden.
