package de.tbhelfer.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Regression: same immutable raw source may be linked to different worksites. */
class SurveyOriginalOutboxTest {
    private val hash = "abc123def456"

    @Test
    fun sameSourceAndSameWorksiteHaveStableDeliveryKey() {
        assertEquals("$hash:17", SurveyOriginalOutbox.deliveryKey(hash, 17L))
        assertEquals(
            SurveyOriginalOutbox.deliveryKey(hash, 17L),
            SurveyOriginalOutbox.deliveryKey(hash, 17L)
        )
    }

    @Test
    fun sameSourceForDifferentWorksitesHasDifferentDeliveryKeys() {
        assertNotEquals(
            SurveyOriginalOutbox.deliveryKey(hash, 17L),
            SurveyOriginalOutbox.deliveryKey(hash, 18L)
        )
    }

    @Test
    fun unboundLegacyOriginalKeepsHashKeyWithoutFabricatingWorksite() {
        assertEquals(hash, SurveyOriginalOutbox.deliveryKey(hash, null))
        assertEquals(hash, SurveyOriginalOutbox.deliveryKey(hash, 0L))
    }
}
