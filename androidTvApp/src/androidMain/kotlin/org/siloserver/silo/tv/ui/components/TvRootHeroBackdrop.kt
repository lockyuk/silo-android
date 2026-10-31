package org.siloserver.silo.tv.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.MaterialTheme
import org.siloserver.silo.common.cards.LocalEpisodeSpoilerPrefs
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.model.section.SectionItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Page-level Skyline backdrop for tvOS-style root screens. Faithful Android
 * port of the reworked tvOS `TVRootHeroBackdrop` (§5.4): crisp (un-blurred)
 * artwork anchored in the top-RIGHT corner at ~0.64w × 0.70h, faded out toward
 * the leading edge and the bottom by a two-axis corner mask into a color
 * sampled from the art itself, which is carried (dimmed) diagonally
 * topEnd → bottomStart across the page so the metadata and rows sit on the same
 * tint. The shell owns the single shared top-navigation shadow so this artwork
 * does not double-darken Home relative to other root pages.
 *
 * Driven by whichever row card holds focus — pass that item's marquee
 * [content]; when null the page renders flat on the app background.
 */
@Composable
fun TvRootHeroBackdrop(
    content: TvMarqueeContent?,
    modifier: Modifier = Modifier,
    emptyWashColor: Color? = null,
    animateTransition: Boolean = true,
    /** The art and tint crossfade; Home's Continue Watching hero runs slower. */
    crossfadeMillis: Int = TvMarqueeCrossfadeMs,
    /** Where the art block reaches and fades; Home's hero takes a bigger one. */
    artFrame: TvHeroArtFrame = TvHeroArtFrame.Default,
) {
    val spoilerPrefs = LocalEpisodeSpoilerPrefs.current
    val tintState = LocalAmbientBackdropTint.current
    val ambientAccent = tintState.accent

    // tvOS first-frame parity: the FIRST artwork/tint to arrive snaps in with
    // no animation — a cold entry paints the finished hero instead of fading
    // it up from the black background. Only content-to-content swaps (normal
    // D-pad browsing) keep the ambient crossfade.
    var hasDisplayedArtwork by remember { mutableStateOf(false) }
    val snapInitialArtwork = content != null && !hasDisplayedArtwork
    LaunchedEffect(content != null) {
        if (content != null) hasDisplayedArtwork = true
    }
    var hasDisplayedTint by remember { mutableStateOf(false) }
    val snapInitialTint = ambientAccent != null && !hasDisplayedTint
    LaunchedEffect(ambientAccent != null) {
        if (ambientAccent != null) hasDisplayedTint = true
    }

    val targetAccent = ambientAccent ?: emptyWashColor ?: MaterialTheme.colorScheme.background
    // Kept as State and read only inside the Canvas draw lambda below: reading
    // the animating colour here would recompose this whole backdrop (and its
    // Crossfade subtree) on every frame of the 500ms tint tween.
    val animatedTint = animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(
            durationMillis = if (snapInitialTint) 0 else crossfadeMillis,
            easing = TvMarqueeEasing,
        ),
        label = "tvRootHeroBackdropTint",
    )

    val isVisible = content != null
    val hasTintOnlyWash = !isVisible && ambientAccent != null
    val hasEmptyWash = !isVisible && ambientAccent == null && emptyWashColor != null
    val leadingWashAlpha = when {
        isVisible -> 1.0f
        hasTintOnlyWash -> 0.34f
        hasEmptyWash -> 0.30f
        else -> 0.0f
    }
    val midWashAlpha = when {
        isVisible -> 0.5f
        hasTintOnlyWash -> 0.18f
        hasEmptyWash -> 0.15f
        else -> 0.0f
    }
    val trailingWashAlpha = when {
        isVisible -> 0.18f
        hasTintOnlyWash -> 0.08f
        hasEmptyWash -> 0.07f
        else -> 0.0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Diagonal sampled-tint wash: richest in the top-right behind the art,
        // carried dimmed to the bottom-left (tvOS stops 1.0 / 0.5 / 0.18).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val displayedTint = if (animateTransition) animatedTint.value else targetAccent
            drawRect(
                brush = Brush.linearGradient(
                    colorStops = smoothedWashStops(
                        tint = displayedTint,
                        leadingAlpha = leadingWashAlpha,
                        midAlpha = midWashAlpha,
                        trailingAlpha = trailingWashAlpha,
                    ),
                    start = Offset(size.width, 0f),
                    end = Offset(0f, size.height),
                ),
            )
        }

        if (hasTintOnlyWash || hasEmptyWash) {
            DitheredWashOverlay(
                alpha = if (hasEmptyWash) EmptyWashDitherAlpha else TintOnlyDitherAlpha,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (animateTransition) {
            // The new art fades in once it has decoded, over the old art held
            // at full strength; a plain Crossfade dimmed both halfway and
            // faded in an empty layer when the picture was still loading,
            // so the art then popped in.
            LoadGatedArtCrossfade(
                target = content,
                targetKey = content?.let { it.id to it.heroBackdropUrl },
                durationMillis = if (snapInitialArtwork) 0 else crossfadeMillis,
            ) { value, onSettled ->
                val art = when {
                    value == null -> null
                    // Protection turning on for the item still shown hides
                    // its still at once instead of fading the sharp one out.
                    content != null && value.id == content.id ->
                        if (content.hidesStills && !value.hidesStills) content else value
                    // A departing item follows the current preferences too.
                    else -> value.underPrefs(spoilerPrefs)
                }
                if (art?.heroBackdropUrl != null) {
                    CornerAnchoredArt(
                        url = art.heroBackdropUrl,
                        thumbhash = art.heroBackdropThumbhash,
                        frame = artFrame,
                        onSettled = onSettled,
                    )
                } else {
                    LaunchedEffect(Unit) { onSettled() }
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
        } else if (content?.heroBackdropUrl != null) {
            CornerAnchoredArt(
                url = content.heroBackdropUrl,
                thumbhash = content.heroBackdropThumbhash,
                frame = artFrame,
            )
        }

        if (artFrame.fullBleed && isVisible) FullBleedScrim()
    }
}

/**
 * Darkens full-screen art just enough for the copy on the left and the rows
 * along the bottom: plain black over the picture, never the grey tint wash, so
 * the art still shows between the cards.
 */
@Composable
private fun FullBleedScrim() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black.copy(alpha = 0.62f),
                    0.30f to Color.Black.copy(alpha = 0.38f),
                    0.60f to Color.Black.copy(alpha = 0.08f),
                    1.00f to Color.Transparent,
                ),
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black.copy(alpha = 0.30f),
                    0.16f to Color.Transparent,
                    0.50f to Color.Transparent,
                    1.00f to Color.Black.copy(alpha = 0.55f),
                ),
            ),
        )
    }
}

