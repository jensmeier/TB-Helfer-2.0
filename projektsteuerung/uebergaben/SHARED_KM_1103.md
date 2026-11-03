# SHARED-KM-1103 – Serverumsetzung / Android-Anschluss offen

03.11.2026. Grundlage Server c5b5177, Android cc1fa7e; PR6 Draft, PR11 offen/kein Draft. Auftrag vor Entwicklung:0f674771. Nutzer: firmenübergreifender gemeinsamer Streckendatenbestand einschließlich manueller KM; anschließend bauen.

## Geändert
- server/tbserver/survey_catalog.py: drei additive Nutzer-/Geräterouten für gebundene GLOBAL-KM-Snapshots, gezieltes Speichern und versioniertes Deaktivieren. Vorhandene Segmenttabelle/Originalgeometrie/Quellprüfung/Überlappung/History/Receipts wiederverwendet. Mobile UUID bleibt stabil. Quelle ausdrücklich per Segment/Original/vollem Hash/Version. GPS/GVP/Mast-Rohanker mit Qualität und rechtem Lot, WGS84→DB_REF via PostGIS. Kein Gesamtbestand-Löschen. Gemeinsamer Historyfeed erhält vor SELECT einen kurzen SHARE-Lesebarrierelock gegen überholte uncommittete Nummern.
- server/tbserver/capabilities.py: survey_shared_km_sync_v1.
- Vertrag: [SHARED_KM_1103.md](../vertraege/SHARED_KM_1103.md). Bestehende COMPANY-Rechte/CSRF/Bearer/Adminrouten/Originale bleiben erhalten. Keine Migration, kein Androidcode und kein Produktivschreiben.

## Tatsächlich geprüft
-19 neue lokale Prüfungen in server/checks/shared_km_1103.py PASS: produktive Handler, Modelle und SQL-Funktionen mit transaktionalem DB-Double. FirmenA/B gleicher Snapshot; private Achse auch für eigene Firma abgewiesen; Quell-UUID/-Hash/-Version und Achsversion; stabile ID, Update, Retry, veränderter Receipt, parallele/stale Version, widersprüchliche Überlappung, KM0/fallende KM, Null/Bool/NaN/Inf/String, Ankergrenzen/Konsistenz/Identität, GPS-/GVP-/Mast-Lot/Mehrdeutigkeit, WGS84-Transformaufruf/Koordinatenkonflikt, Deaktivierung/Tombstone, unveränderte Originale, Rollback bei Receiptfehler und erhaltene Authdependencies.
- Bestehende Checks122 (16),125 (16),127 (21) PASS, zusammen mit neuen19:72 lokale Funktionsprüfungen.127 benötigte zunächst unveränderte admin_purge.py im lokalen Teilauszug; danach vollständiger PASS.122/125 blieben unverändert. Neue Testharness-Erwartungen bei drei Zweitoperationen zunächst an bereits korrekt vorgreifendem Receipt-/Versions-/Überlappungskonflikt gescheitert; Testdaten isoliert, keine Schutzassertionen abgeschwächt.
- Python-Syntax geänderter Runtime und beider neuer Checks PASS. Lokal Python3.12/Pydantic2.14.1. Postman-JSON/Raw-JSON-Struktur geprüft:6 Requests/13 Assertions vorbereitet, NICHT gegen API ausgeführt.
- Check113 NICHT ausführbar: lokale FastAPI-/Psycopg-Runtime fehlt (nach PYTHONPATH-Korrektur ModuleNotFoundError fastapi). Kein Beleg für einen fachlichen Check113-Fehler; kein Stub anstelle des Runtimeimports eingeführt.
- Echte PostgreSQL-Reihenfolgeprüfung vorbereitet: server/checks/shared_km_1103_postgres.py, nur TB_SYNC_TEST_DSN mit Datenbankname enthält test, eindeutiges Testschema mit garantiertem Cleanup. Führt produktiven Katalogfeedhandler mit Schemaadapter aus; Commit und Rollback eines zurückgehaltenen kleineren Events. Mangels Psycopg/PostgreSQL NICHT ausgeführt. Kein NAS-Fallback.

## Offen / Grenzen
Keine echte FastAPI-HTTP-/PostgreSQL-/PostGIS-/Postman-/Portalbrowser-/Pixel-/Poco-/firmenübergreifende Geräte-E2E-Abnahme. Keine Geschwindigkeitszusage. SHARE-Feedbarriere muss mit echten konkurrierenden Katalogimporten, Lockwartezeiten und Durchsatz geprüft werden; keine NAS-Freigabe aus Doubles ableiten. Keine neue GitHub-Gesamt-CI gestartet ([skip ci]); vorhandenen Workflow geprüft, keine Tests/Workflow/Budget verändert. Vorangehende Gesamt-CI rot ist kein Test dieses Quellstands. Keine APK/Installation/Deployment/SSH.

Android-Outbox, Quellenbindung, Altbestandübernahme und globaler Katalogempfang stehen noch aus und bleiben separater Android-Auftrag. Der bisherige destruktive Worksite-Gesamtvergleich wurde nicht nebenbei auf Android geändert. Ein unvollständiger Entwurf/Ein-Anker-Modell wird nicht als vollständig geteilter KM-Beitrag publiziert. Bestehende lokale Funktionen nicht geändert. Erst nach Androidanschluss ist Handy→Server→andere Firmenhandys praktisch umgesetzt.

Status-Excel-Nachtrag weiterhin offen: bereits dokumentierter binärer Connectorabruf nicht als XLSX verifizierbar; Original nicht überschrieben. Markdownstatus und Vertrag sind gesichert.

## Spätere Übernahme – nicht durchgeführt
Zwei Runtime-Dateien: server/tbserver/survey_catalog.py und capabilities.py, mitsamt bisher zusammengehörigem Serverstand. Keine Migration/Datenbereinigung/Storageänderung. Vor NAS-Übernahme echte Integrationsprüfungen und verifiziertes Backup; bestehender Weg GitHub Desktop→File Station→API/Worker-Neustart nur nach Deploymentauftrag. Android benötigt eigenen Implementierungs-/Build-/Installationsblock. Keine Handydaten löschen oder neu aktivieren.

Status SERVER-QUELLSTAND FERTIG / GEZIELTE LOKALE FUNKTIONSPRÜFUNGEN BESTANDEN / HTTP-DB-POSTGIS-CI OFFEN / ANDROID-ANBINDUNG OFFEN / DEPLOY OFFEN / E2E OFFEN.
