package org.siloserver.silo.common.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StableArtworkKeyTest {
    private val base = "https://silo.example/api/v2/artwork/tvdb/series/1/poster/w500.abc123.webp"

    @Test
    fun dropsTheDailySignature() {
        assertEquals(base, stableArtworkKey("$base?exp=1793419200&sig=v7Mu"))
        // The next day's URL for the same picture lands on the same key.
        assertEquals(base, stableArtworkKey("$base?exp=1793505600&sig=Q2xx"))
    }

    @Test
    fun keepsOtherParameters() {
        assertEquals("$base?w=300", stableArtworkKey("$base?exp=1&w=300&sig=x"))
    }

    @Test
    fun leavesUnsignedAndNonArtworkUrlsAlone() {
        assertNull(stableArtworkKey(base))
        assertNull(stableArtworkKey("$base?w=300"))
        assertNull(stableArtworkKey("https://silo.example/api/v2/avatars/1.png?exp=1&sig=x"))
    }
}
