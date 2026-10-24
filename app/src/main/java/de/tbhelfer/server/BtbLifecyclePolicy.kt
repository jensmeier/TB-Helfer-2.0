package de.tbhelfer.server

/** Explicit targets: a retry never derives the opposite action from a changed server status. */
object BtbLifecyclePolicy {
    enum class Target { SHIFT_FINISH, SHIFT_RESUME, FINALIZE }

    fun confirmed(target: Target, finalized: Boolean, participant: String?): Boolean = when (target) {
        Target.FINALIZE -> finalized
        Target.SHIFT_FINISH -> finalized || participant.equals("FINISHED", true)
        Target.SHIFT_RESUME -> !finalized && participant.equals("WORKING", true)
    }

    fun maySend(target: Target, lead: Boolean, active: Boolean, finalized: Boolean): Boolean =
        active && !finalized && (if (target == Target.FINALIZE) lead else !lead)
}
