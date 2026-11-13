package org.siloserver.silo.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import org.siloserver.silo.common.ui.components.DefaultArtworkKind
import org.siloserver.silo.common.ui.components.SpoilerImage
import org.siloserver.silo.model.catalog.MediaItemUserState
import org.siloserver.silo.tv.ui.theme.Level
import org.siloserver.silo.tv.ui.theme.LevelChamfer
import org.siloserver.silo.tv.ui.theme.LevelDisplayFamily
import org.siloserver.silo.tv.ui.theme.LevelMonoFamily

/** Height of a shelf spine, and its width at rest and when focused (the focused spine opens to show its artwork). */
internal val TvSpineHeight = 186.dp
internal val TvSpineWidth = 40.dp
internal val TvSpineWidthFocused = 150.dp

/**
 * A library item as a spine on a shelf. At rest it is a narrow coloured spine with the title running
 * down it and a level tube at the foot showing how much has been watched; focused, it opens to a
 * 150 dp card with the artwork and a taller tube. [showTitle] turns the vertical title off.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvSpineCard(
    title: String,
    imageUrl: String?,
    imageThumbhash: String?,
    hideImage: Boolean,
    mediaType: String?,
    progress: Float?,
    quality: String?,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    cardModifier: Modifier,
    userState: MediaItemUserState?,
    actions: TvMediaCardActions,
    contentKey: Any?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var menuExpanded by remember(contentKey) { mutableStateOf(false) }
    val hue = remember(contentKey) { (((contentKey?.hashCode() ?: 0) % 360 + 360) % 360).toFloat() }
    val spine = Level.spine(hue)
    val width = if (isFocused) TvSpineWidthFocused else TvSpineWidth

    Column(modifier = modifier) {
        TvMediaCardContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            actions = actions,
            isPlayed = userState?.played == true,
            isFavorite = userState?.isFavorite == true,
            isInWatchlist = userState?.inWatchlist == true,
        )
        Card(
            onClick = onClick,
            onLongClick = onLongClick ?: if (actions.isEmpty) null else { { menuExpanded = true } },
            interactionSource = interactionSource,
            shape = CardDefaults.shape(shape = LevelChamfer),
            scale = CardDefaults.scale(scale = 1f, focusedScale = 1f),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(2.5.dp, Level.Accent), inset = 0.dp, shape = LevelChamfer),
            ),
            colors = CardDefaults.colors(containerColor = spine, focusedContainerColor = spine),
            modifier = cardModifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .width(width)
                .height(TvSpineHeight),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isFocused) {
                    SpoilerImage(
                        url = imageUrl,
                        thumbhash = imageThumbhash,
                        hidden = hideImage,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        defaultArtwork = DefaultArtworkKind.forItemType(mediaType),
                    )
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Transparent, 1f to Color(0x99000000)),
                        ),
                    )
                } else if (showTitle) {
                    // The title runs down the spine (rotated a quarter turn clockwise).
                    Text(
                        text = title.uppercase(),
                        color = Level.Fg,
                        fontFamily = LevelDisplayFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset(x = 28.dp, y = 12.dp)
                            .requiredWidth(144.dp)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0f)
                                rotationZ = 90f
                            },
                    )
                }
                if (quality != null) {
                    Text(
                        text = quality,
                        color = if (isFocused) Color.White else Level.Fg,
                        fontFamily = LevelMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 8.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 8.dp),
                    )
                }
                // The level tube: how much of the item has been watched.
                val tubeHeight = if (isFocused) 60.dp else 40.dp
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 5.dp, bottom = 8.dp)
                        .size(width = 4.dp, height = tubeHeight)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(2.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val fill = (progress ?: 0f).coerceIn(0f, 1f)
                    if (fill > 0f) {
                        Box(Modifier.fillMaxWidth().fillMaxHeight(fill).background(Level.Accent, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}

/** Short quality mark for a spine: 4K or HD from the item's overlay resolution. */
internal fun spineQuality(resolution: String?): String? {
    val r = resolution?.lowercase() ?: return null
    return when {
        "2160" in r || "4k" in r -> "4K"
        "1080" in r || "720" in r || "hd" in r -> "HD"
        r.isBlank() -> null
        else -> resolution.uppercase()
    }
}
