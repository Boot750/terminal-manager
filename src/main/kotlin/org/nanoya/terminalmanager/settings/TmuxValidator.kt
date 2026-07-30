package org.nanoya.terminalmanager.settings

import java.util.concurrent.TimeUnit

/**
 * Validates a configured tmux binary by running `<binary> -V`. This confirms both that
 * the binary can be resolved/executed AND that it is actually tmux (not some other
 * program), returning the reported version.
 *
 * `-V` prints the version and exits immediately without starting a tmux server, so it
 * is safe to read its output. Calls MUST be made off the EDT. The last result is cached
 * (keyed by the binary string) so repeated launches don't re-spawn the process.
 */
object TmuxValidator {

    data class Result(val ok: Boolean, val version: String?, val error: String?)

    @Volatile
    private var cached: Pair<String, Result>? = null

    /** Runs `<binary> -V` (or returns the cached result for the same binary). Off-EDT only. */
    fun validate(binary: String): Result {
        val key = binary.trim()
        cached?.let { (k, r) -> if (k == key) return r }
        val result = runQuery(key)
        cached = key to result
        return result
    }

    fun invalidate() {
        cached = null
    }

    /**
     * Returns the number of clients attached to the tmux session named [sessionName], or
     * null if that couldn't be determined (no server, query failed). Callers use this to
     * tell a working tmux tab from a failed one without parsing the command. Off-EDT only;
     * not cached (the value changes over time).
     */
    fun sessionAttached(binary: String, sessionName: String): Int? {
        val resolved = ShellDetector.resolveTmuxBinary(binary) ?: return null
        var process: Process? = null
        return try {
            process = ProcessBuilder(
                resolved.absolutePath, "list-sessions", "-F", "#{session_name} #{session_attached}"
            ).redirectErrorStream(true).start()
            process.outputStream.close()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            val output = process.inputStream.bufferedReader().readText()
            // Exit is non-zero when there is no server yet; that means the session isn't
            // attached, so report 0 rather than "unknown".
            if (process.exitValue() != 0) return 0
            for (line in output.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                val idx = trimmed.lastIndexOf(' ')
                if (idx <= 0) continue
                val name = trimmed.substring(0, idx)
                if (name == sessionName) {
                    return trimmed.substring(idx + 1).trim().toIntOrNull() ?: 0
                }
            }
            0 // session not present -> not attached
        } catch (e: Exception) {
            process?.destroyForcibly()
            null
        }
    }

    private fun runQuery(binary: String): Result {
        val resolved = ShellDetector.resolveTmuxBinary(binary)
            ?: return Result(false, null, "tmux not found (checked the given path and PATH)")

        var process: Process? = null
        return try {
            process = ProcessBuilder(resolved.absolutePath, "-V")
                .redirectErrorStream(true)
                .start()
            process.outputStream.close() // no stdin
            val finished = process.waitFor(3, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(false, null, "timed out running \"${resolved.absolutePath} -V\"")
            }
            // Output is tiny and the process has exited, so this read won't block.
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.exitValue() == 0 && output.startsWith("tmux ")) {
                Result(true, output.removePrefix("tmux").trim().ifBlank { output }, null)
            } else {
                Result(false, null, output.ifBlank { "not a tmux binary (exit code ${process.exitValue()})" })
            }
        } catch (e: Exception) {
            process?.destroyForcibly()
            Result(false, null, e.message ?: "failed to run tmux")
        }
    }
}
