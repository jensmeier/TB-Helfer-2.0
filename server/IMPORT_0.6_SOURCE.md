# Produktiven Server-0.6.0-Quellstand sicher übernehmen

Diese Anleitung gilt für den noch fehlenden produktiven Quellstand aus:

```text
/volume1/docker/tb-helfer/server/
```

Ziel ist ausschließlich, den **tatsächlich laufenden 0.6.0-Code** als Basis in `feature/admin-offline-sync` zu übernehmen. Der laufende NAS-Server wird bei diesem Schritt nicht verändert.

## 1. Vorher nichts migrieren

Vor der Quellcode-Übernahme ausdrücklich **nicht** ausführen:

- Migration 008
- Migration 009
- Rebuild/Restart des produktiven API-Containers mit dem Feature-Stand
- Änderungen an der produktiven PostgreSQL-Datenbank

Produktiv bleibt bis zum späteren Freigabeschritt:

- API `0.6.0`
- Schema `7`

## 2. Niemals übernehmen/committen

Mindestens ausschließen:

```text
.env
.env.*
*.env
cloudflared.env
*.pem
*.key
*.p12
*.pfx
*.jks
*.keystore
__pycache__/
*.pyc
```

Zusätzlich niemals in GitHub übernehmen:

- Datenbankpasswörter
- SMTP-Passwörter
- Cloudflare-/Tunnel-Token
- Tailscale-Authkeys
- API-/Admin-Secrets
- private Schlüssel
- Signing-Keys
- echte Aktivierungs-/Geräte-Tokens

Die vorhandene `.gitignore` ist zusätzliche Sicherung, ersetzt aber keine Sichtprüfung.

## 3. Erst Inventar ansehen

Auf dem NAS nur lesend:

```bash
cd /volume1/docker/tb-helfer
find server -maxdepth 4 -type f -print | sort
```

Vor dem Kopieren prüfen, welche Runtime tatsächlich verwendet wird, z. B.:

- Python-/API-Dateien
- Templates/Static-Dateien der Admin-Oberfläche
- `requirements*.txt` / `pyproject.toml`
- Dockerfile
- Startskripte/Entrypoint
- vorhandene Migration-/DB-Helfer
- Tests

Keine Serverarchitektur aus Dateinamen erraten; maßgeblich ist der produktive Quellstand selbst.

## 4. Secrets-freies Archiv erzeugen

Beispiel:

```bash
cd /volume1/docker/tb-helfer

tar \
  --exclude='server/.env' \
  --exclude='server/.env.*' \
  --exclude='server/*.env' \
  --exclude='server/**/.env' \
  --exclude='server/**/.env.*' \
  --exclude='server/cloudflared.env' \
  --exclude='server/**/cloudflared.env' \
  --exclude='server/**/__pycache__' \
  --exclude='server/**/*.pyc' \
  --exclude='server/**/*.pem' \
  --exclude='server/**/*.key' \
  --exclude='server/**/*.p12' \
  --exclude='server/**/*.pfx' \
  -czf TB-Helfer_SERVER_0.6.0_SOURCE.tar.gz \
  server
```

Hinweis: Je nach auf der Synology verfügbarer `tar`-Version können `**`-Muster unterschiedlich behandelt werden. Deshalb das Archiv danach immer auflisten und kontrollieren.

## 5. Archiv vor Weitergabe kontrollieren

```bash
tar -tzf TB-Helfer_SERVER_0.6.0_SOURCE.tar.gz | sort
```

Es dürfen keine der oben genannten Secret-Dateien auftauchen.

Zusätzlich vor dem Commit nach verdächtigen Dateinamen suchen:

```bash
tar -tzf TB-Helfer_SERVER_0.6.0_SOURCE.tar.gz \
  | grep -Ei '(^|/)(\.env|.*\.pem|.*\.key|.*\.p12|.*\.pfx|.*\.jks|.*\.keystore)$' \
  && echo 'ACHTUNG: Archiv erneut prüfen' \
  || echo 'Keine typischen Secret-Dateinamen gefunden'
```

## 6. Beim Import in den Feature-Branch

Der produktive Quellstand wird **nicht blind über** die bereits vorbereiteten Dateien kopiert.

Zu erhalten/abzugleichen sind insbesondere:

- `server/migrations/008_incremental_sync_mutations.sql`
- `server/migrations/009_worksite_collaboration.sql`
- `server/checks/00_preflight_schema7_for_008_009.sql`
- `server/checks/01_verify_schema9.sql`
- `server/API_0.7_VERTRAG.md`
- vorhandene Bahnhofsdaten unter `server/data/`

Zuerst 0.6.0-Code importieren, dann gezielt gegen den 0.7-Vertrag erweitern.

## 7. Reihenfolge nach erfolgreichem Quellcode-Import

1. produktiven 0.6.0-Start-/Auth-/DB-Code lesen;
2. vorhandene Tabellen-/API-Namen mit Preflight und Migrationen abgleichen;
3. Server-0.7-Endpunkte im **vorhandenen** Framework implementieren;
4. keine produktiven Secrets in Konfigurationsdateien übernehmen;
5. isoliert/lokal bzw. gegen DB-Testkopie testen;
6. Android 200018 neu bauen und gegen Test-0.7 prüfen;
7. Zwei-Geräte-Test durchführen;
8. erst danach unmittelbar vor einer Produktivmigration DB-Backup erstellen;
9. Preflight auf Produktion read-only prüfen;
10. Migration/Serverupdate durchführen und Schema 9 read-only nachprüfen.

## 8. Source of Truth

- Tatsächlich laufender Serverquellstand: Synology/NAS.
- Fachliche Regeln: `docs/TB-HELFER_PROJEKTREGELN.md`.
- Noch nicht produktive Entwicklung: aktiver GitHub-Feature-Branch / Draft-PR.
- Aktuelle Übergabe: `docs/TB-HELFER_AKTUELLER_STAND.md`.
