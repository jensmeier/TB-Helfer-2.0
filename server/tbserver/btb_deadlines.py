"""Server-authoritative 16h closure, rechecked under the existing BTB lock."""
import logging
from .database import transaction
from .idempotency import MutationContext

logger = logging.getLogger(__name__)


def finalize_due_btbs():
    from .btb import _finalize_btb, BtbFinalizeRequest
    with transaction() as conn, conn.cursor() as cur:
        cur.execute("""SELECT b.id AS btb_id, u.id AS user_id, u.company_id, u.company_role,
                              c.status AS company_status, c.deleted_at AS company_deleted_at
                       FROM tb.btbs b JOIN tb.teams t ON t.id=b.team_id
                       JOIN tb.users u ON u.id=t.lead_user_id
                       JOIN tb.companies c ON c.id=u.company_id
                       WHERE b.status='ACTIVE' AND b.started_at <= now()-interval '16 hours'
                         AND t.status='ACTIVE' AND t.active_btb_id=b.id
                         AND u.status='ACTIVE' AND u.deleted_at IS NULL
                         AND (u.access_valid_until IS NULL OR u.access_valid_until > now())
                         AND c.status='ACTIVE' AND c.deleted_at IS NULL
                       ORDER BY b.started_at,b.id LIMIT 20""")
        due = [dict(row) for row in cur.fetchall()]
    completed = 0
    for user in due:
        btb_id = user.pop('btb_id')
        try:
            result = _finalize_btb(btb_id, BtbFinalizeRequest(allow_unfinished=True), user,
                                   MutationContext(f'auto-16h:{btb_id}'), automatic=True)
            if not result.get('skipped'):
                completed += 1
        except Exception:
            # Data/PDF remains unchanged on failure; another scheduler cycle retries.
            logger.exception('btb_deadline_finalize_failed btb_id=%s', btb_id)
    return completed
