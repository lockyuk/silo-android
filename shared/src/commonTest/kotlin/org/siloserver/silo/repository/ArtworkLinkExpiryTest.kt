package org.siloserver.silo.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkLinkExpiryTest {
    private val now = 1_793_419_200_000L

    @Test
    fun pastExpiryIsExpired() {
        assertTrue(artworkLinkExpired("/api/v2/artwork/x/logo/w500.a.webp?exp=1793419100&sig=s", now))
    }

    @Test
    fun linkAboutToExpireCountsAsExpired() {
        assertTrue(artworkLinkExpired("/api/v2/artwork/x.webp?exp=1793419230&sig=s", now))
    }

    @Test
    fun futureExpiryIsCurrent() {
        assertFalse(artworkLinkExpired("/api/v2/artwork/x.webp?sig=s&exp=1793505600", now))
    }

    @Test
    fun unsignedOrMissingLinksNeverExpire() {
        assertFalse(artworkLinkExpired(null, now))
        assertFalse(artworkLinkExpired("/api/v2/artwork/x.webp", now))
        assertFalse(artworkLinkExpired("/api/v2/artwork/x.webp?w=300", now))
    }
}
