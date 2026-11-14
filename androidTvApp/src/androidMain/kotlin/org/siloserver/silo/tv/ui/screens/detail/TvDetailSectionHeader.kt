package org.siloserver.silo.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.siloserver.silo.tv.ui.components.vantage.mono
import org.siloserver.silo.tv.ui.components.vantage.ui
import org.siloserver.silo.tv.ui.theme.Level
import org.siloserver.silo.tv.ui.theme.animatedAccent

/**
 * The section header used below the detail hero (Cast, Trailers & More, Episodes ...): the same
 * Vantage title as every row, an accent bar and plain text with a quiet note beside it, so a
 * title is never mistaken for a button.
 */
@Composable
internal fun TvDetailSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** A small tracked line above the title when it carries context the title doesn't ("Season 2"). */
    eyebrow: String? = null,
    /** A quiet mono note beside the title ("7 of 41"). */
    note: String? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!eyebrow.isNullOrBlank()) {
            Text(eyebrow.uppercase(), style = mono(11, FontWeight.SemiBold, Level.Dim, 0.16f), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.width(3.dp).height(18.dp).background(animatedAccent()))
            Text(title, style = ui(20, FontWeight.SemiBold), maxLines = 1)
            if (!note.isNullOrBlank()) Text(note, style = mono(11, FontWeight.Normal, Level.Faint), maxLines = 1)
        }
    }
}
