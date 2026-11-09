package org.siloserver.silo.android.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushRichContentTest {
    @Test fun blankOrMissingOverviewYieldsNothing() {
        assertNull(trimSynopsis(null))
        assertNull(trimSynopsis("   \n\t "))
    }

    @Test fun shortOverviewIsKeptWithWhitespaceCollapsed() {
        assertEquals("A short plot.", trimSynopsis("  A  short\nplot.  "))
    }

    @Test fun longOverviewIsCutAtAWordBoundaryWithEllipsis() {
        val long = "word ".repeat(200)
        val out = trimSynopsis(long)!!
        assertTrue(out.endsWith("…"))
        assertTrue(out.length <= 401)
        assertTrue(out.dropLast(1).endsWith("word"))
    }
}
