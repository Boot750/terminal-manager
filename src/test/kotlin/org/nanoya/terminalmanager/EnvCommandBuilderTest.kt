package org.nanoya.terminalmanager

import kotlin.test.Test
import kotlin.test.assertEquals

class EnvCommandBuilderTest {

    @Test
    fun `empty env yields no commands`() {
        assertEquals(emptyList(), TerminalTabHelper.buildEnvCommands(emptyMap(), "bash"))
    }

    @Test
    fun `posix shell uses export`() {
        assertEquals(listOf("export FOO=bar"), TerminalTabHelper.buildEnvCommands(linkedMapOf("FOO" to "bar"), "gitbash"))
    }

    @Test
    fun `cmd uses set`() {
        assertEquals(listOf("set \"FOO=bar\""), TerminalTabHelper.buildEnvCommands(linkedMapOf("FOO" to "bar"), "cmd"))
    }

    @Test
    fun `powershell uses dollar env assignment`() {
        assertEquals(listOf("\$env:FOO=\"bar\""), TerminalTabHelper.buildEnvCommands(linkedMapOf("FOO" to "bar"), "powershell"))
    }
}