/**
 * Crisp art block pinned to the top-right corner, masked with a two-axis ramp:
 * opaque only at the top-right, fading to clear toward the leading edge
 * (horizontal ramp) and the bottom (vertical ramp). The two ramps multiply via
 * [BlendMode.DstIn] so the art dissolves into the sampled wash on its left and
 * below.
 */
@Composable
private fun CornerAnchoredArt(
    url: String?,
    thumbhash: String?,
    frame: TvHeroArtFrame = TvHeroArtFrame.Default,
    onSettled: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artWidth = maxWidth * frame.widthFraction
        val artHeight = maxHeight * frame.heightFraction

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(artWidth, artHeight)
                    .then(if (frame.fullBleed) Modifier else Modifier.cornerFadeMask(frame)),
            ) {
                ThumbhashImage(
                    url = url,
                    thumbhash = thumbhash,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    transparent = true,
                    // The Skyline feed preloads this frame and owns the one
                    // shared art + copy crossfade. A second Coil fade would
                    // make the image visibly lag behind the text.
                    crossfadeMillis = 0,
                    // Some episode stills carry the film's black bars; full
                    // screen they read as a black band under the menu.
                    cropLetterbox = frame.fullBleed,
                    onSuccess = onSettled,
                    onError = onSettled,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Fades the corner art block out toward its leading edge and its bottom. */
private fun Modifier.cornerFadeMask(frame: TvHeroArtFrame): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        // drawWithCache: the two mask brushes are built once per
        // size, not on every frame of a crossfade.
        .drawWithCache {
            // Horizontal ramp: opaque at trailing (right) edge,
            // clear toward the leading (left) edge.
            val horizontalMask = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    frame.horizontalOpaqueFrom to Color.Black,
                    1.0f to Color.Black,
                ),
                endX = size.width,
            )
            // Vertical ramp: opaque at the top, clear toward bottom.
            val verticalMask = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Black,
                    frame.verticalOpaqueUntil to Color.Black,
                    1.0f to Color.Transparent,
                ),
                endY = size.height,
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush = horizontalMask, blendMode = BlendMode.DstIn)
                drawRect(brush = verticalMask, blendMode = BlendMode.DstIn)
            }
        }

