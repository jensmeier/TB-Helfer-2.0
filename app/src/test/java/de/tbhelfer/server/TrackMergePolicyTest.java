package de.tbhelfer.server;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static de.tbhelfer.server.TrackMergePolicy.Action.*;

public class TrackMergePolicyTest {
    @Test public void firstUpload() { assertEquals(UPLOAD, TrackMergePolicy.decide(null,"a",null,true)); }
    @Test public void firstReceive() { assertEquals(RECEIVE, TrackMergePolicy.decide(null,null,"a",true)); }
    @Test public void sameAcknowledgesRetry() { assertEquals(SAME, TrackMergePolicy.decide("old","a","a",true)); }
    @Test public void unknownDifferentCopiesConflict() { assertEquals(CONFLICT, TrackMergePolicy.decide(null,"a","b",true)); }
    @Test public void localOnlyChange() { assertEquals(UPLOAD, TrackMergePolicy.decide("a","b","a",true)); }
    @Test public void remoteOnlyChange() { assertEquals(RECEIVE, TrackMergePolicy.decide("a","a","b",true)); }
    @Test public void simultaneousChangesConflict() { assertEquals(CONFLICT, TrackMergePolicy.decide("a","b","c",true)); }
    @Test public void absenceNeverDeletesRemote() { assertEquals(CONFLICT, TrackMergePolicy.decide("a",null,"a",true)); }
    @Test public void dirtyLocalSurvivesRemoteDelete() { assertEquals(CONFLICT, TrackMergePolicy.decide("a","b",null,true)); }
    @Test public void remoteDisappearanceNeedsReview() { assertEquals(REMOTE_REMOVED, TrackMergePolicy.decide("a","a",null,true)); }
    @Test public void helperEditRetained() { assertEquals(KEEP_LOCAL, TrackMergePolicy.decide("a","b","a",false)); }
    @Test public void helperLocalSourceRetained() { assertEquals(KEEP_LOCAL, TrackMergePolicy.decide(null,"a",null,false)); }
}
