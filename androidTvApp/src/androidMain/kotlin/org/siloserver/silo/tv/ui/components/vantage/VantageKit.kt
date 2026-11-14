package org.siloserver.silo.tv.ui.components.vantage

import org.siloserver.silo.tv.ui.components.gradientScrim
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.tv.ui.theme.Level
import org.siloserver.silo.tv.ui.theme.VantageCard
import org.siloserver.silo.tv.ui.theme.VantageMonoFamily
import org.siloserver.silo.tv.ui.theme.VantagePanel
import org.siloserver.silo.tv.ui.theme.VantagePill
import org.siloserver.silo.tv.ui.theme.VantageUiFamily
import org.siloserver.silo.tv.ui.theme.animatedAccent

/*
 * The Vantage kit: the mock-up's building blocks (glass panels, chips, pill buttons, rings,
 * bars, line charts, event lines) drawn at the mock-up's 1280x720 proportions on the
 * 960x540dp TV canvas. Mock-up pixels map to dp at x0.75 and to sp at x0.87 (the TV theme
 * scales text by 0.86).
 */

/** Mock-up pixels to dp. */
val Int.mx: Dp get() = (this * 0.75f).dp
val Double.mx: Dp get() = (this * 0.75f).dp

/** Mock-up font pixels to sp. */
val Int.fx: TextUnit get() = (this * 0.872f).sp
val Double.fx: TextUnit get() = (this * 0.872f).sp

fun ui(px: Number, weight: FontWeight = FontWeight.Medium, color: Color = Level.Fg) =
    TextStyle(fontFamily = VantageUiFamily, fontWeight = weight, fontSize = (px.toFloat() * 0.872f).sp, color = color)

fun mono(px: Number, weight: FontWeight = FontWeight.Medium, color: Color = Level.Fg, tracking: Float = 0f): TextStyle {
    val size = px.toFloat() * 0.872f
    return TextStyle(
        fontFamily = VantageMonoFamily, fontWeight = weight, fontSize = size.sp, color = color,
        letterSpacing = (size * tracking).sp,
    )
}

/** The small uppercase mono label (.lbl). */
@Composable
fun VLabel(text: String, color: Color = Level.Faint, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = mono(12.5, FontWeight.SemiBold, color = color, tracking = 0.12f), maxLines = 1, modifier = modifier)
}

/** The accent kicker above a page title (.kick). */
@Composable
fun VKicker(text: String, color: Color = animatedAccent()) {
    Text(text.uppercase(), style = mono(11, color = color, tracking = 0.16f), maxLines = 1)
}

/** The wide-tracked tagline above a logo (.tag). */
@Composable
fun VTag(text: String, size: Number = 11) {
    Text(text.uppercase(), style = mono(size, color = Color(0xFFE6EAF5).copy(alpha = 0.85f), tracking = 0.3f), maxLines = 1)
}

enum class ChipTone { Plain, Good, Warn, Bad, Solid }

/** A pill chip (.chip). */
@Composable
fun VChip(
    text: String,
    tone: ChipTone = ChipTone.Plain,
    dot: Boolean = false,
    bold: String? = null,
    size: Number = 11,
    modifier: Modifier = Modifier,
) {
    val fg = when (tone) {
        ChipTone.Plain -> Level.Dim
        ChipTone.Good -> Level.Good
        ChipTone.Warn -> Level.Warn
        ChipTone.Bad -> Level.Bad
        ChipTone.Solid -> Level.Ink
    }
    val bg = if (tone == ChipTone.Solid) Level.Fg else Level.Glass2
    val edge = when (tone) {
        ChipTone.Plain -> Level.Edge
        ChipTone.Solid -> Color.Transparent
        else -> fg.copy(alpha = 0.4f)
    }
    // Vantage: an info chip is quiet text with a thin line on its left, so it is plainly not a button.
    val line = when (tone) {
        ChipTone.Plain -> Color.White.copy(alpha = 0.35f)
        ChipTone.Solid -> Level.Fg
        else -> fg.copy(alpha = 0.8f)
    }
    val textTone = if (tone == ChipTone.Solid) Level.Fg else fg
    Row(
        modifier = modifier
            .drawBehind { drawRect(line, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), this.size.height)) }
            .padding(start = 9.mx, top = 1.mx, bottom = 1.mx, end = 2.mx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.mx),
    ) {
        if (dot) VDot(fg)
        if (bold != null) Text(bold, style = mono(size, color = Level.Fg, tracking = 0.05f), maxLines = 1)
        Text(text, style = mono(size, if (tone == ChipTone.Solid) FontWeight.SemiBold else FontWeight.Medium, textTone, 0.05f), maxLines = 1)
    }
}

/** The glowing status dot. */
@Composable
fun VDot(color: Color, size: Dp = 5.dp) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                val halo = this.size.minDimension * 0.6f
                drawRect(color.copy(alpha = 0.35f), Offset(-halo, -halo), androidx.compose.ui.geometry.Size(this.size.width + halo * 2, this.size.height + halo * 2))
                drawRect(color)
            },
    )
}

