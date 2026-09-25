#!/bin/sh
set -eu

ROOT="${TB_V1_DEPLOY_ROOT:-/volume1/docker/tb-helfer-v1}"
PROJECT="${TB_V1_COMPOSE_PROJECT:-tb-helfer-v1}"

cd "$ROOT"

if docker compose version >/dev/null 2>&1; then
  COMPOSE_BIN="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_BIN="docker-compose"
else
  echo "FEHLER: Docker Compose wurde nicht gefunden." >&2
  exit 2
fi

if [ -f "$ROOT/compose.yml" ]; then
  COMPOSE_FILE="$ROOT/compose.yml"
elif [ -f "$ROOT/docker-compose.synology-staging.v1.yml" ]; then
  COMPOSE_FILE="$ROOT/docker-compose.synology-staging.v1.yml"
else
  echo "FEHLER: Keine Compose-Datei im TB-V1-Verzeichnis gefunden." >&2
  exit 2
fi

compose() {
  # shellcheck disable=SC2086
  $COMPOSE_BIN -p "$PROJECT" -f "$COMPOSE_FILE" "$@"
}

echo "=== TB-Server 1.0 Integrations-Deploy ==="
echo "Projekt: $PROJECT"
echo "Root:    $ROOT"
echo "Compose: $COMPOSE_FILE"
echo

echo "[1/7] Laufende Kerncontainer prüfen"
for service in db api worker cloudflared; do
  cid="$(compose ps -q "$service" 2>/dev/null || true)"
  if [ -z "$cid" ]; then
    echo "FEHLER: Service '$service' läuft nicht bzw. ist nicht im Projekt vorhanden." >&2
    exit 3
  fi
done

echo "[2/7] FULL-Backup vor Migration 015"
compose exec -T api python - <<'PY'
from tbserver.backup import run_backup

result = run_backup("FULL")
if result.get("status") != "OK":
    raise SystemExit("FULL backup did not finish with status OK")
print(
    "BACKUP_OK",
    "id=" + str(result.get("id")),
    "bytes=" + str(result.get("byte_size")),
    "sha256=" + str(result.get("sha256")),
)
PY

echo "[3/7] Migrationsstand vor Apply prüfen"
compose exec -T api python - <<'PY'
from tbserver.migrations import status

current = status()
print(current)
known = current.get("latest_known")
applied = current.get("latest_applied")
pending = current.get("pending") or []
if known != 15:
    raise SystemExit(f"STOP: expected latest_known=15, got {known}")
if applied == 14:
    if pending != ["015_file_photo_metadata.sql"]:
        raise SystemExit(f"STOP: expected only migration 015 pending, got {pending}")
elif applied == 15:
    if pending:
        raise SystemExit(f"STOP: migration 15 applied but pending is not empty: {pending}")
else:
    raise SystemExit(f"STOP: unexpected latest_applied={applied}")
PY

echo "[4/7] Migrationen anwenden"
compose exec -T api python -m tbserver.migrations up
compose exec -T api python - <<'PY'
from tbserver.migrations import status

current = status()
print(current)
if (
    current.get("latest_known") != 15
    or current.get("latest_applied") != 15
    or (current.get("pending") or []) != []
):
    raise SystemExit("STOP: migration target 15/15 was not reached")
print("MIGRATIONS_OK 15/15")
PY

echo "[5/7] API/Worker/Monitor mit aktuellem Quellstand neu erzeugen"
# DB und Restore-DB werden absichtlich NICHT recreated.
compose up -d --no-deps --force-recreate api worker monitor

echo "[6/7] API-Health abwarten"
api_cid="$(compose ps -q api)"
attempt=0
while [ "$attempt" -lt 60 ]; do
  health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$api_cid" 2>/dev/null || true)"
  if [ "$health" = "healthy" ]; then
    echo "API_HEALTH_OK"
    break
  fi
  if [ "$health" = "exited" ] || [ "$health" = "dead" ]; then
    echo "FEHLER: API-Container ist '$health'." >&2
    compose logs --tail 120 api >&2 || true
    exit 4
  fi
  attempt=$((attempt + 1))
  sleep 2
done
if [ "$attempt" -ge 60 ]; then
  echo "FEHLER: API wurde innerhalb von 120 Sekunden nicht healthy." >&2
  compose logs --tail 120 api >&2 || true
  exit 4
fi

echo "[7/7] In-Container Runtime-Probe"
compose exec -T api python /app/tools/server1_runtime_probe.py

echo
echo "=== DEPLOY_OK ==="
echo "DB-Volume wurde nicht recreated."
echo "Originaldateien/Backups wurden nicht gelöscht."
echo "Als Nächstes Browser-/Session-/Foto-E2E und Delta-Lastprobe ausführen."