/**
 * Backward-compatible entry point for surfaces that still drive the backdrop
 * from a raw [SectionItem] (the library Browse landing, Sweep 3). Adapts the
 * item into the Skyline [TvMarqueeContent] payload and renders the corner-
 * anchored treatment.
 */
@Composable
fun TvRootHeroBackdrop(
    item: SectionItem?,
    modifier: Modifier = Modifier,
) {
    val spoilers = LocalEpisodeSpoilerPrefs.current
    // Keyed on the whole item: a watch-state change re-decides spoiler hiding.
    val content = remember(item, spoilers) {
        item?.let { TvMarqueeContent.from(it, rowTitle = "", spoilers = spoilers) }
    }
    TvRootHeroBackdrop(content = content, modifier = modifier)
}

/** One picture in [LoadGatedArtCrossfade]: shown once [alpha] rises. */
private class ArtLayer(val key: Any?, val value: TvMarqueeContent?, initialAlpha: Float) {
    val alpha = Animatable(initialAlpha)
    val settled = CompletableDeferred<Unit>()
    var job: Job? = null
}

/**
 * Crossfades backdrop art without dimming or pop-in. Each new picture waits
 * until it has decoded (or [ArtSettleTimeoutMillis] passes), then fades in
 * over the pictures under it, which stay at full strength until it is fully
 * in and then fade out. Pictures passed over before they were ready are
 * dropped unseen, so fast D-pad moves never stack fades.
 */
