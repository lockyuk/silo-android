package org.siloserver.silo.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import org.siloserver.silo.tv.ui.components.TvDialogDefaults
import org.siloserver.silo.tv.ui.components.tvDialogSurface
import org.siloserver.silo.tv.ui.focus.TvContentInitialFocusMaxAttempts
import org.siloserver.silo.tv.ui.focus.claimFocusOrReport
import org.siloserver.silo.tv.ui.focus.requestFocusUntilObserved
import org.siloserver.silo.tv.ui.theme.DarkSurfaceElevated

/**
 * The hero's overview, clamped to [collapsedMaxLines] with a "Read more" pill
 * under it when the text is longer. The pill expands the text where it stands
 * (and the hero grows by [onExtraHeight] so the content below moves down); it
 * is the only focus stop, and the text itself shows no focus or selection.
 *
 * [pillFocusRequester] lets the page start focus on the pill, and
 * [onPillPresent] says whether there is one (short text has none).
 * [previewText] can include compact context (for example the focused episode
 * name) while [overview] remains the clean full text.
 */
@Composable
internal fun TvExpandableSynopsis(
    overview: String,
    tagline: String?,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
    dialogTitle: String? = null,
    previewText: String = overview,
    pillFocusRequester: FocusRequester? = null,
    onPillPresent: ((Boolean) -> Unit)? = null,
    onExtraHeight: ((androidx.compose.ui.unit.Dp) -> Unit)? = null,
) {
    require(collapsedMaxLines > 0) { "collapsedMaxLines must be positive" }

    // Vantage: the synopsis is always shown in full; there is no Read more / Show less.
    val expanded = true
    // Lines the whole text needs, measured with no height limit so the answer does not depend on
    // how much room the hero happens to have (a height-limited measure made the pill flicker).
    var fullLines by remember(previewText) { mutableStateOf(0) }
    val lineHeight = 18.dp
    val overflows = fullLines > collapsedMaxLines
    val showPill = false

    LaunchedEffect(showPill) { onPillPresent?.invoke(showPill) }
    LaunchedEffect(expanded, fullLines) {
        onExtraHeight?.invoke(if (expanded && overflows) lineHeight * (fullLines - collapsedMaxLines) else 0.dp)
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            onPillPresent?.invoke(false)
            onExtraHeight?.invoke(0.dp)
        }
    }

    Column(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            // Invisible, zero-size copy used only to count lines.
            Box(
                Modifier.layout { measurable, constraints ->
                    measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                    layout(0, 0) {}
                },
            ) {
                Text(
                    text = previewText,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.95.sp,
                    lineHeight = 20.9.sp,
                    onTextLayout = { fullLines = it.lineCount },
                )
            }
            Text(
                text = previewText,
                fontWeight = FontWeight.Normal,
                fontSize = 12.2.sp,
                lineHeight = 18.3.sp,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Start,
                maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showPill) {
            TvReadMorePill(
                expanded = expanded,
                onClick = {},
                focusRequester = pillFocusRequester,
            )
        }
    }
}

/**
 * Set by a page whose scrolling follows focus: called with a pixel amount (negative is up) when
 * Up / Down is pressed on the pill and there is nothing further to move focus to, so an
 * expanded synopsis can still be scrolled past.
 */
internal val LocalTvPageScrollBy = androidx.compose.runtime.staticCompositionLocalOf<((Float) -> Unit)?> { null }

/** A "Read more" / "Show less" link drawn as a pill. White when focused. */
@Composable
internal fun TvReadMorePill(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val pill = RoundedCornerShape(50)
    val scrollBy = LocalTvPageScrollBy.current
    Box(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                // Nothing is above the pill, so Up scrolls the page back to the top.
                if (scrollBy != null && event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                    scrollBy(-360f)
                    true
                } else {
                    false
                }
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .background(
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.14f),
                shape = pill,
            )
            .border(1.dp, Color.White.copy(alpha = if (isFocused) 0f else 0.35f), pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text = if (expanded) "Show less" else "Read more",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) Color.Black else Color.White,
        )
    }
}
