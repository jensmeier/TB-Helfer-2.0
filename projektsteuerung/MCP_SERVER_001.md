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
