from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sync = (ROOT / "server/tbserver/sync.py").read_text(encoding="utf-8")
capabilities = (ROOT / "server/tbserver/capabilities.py").read_text(encoding="utf-8")

for token in (
    "class _ChangeHub:",
    "One lightweight DB poller per API process",
    "SELECT coalesce(max(change_id), 0) AS change_id FROM tb.change_feed",
    '@router.get("/changes/wait")',
    "entity_types:",
    "wait_seconds:",
    '"caught_up": not has_more',
    "await _CHANGE_HUB.wait_for_head",
    "_MAX_CONCURRENT_SYNC_QUERIES = 16",
    "_SYNC_QUERY_SEMAPHORE = asyncio.Semaphore",
    "async def _run_sync_query",
    "@router.get("/events")",
):
    assert token in sync, token

assert "await asyncio.sleep(2)" not in sync
assert "await _run_sync_query(_changes_for_worksite" in sync
assert "params.extend([user["user_id"], limit])" in sync

print("OK: server delta sync offers shared long-poll/SSE wakeup without per-client 2s DB polling")
