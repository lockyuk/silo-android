package org.siloserver.silo.repository

import org.siloserver.silo.util.wallClockMillis

/** Links this close to expiry count as expired: a read is about to use them. */
private const val ExpiryMarginSeconds = 60L

/**
 * Whether [url] is a signed artwork link the server will refuse now: its
 * `exp` parameter (epoch seconds) has passed. Unsigned links never expire.
 */
fun artworkLinkExpired(url: String?, nowMillis: Long = wallClockMillis()): Boolean {
    val query = url?.substringAfter('?', "")?.takeIf { it.isNotEmpty() } ?: return false
    val exp = query.split('&').firstOrNull { it.startsWith("exp=") }?.substringAfter("exp=")?.toLongOrNull()
        ?: return false
    return exp - ExpiryMarginSeconds <= nowMillis / 1000
}
