package org.siloserver.silo.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.catalog.CastMember
import org.siloserver.silo.tv.ui.theme.TvRailScrollBehavior
import org.siloserver.silo.tv.ui.theme.tvRailPinOnFocus
import org.siloserver.silo.tv.ui.theme.DarkSurfaceElevated
import org.siloserver.silo.tv.ui.theme.siloCardDefaults

internal fun restoredRailIndex(lastFocusedIndex: Int, itemCount: Int): Int? =
    if (itemCount <= 0) null else lastFocusedIndex.coerceIn(0, itemCount - 1)

/**
 * Horizontal rail of circular cast portraits, matching the tvOS
 * `TVDetailCastRail`. Cards lift on focus; the whole rail is a `focusGroup`
 * so seasons-rail / hero focus arithmetic stays clean. Cards lift on focus
 * and invoke [onCastMemberClick] when selected. The caller decides whether a
 * member has a routable `person_id`; members without one can still render as
 * display-only credits.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun TvCastCrewSection(
    cast: List<CastMember>,
    modifier: Modifier = Modifier,
    /**
     * Safe-area inset applied INSIDE the rail (header padding + LazyRow
     * contentPadding) instead of on the section, so the leftmost card's focus
     * ring can render into the inset instead of being clipped by the rail's
     * viewport edge (QA 2026-07-08).
     */
    horizontalContentPadding: Dp = 0.dp,
    firstItemFocusRequester: FocusRequester? = null,
    firstItemCardModifier: Modifier = Modifier,
    onDirectionDown: (() -> Boolean)? = null,
    onDirectionUp: (() -> Boolean)? = null,
    /**
     * Attaches [restoreFocusRequester] to the card at this index so the caller
     * can put focus back on the cast member that pushed a person page.
     */
    restoreFocusIndex: Int = -1,
    restoreFocusRequester: FocusRequester? = null,
    /** Fires when the restore-target card actually gains focus, so callers
     *  can end their restore window immediately instead of holding it open
     *  (and re-requesting) for a fixed number of frames. */
    onRestoreCardFocused: (() -> Unit)? = null,
    onCastMemberClick: (index: Int, member: CastMember) -> Unit = { _, _ -> },
) {
    if (cast.isEmpty()) return
    val photoSize = 100.dp
    var lastFocusedIndex by rememberSaveable { mutableIntStateOf(-1) }
    val rememberedEntryRequester = remember { FocusRequester() }
    val castListState = rememberLazyListState()
    // One list per cast snapshot: a fresh take() per composition re-keys the
    // LazyRow interval on every focus move.
    val visibleCast = remember(cast) { cast.take(24) }
    val rememberedEntryIndex = restoredRailIndex(lastFocusedIndex, visibleCast.size)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TvDetailSectionHeader(
            title = "Cast & Crew",
            modifier = Modifier.padding(horizontal = horizontalContentPadding),
        )

        TvRailScrollBehavior {
        LazyRow(
            state = castListState,
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties {
                    // While a person-page return is pending, entry into the
                    // rail (default focus, D-pad, programmatic) must land on
                    // the launch card — the enter redirect otherwise sends
                    // every entry to card 0 and silently rolls back direct
                    // requests to other cards.
                    val enterTarget = restoreFocusRequester
                        ?: rememberedEntryRequester.takeIf { rememberedEntryIndex != null }
                        ?: firstItemFocusRequester
                    if (enterTarget != null) {
                        enter = { enterTarget }
                    }
                }
                .then(
                    if (onDirectionDown != null || onDirectionUp != null) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionDown -> onDirectionDown?.invoke() ?: false
                                    Key.DirectionUp -> onDirectionUp?.invoke() ?: false
                                    else -> false
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                horizontal = horizontalContentPadding,
                vertical = 8.dp,
            ),
        ) {
            itemsIndexed(
                visibleCast,
                key = { idx, member -> "${member.personId ?: member.name}-${member.order}-$idx" },
                contentType = { _, _ -> "cast-member" },
            ) { index, member ->
                TvCastCard(
                    member = member,
                    photoSize = photoSize,
                    focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                    cardModifier = (if (index == 0) firstItemCardModifier else Modifier)
                        .then(
                            if (index == rememberedEntryIndex) {
                                Modifier.focusRequester(rememberedEntryRequester)
                            } else {
                                Modifier
                            },
                        )
                        .then(
                            if (restoreFocusRequester != null && index == restoreFocusIndex) {
                                Modifier.focusRequester(restoreFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .tvRailPinOnFocus(castListState, index, horizontalContentPadding)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                lastFocusedIndex = index
                                if (restoreFocusRequester != null && index == restoreFocusIndex) {
                                    onRestoreCardFocused?.invoke()
                                }
                            }
                        },
                    onClick = { onCastMemberClick(index, member) },
                )
            }
        }
        }
    }
}

/**
 * Vantage cast pill: a small round headshot beside the name and role on light glass. Focus turns
 * the pill white with the accent glow. [photoSize] is kept for callers; the pill sets its own size.
 */
@Composable
internal fun TvCastCard(
    member: CastMember,
    @Suppress("UNUSED_PARAMETER") photoSize: Dp,
    focusRequester: FocusRequester?,
    cardModifier: Modifier,
    onClick: () -> Unit = {},
) {
    val L = org.siloserver.silo.tv.ui.theme.Level
    val pill = org.siloserver.silo.tv.ui.theme.VantagePill
    val accent = org.siloserver.silo.tv.ui.theme.animatedAccent()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(11.dp),
        modifier = cardModifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .androidxDrawHalo(isFocused, accent)
            .background(if (isFocused) L.Fg else L.Glass, pill)
            .border(1.dp, if (isFocused) Color.Transparent else L.Edge, pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(start = 5.dp, end = 18.dp, top = 5.dp, bottom = 5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(pill)
                .background(DarkSurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            if (!member.photoUrl.isNullOrBlank()) {
                ThumbhashImage(
                    url = member.photoUrl,
                    thumbhash = member.photoThumbhash,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column {
            Text(
                text = member.name,
                style = org.siloserver.silo.tv.ui.components.vantage.ui(15.5, FontWeight.SemiBold, if (isFocused) L.Ink else L.Fg),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val role = member.character?.takeIf { it.isNotBlank() }
            if (role != null) {
                Text(
                    text = role,
                    style = org.siloserver.silo.tv.ui.components.vantage.mono(12, color = if (isFocused) L.Ink.copy(alpha = 0.7f) else L.Dim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun Modifier.androidxDrawHalo(on: Boolean, accent: Color): Modifier =
    if (!on) this else this.drawBehind {
        val grow = 3.dp.toPx()
        val r = size.height / 2 + grow
        drawRoundRect(
            accent.copy(alpha = 0.5f),
            topLeft = androidx.compose.ui.geometry.Offset(-grow, -grow),
            size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
    }
