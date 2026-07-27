package org.nanoya.terminalmanager

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabOpenGateTest {

    @Test
    fun `blank openIf always opens`() {
        assertTrue(TabOpenGate.shouldOpen("", "/anything"))
        assertTrue(TabOpenGate.shouldOpen(".", "/anything"))
    }

    @Test
    fun `null project base path always opens`() {
        assertTrue(TabOpenGate.shouldOpen("docker-compose.yml", null))
    }

    @Test
    fun `relative path that exists opens`() {
        val dir = File.createTempFile("tmgr", "").let { it.delete(); it.mkdirs(); it }
        try {
            File(dir, "marker.txt").writeText("x")
            assertTrue(TabOpenGate.shouldOpen("marker.txt", dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `relative path that is missing does not open`() {
        val dir = File.createTempFile("tmgr", "").let { it.delete(); it.mkdirs(); it }
        try {
            assertFalse(TabOpenGate.shouldOpen("nope.txt", dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }
}
