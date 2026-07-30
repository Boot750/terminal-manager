package org.nanoya.terminalmanager.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigMergerTest {

    private fun baseTab(id: String, name: String) =
        TerminalTabConfig(id = id, name = name, shellId = "default")

    @Test
    fun `no local config returns base tabs as SHARED`() {
        val base = TerminalManagerConfig(tabs = listOf(baseTab("a", "Build")))
        val eff = ConfigMerger.mergeEffective(base, null)
        assertEquals(1, eff.size)
        assertEquals(TabSource.SHARED, eff[0].source)
        assertEquals("Build", eff[0].config.name)
    }

    @Test
    fun `override matched by targetId replaces only non-null fields`() {
        val base = TerminalManagerConfig(tabs = listOf(baseTab("a", "Build")))
        val local = LocalTerminalManagerConfig(
            tabs = listOf(LocalTerminalTabOverride(targetId = "a", shellId = "pwsh"))
        )
        val eff = ConfigMerger.mergeEffective(base, local)
        assertEquals(TabSource.OVERRIDDEN, eff[0].source)
        assertEquals("pwsh", eff[0].config.shellId)
        assertEquals("Build", eff[0].config.name) // name untouched
    }

    @Test
    fun `legacy override without targetId matches by name`() {
        val base = TerminalManagerConfig(tabs = listOf(baseTab("a", "Build")))
        val local = LocalTerminalManagerConfig(
            tabs = listOf(LocalTerminalTabOverride(name = "Build", workingDirectory = "sub"))
        )
        val eff = ConfigMerger.mergeEffective(base, local)
        assertEquals(TabSource.OVERRIDDEN, eff[0].source)
        assertEquals("sub", eff[0].config.workingDirectory)
    }

    @Test
    fun `muted override drops the base tab from effective`() {
        val base = TerminalManagerConfig(tabs = listOf(baseTab("a", "Build"), baseTab("b", "Test")))
        val local = LocalTerminalManagerConfig(
            tabs = listOf(LocalTerminalTabOverride(targetId = "a", muted = true))
        )
        val eff = ConfigMerger.mergeEffective(base, local)
        assertEquals(1, eff.size)
        assertEquals("Test", eff[0].config.name)
    }

    @Test
    fun `personal tab is appended as PERSONAL`() {
        val base = TerminalManagerConfig(tabs = listOf(baseTab("a", "Build")))
        val local = LocalTerminalManagerConfig(
            tabs = listOf(LocalTerminalTabOverride(id = "p1", name = "Scratch", isPersonal = true, shellId = "cmd"))
        )
        val eff = ConfigMerger.mergeEffective(base, local)
        assertEquals(2, eff.size)
        assertEquals(TabSource.PERSONAL, eff[1].source)
        assertEquals("Scratch", eff[1].config.name)
        assertEquals("cmd", eff[1].config.shellId)
        assertEquals("p1", eff[1].config.id)
    }

    @Test
    fun `override targeting missing base tab is ORPHANED and excluded from runtime config`() {
        val base = TerminalManagerConfig(tabs = listOf(baseTab("a", "Build")))
        val local = LocalTerminalManagerConfig(
            tabs = listOf(LocalTerminalTabOverride(targetId = "gone", name = "Ghost"))
        )
        val eff = ConfigMerger.mergeEffective(base, local)
        assertTrue(eff.any { it.source == TabSource.ORPHANED && it.config.name == "Ghost" })
        val runtime = ConfigMerger.toRuntimeConfig(base, local)
        assertTrue(runtime.tabs.none { it.name == "Ghost" })
    }

    @Test
    fun `env merges per key with local winning`() {
        val base = TerminalManagerConfig(
            tabs = listOf(baseTab("a", "Build").apply { env = linkedMapOf("FOO" to "1", "BAR" to "2") })
        )
        val local = LocalTerminalManagerConfig(
            tabs = listOf(LocalTerminalTabOverride(targetId = "a", env = mapOf("BAR" to "9", "BAZ" to "3")))
        )
        val merged = ConfigMerger.mergeEffective(base, local)[0].config.env
        assertEquals("1", merged["FOO"])
        assertEquals("9", merged["BAR"])
        assertEquals("3", merged["BAZ"])
    }

    @Test
    fun `scalar overrides inherit on null`() {
        val base = TerminalManagerConfig(enabled = true, closeExistingTerminals = false)
        val local = LocalTerminalManagerConfig(closeExistingTerminals = true)
        val runtime = ConfigMerger.toRuntimeConfig(base, local)
        assertEquals(true, runtime.enabled)              // inherited
        assertEquals(true, runtime.closeExistingTerminals) // overridden
    }

    @Test
    fun `isLocalEmpty true for null and all-empty local config`() {
        assertTrue(ConfigMerger.isLocalEmpty(null))
        assertTrue(ConfigMerger.isLocalEmpty(LocalTerminalManagerConfig()))
        assertTrue(ConfigMerger.isLocalEmpty(LocalTerminalManagerConfig(tabs = emptyList())))
        assertTrue(!ConfigMerger.isLocalEmpty(LocalTerminalManagerConfig(enabled = true)))
        assertTrue(!ConfigMerger.isLocalEmpty(LocalTerminalManagerConfig(tabs = listOf(LocalTerminalTabOverride(targetId = "a")))))
    }
}