@Composable
private fun LoadGatedArtCrossfade(
    target: TvMarqueeContent?,
    targetKey: Any?,
    durationMillis: Int,
    layer: @Composable (value: TvMarqueeContent?, onSettled: () -> Unit) -> Unit,
) {
    val layers = remember { mutableStateListOf<ArtLayer>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(targetKey) {
        if (layers.lastOrNull()?.key == targetKey) return@LaunchedEffect
        // Waiting pictures never showed; drop them rather than fade them in late.
        layers.filter { it !== layers.first() && it.alpha.value == 0f }.forEach { unseen ->
            unseen.job?.cancel()
            layers.remove(unseen)
        }
        val instant = durationMillis == 0 || layers.isEmpty()
        val incoming = ArtLayer(targetKey, target, if (instant) 1f else 0f)
        layers.add(incoming)
        incoming.job = scope.launch {
            if (!instant) {
                withTimeoutOrNull(ArtSettleTimeoutMillis) { incoming.settled.await() }
                incoming.alpha.animateTo(1f, tween(durationMillis, easing = TvMarqueeEasing))
            }
            val covered = layers.takeWhile { it !== incoming }
            covered.forEach { it.job?.cancel() }
            covered.map { old ->
                launch { old.alpha.animateTo(0f, tween(durationMillis / 2, easing = TvMarqueeEasing)) }
            }.joinAll()
            layers.removeAll(covered)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        layers.forEach { artLayer ->
            key(artLayer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Read in the draw phase, so a fade never recomposes the art.
                        .graphicsLayer { alpha = artLayer.alpha.value },
                ) {
                    layer(if (artLayer.key == targetKey) target else artLayer.value) {
                        artLayer.settled.complete(Unit)
                    }
                }
            }
        }
    }
}

/** How long new art may take to decode before it fades in anyway. */
private const val ArtSettleTimeoutMillis = 1_000L

/**
 * The corner art block: its share of the screen, where the left-edge fade
 * turns opaque and where the bottom fade begins, each as a share of the block.
 */
data class TvHeroArtFrame(
    val widthFraction: Float,
    val heightFraction: Float,
    val horizontalOpaqueFrom: Float,
    val verticalOpaqueUntil: Float,
    /** The art covers the whole screen unmasked, under a black legibility scrim. */
    val fullBleed: Boolean = false,
) {
    companion object {
        /** Home: the picture fills the screen and shows between the rows' cards. */
        val FullScreen = TvHeroArtFrame(
            widthFraction = 1f,
            heightFraction = 1f,
            horizontalOpaqueFrom = 0f,
            verticalOpaqueUntil = 1f,
            fullBleed = true,
        )

        val Default = TvHeroArtFrame(
            widthFraction = 0.64f,
            heightFraction = 0.70f,
            horizontalOpaqueFrom = 0.68f,
            verticalOpaqueUntil = 0.58f,
        )
    }
}
private const val TintOnlyDitherAlpha = 0.28f
private const val EmptyWashDitherAlpha = 0.34f

private fun smoothedWashStops(
    tint: Color,
    leadingAlpha: Float,
    midAlpha: Float,
    trailingAlpha: Float,
): Array<Pair<Float, Color>> = arrayOf(
    0.00f to tint.copy(alpha = leadingAlpha),
    0.12f to tint.copy(alpha = lerpAlpha(leadingAlpha, midAlpha, 0.18f)),
    0.26f to tint.copy(alpha = lerpAlpha(leadingAlpha, midAlpha, 0.48f)),
    0.45f to tint.copy(alpha = midAlpha),
    0.62f to tint.copy(alpha = lerpAlpha(midAlpha, trailingAlpha, 0.36f)),
    0.80f to tint.copy(alpha = lerpAlpha(midAlpha, trailingAlpha, 0.68f)),
    1.00f to tint.copy(alpha = trailingAlpha),
)

private fun lerpAlpha(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

@Composable
private fun DitheredWashOverlay(
    alpha: Float,
    modifier: Modifier = Modifier,
) {
    val noise = remember { createBackdropNoiseBitmap() }
    Canvas(modifier = modifier) {
        val tileWidth = noise.width.toFloat()
        val tileHeight = noise.height.toFloat()
        var y = 0f
        while (y < size.height) {
            var x = 0f
            while (x < size.width) {
                drawImage(
                    image = noise,
                    topLeft = Offset(x, y),
                    alpha = alpha,
                )
                x += tileWidth
            }
            y += tileHeight
        }
    }
}

private fun createBackdropNoiseBitmap(): ImageBitmap {
    val dimension = 96
    val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
    val pixels = IntArray(dimension * dimension)
    var state = 0x4F1BBCDC

    for (index in pixels.indices) {
        state = state * 1664525 + 1013904223
        val sample = state ushr 24
        val pixelAlpha = when {
            sample < 16 -> 14
            sample < 42 -> 8
            else -> 0
        }
        pixels[index] = (pixelAlpha shl 24) or 0x00FFFFFF
    }

    bitmap.setPixels(pixels, 0, dimension, 0, 0, dimension, dimension)
    return bitmap.asImageBitmap()
}
