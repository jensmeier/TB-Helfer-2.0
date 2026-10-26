# AND-BIDIR-026 – gemeinsamer Implementierungsvertrag, 26.10.2026

Basis: Android6a0e7e4, Server923cf93; Implementierung ausdrücklich vom Nutzer beauftragt.

## Transport
- POST /api/v1/worksites: bestehender Vertrag; stabile X-Client-Change-Id und unveränderter Name je Versuch. Antwort id/name/version wird vor Binding gesichert. Wiederholung desselben Auftrags verwendet dieselbe ID. Kein Helfer als Voraussetzung.
- PATCH /api/v1/worksites/{id}: additive Capability worksite_head_update_v1. JSON {"name":"Neuer Name","base_version":3}; X-Client-Change-Id für mobile Wiederholungen. Antwort id/name/version und bestehende Kopffelder. Nur worksite.structure.edit, eigene Firma, bestehende CSRF-/Geräteauthentifizierung. Änderungen sind auf den Namen beschränkt; weitere Kopfattribute werden nicht versehentlich überschrieben.
- PATCH 409 bei Versionsabweichung; lokale Änderung und ursprüngliche Anfrage bleiben erhalten. Keine automatische Neubasierung. Ein Receipt gehört zum selben Baustellenziel; Rechte werden auch vor einem Replay geprüft.
- GET /api/v1/sync/events: worksite_id optional; ohne ID nur derselbe autorisierte Firmen-/Mitgliedschaftsfilter wie /changes/wait. Optional entity_types; Portal-Liste fragt ausschließlich WORKSITE,WORKSITE_MEMBERSHIP,WORKSITE_INVITATION ab. Bestehender baustellengebundener SSE bleibt bestehen.

## Android
- Persistente, recoverygebundene Anfragen und Ownerfingerprint; Root-/Node-IDs sind unabhängig vom sichtbaren Namen. Lokal erzeugte Node-Keys werden beim Binding transaktional unverändert in den Servernamespace übernommen; keine neue Room-Tabelle/Migration.
- Neue lokale Hauptbaustellen markieren den Anlageauftrag. Bereits vorhandene ungebundene Dirty-Struktur-/BTB-Scope wird ebenfalls provisioniert. Nicht wahllos sämtliche historischen ungebundenen lokalen Baustellen hochladen.
- Serverantwort verloren: gleicher eingefrorener POST wird wiederholt. Bestätigte IDs werden vor Bootstrap gebunden. Solange Anlagebelege ausstehen, ist automatisches Root-Bootstrap zurückgestellt; keine falsche Zusammenführung nach Namen und keine zweite Anlage über Nutzer hinzufügen.
- Neue berechtigte Baustellen/Teams werden kontextbezogen nachgeladen; persistente Initialmarker bleiben bis erfolgreichem Struktur-/aktivem BTB-Apply. Generationsprüfung verhindert Quittierung einer neueren Initialanforderung.
- Normale gebundene BTB-Sends behalten den selektiven Weg, ohne bei jedem Autosave einen Kontext-/Medienvollscan hinzuzufügen. Neue Bindings behalten den bisherigen initialen WorkManager-Abgleich; Kontext-/Teamereignisse erzeugen keinen zusätzlichen globalen Foto-Vollabgleich.
- Laufende App: Metadatenfeed plus Kontextkontrolle ungefähr jede Minute, jeweils bei der nächsten abgeschlossenen Long-Poll-Runde; keine harte 60s-Zustellgarantie. Dies erkennt auch entfernte Mitgliedschaften, deren Feedereignis dem entfernten Nutzer nicht mehr sichtbar ist.
- Lokale Haupt-TB-Vorschau vor Serverbindung sichtbar; gebundener Helfer wird dadurch nicht zum Haupt-TB. Team-/Rechteentzug macht bekannte alte BTB-Kontexte lesbar und bewahrt Dirty/Pending; keine stillschweigende Neuzuordnung ins neue Team. Hierbei blockierte Daten benötigen bewusste fachliche Prüfung.
- Offline-Umbenennen behält ursprüngliche Basis und Anfrage. Beim Upgrade ohne Version darf eine erste Basis nur übernommen werden, wenn der Servername noch exakt dem ursprünglich lokal bearbeiteten Namen entspricht. Sonst offen sichtbarer Konflikt.

## Nachweise und Grenzen
14 lokale Server-Handler-/SQL-Tests und Portal-VM-Regression bestanden. Kein FastAPI-HTTP-/PostgreSQL-/Android-/NAS-E2E. Kotlin/Room/Unit/Lint/Build 200082 offen. Keine Actions/Deployment/produktiven Datenänderungen. Gleiche Anzeigenamen sind kein Identitätsbeweis; tatsächliche Geräte-/Portal-UUIDs im Praxisablauf vergleichen.
