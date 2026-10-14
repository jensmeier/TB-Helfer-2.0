# TB-Helfer MCP – nur lesende Serverdiagnose

Separater optionaler Container. Keine Änderungen an der normalen API, DB, Migration, Android oder produktiven Compose-Datei.

## Vorhandene Datenquellen
- GET http://api:8080/health/live
- GET http://api:8080/health/ready
- monitor-state.json aus dem vorhandenen Synology-Diagnostik-Verzeichnis (Volume nur \`:ro\`)

Tools: \`server_gesundheit\`, \`monitor_uebersicht\`, \`server_probleme\`.
Kein SQL/SSH/Shell, kein Backup-Restore und keine Schreibfunktion.
Es werden keine Rohfehler, Logzeilen, URLs, Kundennamen, Foto- oder Auditdaten an das MCP-Tool zurückgegeben.

## Isoliert prüfen (ohne Synology-Betrieb zu ändern)
\`python -m unittest discover -s server/mcp_gateway/tests -p 'test*.py'\`
Diese Source-/Unit-Tests belegen noch keinen Containerstart oder NAS-/ChatGPT-End-to-End-Zugriff.

## Optionaler lokaler Start (noch kein automatisches Deployment)
1. Zuerst separat prüfen, ob das bestehende Compose-Projekt-Netz wirklich \`tb-helfer-v1_default\` heißt; keine automatische Änderung daran.
2. Einen starken zufälligen \`TB_MCP_BEARER_TOKEN\` von mindestens 32 Byte ausschließlich lokal und geheim festlegen. Nicht in GitHub committen.
3. Den bereits verwendeten \`TB_V1_DIAGNOSTICS_DIR\` korrekt angeben, damit das existierende \`monitor-state.json\` sichtbar wird; keine neuen Diagnostikdateien erfinden.
4. Nur wenn freigegeben, \`docker compose -f docker-compose.mcp.v1.yml up -d --build\` im Verzeichnis \`server/\` über Container Manager ausführen. Bei fehlender Leseberechtigung des unprivilegierten Containerusers keinen pauschalen chmod auf Daten anwenden.
5. MCP-URL ist ausschließlich lokal \`http://127.0.0.1:8090/mcp\`, \`Authorization: Bearer <Token>\`. Port NICHT im Router oder Cloudflare direkt veröffentlichen.

**Wichtig:** Die lokale TCP-Anbindung ist noch keine fertige ChatGPT-Cloud-Verbindung. Vor Remote-Zugriff sind HTTPS, OAuth-/Client-Kompatibilität und strikte Proxy-Zugriffskontrolle gesondert zu prüfen. Kein öffentliches Portforwarding.