/** A glass panel (.gl.card) with an optional section header. */
@Composable
fun VPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    sub: String? = null,
    subColor: Color = Level.Faint,
    edge: Color = Level.Edge,
    padding: PaddingValues = PaddingValues(horizontal = 18.mx, vertical = 16.mx),
    gap: Dp = 10.mx,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(Level.Glass, VantagePanel)
            .border(1.dp, edge, VantagePanel)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (title != null) VSection(title, sub, subColor)
        content()
    }
}

/** A section header: title plus mono note (.sec). */
@Composable
fun VSection(title: String, sub: String? = null, subColor: Color = Level.Faint) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.mx)) {
        Text(title, style = ui(17, FontWeight.SemiBold), maxLines = 1)
        if (sub != null) Text(sub, style = mono(11, FontWeight.Normal, subColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A progress bar (.bar). */
@Composable
fun VBar(fraction: Float, color: Color = animatedAccent(), height: Dp = 5.mx, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .background(Level.Track),
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).clip(androidx.compose.ui.graphics.RectangleShape).background(color))
    }
}

/** A gauge ring with the value in the middle (.ringw). */
@Composable
fun VRing(percent: Int, color: Color, label: String, size: Dp = 84.mx) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.mx)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.mx.toPx()
                val inset = stroke / 2 + 2.mx.toPx()
                val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(color, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text("$percent", style = ui(19, FontWeight.SemiBold))
        }
        VLabel(label)
    }
}

/** A line chart with a soft fill under it. */
@Composable
fun VLineChart(values: List<Float>, color: Color = animatedAccent(), modifier: Modifier = Modifier, maxOverride: Float? = null) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(Color.White.copy(alpha = 0.1f), Offset(0f, y), Offset(w, y), 1f)
        }
        if (values.size < 2) return@Canvas
        val max = (maxOverride ?: values.max()).coerceAtLeast(0.0001f)
        // Light 1-2-1 smoothing so 500 ms samples don't zig-zag, then a smooth curve through the points.
        val smooth = values.mapIndexed { i, v ->
            val l = values[(i - 1).coerceAtLeast(0)]; val r = values[(i + 1).coerceAtMost(values.size - 1)]
            (l + 2f * v + r) / 4f
        }
        val pts = smooth.mapIndexed { i, v -> Offset(w * i / (smooth.size - 1), h - 2f - (v / max).coerceIn(0f, 1f) * (h - 6f)) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 0 until pts.size - 1) {
                val p0 = pts[(i - 1).coerceAtLeast(0)]; val p1 = pts[i]; val p2 = pts[i + 1]; val p3 = pts[(i + 2).coerceAtMost(pts.size - 1)]
                val lo = minOf(p1.y, p2.y); val hi = maxOf(p1.y, p2.y)
                val c1y = (p1.y + (p2.y - p0.y) / 6f).coerceIn(lo, hi)
                val c2y = (p2.y - (p3.y - p1.y) / 6f).coerceIn(lo, hi)
                cubicTo(p1.x + (p2.x - p0.x) / 6f, c1y, p2.x - (p3.x - p1.x) / 6f, c2y, p2.x, p2.y)
            }
        }
        val fill = Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.5f), color.copy(alpha = 0f))))
        drawPath(line, color, style = Stroke(2.5.mx.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A big number with a small mono suffix (.big). */
@Composable
fun VBig(value: String, suffix: String? = null, px: Number = 40, color: Color = Level.Fg) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = ui(px, FontWeight.SemiBold, color), maxLines = 1)
        if (suffix != null) Text(" $suffix", style = mono(12, FontWeight.Normal, Level.Dim), maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
    }
}

