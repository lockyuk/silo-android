package org.siloserver.silo.common.player.probe

import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.siloserver.silo.common.player.ActivePlayerHolder

/**
 * Whether local media is playing or loading, for the bandwidth probe's
 * "never while media plays or starts" rule.
 *
 * Read from the process's one Media3 player ([ActivePlayerHolder]), which
 * plays every local video and audiobook on phone and TV, so one signal covers
 * every player screen. Active while the player loads ([Player.isLoading]: a
 * start, a seek, or a paused player still filling its buffer) or intends to
 * play with media ready or buffering. A paused player with a full buffer
 * leaves the link alone and does not count.
 *
 * [shortOfMedia] is the narrower "playback needs the link now" signal, for
 * background downloads that can share spare bandwidth: starting, seeking or
 * buffering, or playing with less than [HealthyBufferAheadMs] buffered ahead.
 *
 * [scope] must run on the main thread, where the player lives.
 */
class PlayerMediaActivity(holder: ActivePlayerHolder, scope: CoroutineScope) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    private val _shortOfMedia = MutableStateFlow(false)
    val shortOfMedia: StateFlow<Boolean> = _shortOfMedia.asStateFlow()

    init {
        scope.launch {
            holder.player.collectLatest { player ->
                if (player == null) {
                    _active.value = false
                    _shortOfMedia.value = false
                    return@collectLatest
                }
                val listener = object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        _active.value = player.usesMediaLink()
                        _shortOfMedia.value = player.isShortOfMedia()
                    }
                }
                player.addListener(listener)
                _active.value = player.usesMediaLink()
                try {
                    // The buffer drains without an event, so it is sampled.
                    while (true) {
                        _shortOfMedia.value = player.isShortOfMedia()
                        delay(BufferSampleMillis)
                    }
                } finally {
                    player.removeListener(listener)
                }
            }
        }
    }
}

/** Buffered media ahead of the playhead that leaves bandwidth to spare. */
internal const val HealthyBufferAheadMs = 15_000L

private const val BufferSampleMillis = 1_000L

/** Whether playback needs the network now: starting, buffering, or running low. */
internal fun Player.isShortOfMedia(): Boolean =
    playWhenReady && (
        playbackState == Player.STATE_BUFFERING ||
            (playbackState == Player.STATE_READY && totalBufferedDuration < HealthyBufferAheadMs)
        )

/** Whether this player is moving media over the network, or about to. */
internal fun Player.usesMediaLink(): Boolean =
    isLoading || (playWhenReady && (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_READY))
