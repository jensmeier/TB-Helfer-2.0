package de.tbhelfer.server;

/** Three-way comparison. Versions are never silently refreshed for dirty edits. */
public final class TrackMergePolicy {
    private TrackMergePolicy() {}
    public enum Action { SAME, UPLOAD, RECEIVE, CONFLICT, KEEP_LOCAL, REMOTE_REMOVED }
    public static Action decide(String base, String local, String remote, boolean mayUpload) {
        if (java.util.Objects.equals(local, remote)) return Action.SAME;
        if (base == null) {
            if (local == null) return Action.RECEIVE;
            if (remote == null) return mayUpload ? Action.UPLOAD : Action.KEEP_LOCAL;
            return Action.CONFLICT;
        }
        boolean localChanged = !java.util.Objects.equals(base, local);
        boolean remoteChanged = !java.util.Objects.equals(base, remote);
        if (localChanged && remoteChanged) return Action.CONFLICT;
        if (remoteChanged) return remote == null ? Action.REMOTE_REMOVED : Action.RECEIVE;
        if (local == null) return Action.CONFLICT; // absence is not a delete command
        return mayUpload ? Action.UPLOAD : Action.KEEP_LOCAL;
    }
}