/** A dotted event line: dot, text, right-hand note (hl()). */
@Composable
fun VEventLine(text: String, color: Color, right: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.mx)) {
        VDot(color)
        Text(text, style = mono(12, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(right, style = mono(12, color = color), maxLines = 1)
    }
}

/** A backdrop thumbnail (.cd) with an optional progress bar and new-dot; no text on it. */
@Composable
fun VArt(
    url: String?,
    width: Dp,
    height: Dp,
    progress: Float? = null,
    isNew: Boolean = false,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = VantageCard,
) {
    Box(
        modifier
            .size(width, height)
            .clip(shape)
            .background(Color(0xFF151824))
            .border(1.dp, Level.ImageEdge, shape),
    ) {
        if (url != null) {
            ThumbhashImage(url = url, thumbhash = null, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
        if (progress != null && progress > 0f) {
            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 7.mx, vertical = 6.mx).fillMaxWidth().height(4.mx).clip(RoundedCornerShape(2.dp)).background(Color.Black.copy(alpha = 0.55f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(animatedAccent()))
            }
        }
        if (isNew) {
            Box(Modifier.align(Alignment.TopEnd).padding(7.mx)) { VDot(Level.Warn, 7.mx) }
        }
    }
}

/**
 * A focusable pill button (.btn / .btn.p). Primary is white with the accent halo; secondary is
 * light glass that turns white with the halo when focused.
 */
@Composable
fun VButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    small: String? = null,
    leading: (@Composable (Color) -> Unit)? = null,
    height: Dp = 40.mx,
    textPx: Number = 16,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val accent = animatedAccent()
    val lit = primary || focused
    val fg = if (lit) Level.Ink else Level.Fg
    val s by animateFloatAsState(if (focused) 1.05f else 1f, label = "vbtn")
    Row(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(s)
            .drawBehind {
                if (focused || primary) {
                    val grow = (if (focused) 5.mx else 4.mx).toPx()
                    val r = size.height / 2 + grow
                    drawRoundRect(
                        accent.copy(alpha = if (focused) 0.55f else 0.3f),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2, size.height + grow * 2),
                        cornerRadius = CornerRadius(org.siloserver.silo.tv.ui.theme.haloRadius(r), org.siloserver.silo.tv.ui.theme.haloRadius(r)),
                    )
                }
            }
            .background(if (lit) Level.Fg else Color.White.copy(alpha = 0.10f), VantagePill)
            .border(1.5.dp, if (lit) Color.Transparent else Color.White.copy(alpha = 0.38f), VantagePill)
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp && (it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter)) {
                    onClick(); true
                } else false
            }
            .focusable(interactionSource = source)
            .height(height)
            .padding(horizontal = 22.mx),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.mx),
    ) {
        leading?.invoke(fg)
        Text(text, style = ui(textPx, FontWeight.SemiBold, fg), maxLines = 1)
        if (small != null) Text(small, style = mono(11, color = fg.copy(alpha = 0.65f)), maxLines = 1)
    }
}

/** A focusable chip used as a tab (sub-tabs on the Server page). */
@Composable
fun VTabChip(
    text: String,
    selected: Boolean,
    onFocus: () -> Unit,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val accent = animatedAccent()
    androidx.compose.runtime.LaunchedEffect(focused) { if (focused) onFocus() }
    val lit = selected || focused
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .drawBehind {
                if (focused) {
                    val grow = 3.mx.toPx()
                    val r = size.height / 2 + grow
                    drawRoundRect(accent.copy(alpha = 0.55f), Offset(-grow, -grow), Size(size.width + grow * 2, size.height + grow * 2), CornerRadius(org.siloserver.silo.tv.ui.theme.haloRadius(r), org.siloserver.silo.tv.ui.theme.haloRadius(r)))
                }
            }
            .background(if (lit) Level.Fg else Color.White.copy(alpha = 0.10f), VantagePill)
            .border(1.5.dp, if (lit) Color.Transparent else Color.White.copy(alpha = 0.38f), VantagePill)
            .focusable(interactionSource = source)
            .padding(horizontal = 14.mx, vertical = 8.mx),
    ) {
        Text(text, style = mono(12, if (lit) FontWeight.SemiBold else FontWeight.Medium, if (lit) Level.Ink else Level.Dim, 0.05f), maxLines = 1)
    }
}

/** A list row: art, title, mono subtitle, optional bar, right note (lrow()). */
@Composable
fun VListRow(
    title: String,
    sub: String,
    right: String,
    imageUrl: String?,
    progress: Float? = null,
    divider: Boolean = true,
    artWidth: Dp = 96.mx,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.mx)) {
            VArt(imageUrl, artWidth, artWidth * 0.5625f)
            Column(Modifier.weight(1f)) {
                Text(title, style = ui(14, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, style = mono(11, color = Level.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.mx))
                if (progress != null) VBar(progress, height = 4.mx, modifier = Modifier.padding(top = 7.mx))
            }
            Text(right, style = mono(11, color = Level.Dim), maxLines = 1)
        }
        if (divider) {
            Spacer(Modifier.height(10.mx))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Level.Edge))
        }
    }
}

/** A stat tile (stat()). */
@Composable
fun VStat(label: String, value: String, suffix: String, color: Color = Level.Fg, modifier: Modifier = Modifier) {
    Column(
        modifier
            .height(86.mx)
            .background(Level.Glass, VantagePanel)
            .border(1.dp, Level.Edge, VantagePanel)
            .padding(horizontal = 16.mx, vertical = 12.mx),
        verticalArrangement = Arrangement.Center,
    ) {
        VLabel(label)
        VBig(value, suffix, 32, color)
    }
}

/** The bottom-left to top-right page scrims used over full-bleed backdrops. */
@Composable
fun Modifier.vantageHeroScrim(): Modifier = this
    .gradientScrim(true, 0f to Color(0xEB07080C), 0.38f to Color(0x8C07080C), 0.68f to Color(0x0007080C))
    .gradientScrim(false, 0.45f to Color(0x0007080C), 0.70f to Color(0x9907080C), 1f to Color(0xFF07080C))

@Composable
fun RowScope.VFill() = Spacer(Modifier.weight(1f))
