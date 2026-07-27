package org.nanoya.terminalmanager.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdMigrationTest {

    @Test
    fun `tabs missing ids receive ids, existing ids untouched`() {
        var n = 0
        val tabs = mutableListOf(
            TerminalTabConfig(id = "keep", name = "A"),
            TerminalTabConfig(id = "", name = "B")
        )
        val changed = TerminalManagerSettings.assignMissingIds(tabs) { "gen-${n++}" }
        assertTrue(changed)
        assertEquals("keep", tabs[0].id)
        assertEquals("gen-0", tabs[1].id)
    }

    @Test
    fun `returns false when all tabs already have ids`() {
        val tabs = mutableListOf(TerminalTabConfig(id = "x", name = "A"))
        val changed = TerminalManagerSettings.assignMissingIds(tabs) { "should-not-be-used" }
        assertTrue(!changed)
    }
}
