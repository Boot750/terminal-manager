package org.nanoya.terminalmanager

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.nanoya.terminalmanager.settings.ShellDetector
import org.nanoya.terminalmanager.settings.TmuxValidator
import java.util.Collections

/**
 * Runtime support for tmux-backed terminal tabs. Turns the configurable command template
 * into a launch command, and — off the EDT — verifies that a tmux tab can and did start,
 * notifying the user when it can't.
 *
 * The low-level process calls live in [TmuxValidator]; this object adds command building,
 * launch-failure detection, and the user-facing notifications.
 */
object TmuxSupport {

    private const val NOTIFICATION_GROUP_ID = "Terminal Manager"

    // Poll for the tmux client to attach for ~2.5s. Success is detected as soon as it
    // attaches; the window only bounds how quickly a failure is reported (a generous
    // margin so a slow attach isn't misreported as a failure).
    private const val ATTACH_CHECK_INTERVAL_MS = 250L
    private const val ATTACH_CHECK_ATTEMPTS = 10

    /** Binaries already warned about, so we notify at most once per distinct bad binary. */
    private val notifiedBadBinaries = Collections.synchronizedSet(HashSet<String>())

    /**
     * Sanitizes a tab name into a valid tmux session name. tmux disallows '.' and ':' and
     * treats whitespace awkwardly, so those are replaced with underscores.
     */
    fun sessionName(tabName: String): String {
        val sanitized = tabName.trim().replace(Regex("[.:\\s]"), "_")
        return sanitized.ifBlank { "terminal" }
    }

    /**
     * Builds the tmux launch command from the configurable template. The template holds the
     * arguments after the tmux binary; placeholders are substituted per token:
     *   {name}    -> the tmux session name
     *   {dir}     -> the working directory
     *   {command} -> the shell command run on session creation, inserted as a single
     *                argument (omitted when empty)
     *
     * The default template is:
     * `new-session -A -d -s {name} -c {dir} {command} ; set -t {name} mouse on ; attach-session -t {name}`
     *
     * Why the default is shaped this way:
     * - `-d` creates the session detached so the trailing commands actually run before the
     *   (blocking) attach; a plain attaching `new-session` would run them too late.
     * - `mouse on` makes the wheel scroll tmux's scrollback. Do NOT switch it off: tmux runs
     *   on the alternate screen, and with mouse off IntelliJ's "alternate scroll" turns the
     *   wheel into Up/Down arrows (command-history navigation). Known issue:
     *   https://youtrack.jetbrains.com/issue/IJPL-103757
     * - The `{command}` (shell command on creation) is only honored by tmux when the session
     *   is *created*; `-A` ignores it on reattach. So a startup command runs once on creation
     *   (then `exec`s the shell) and is never re-run on reattach.
     */
    fun buildCommand(
        binary: String,
        template: String,
        sessionName: String,
        workingDir: String,
        innerShell: List<String>?,
        startupCommand: String?
    ): List<String> {
        // The shell command run in the pane on creation ({command} placeholder).
        val paneCommand: String? = when {
            !startupCommand.isNullOrBlank() -> {
                val shellExec = if (!innerShell.isNullOrEmpty()) innerShell.joinToString(" ")
                                else "\${SHELL:-/bin/sh}"
                "$startupCommand; exec $shellExec"
            }
            !innerShell.isNullOrEmpty() -> innerShell.joinToString(" ")
            else -> null
        }

        val result = mutableListOf(binary)
        for (token in template.trim().split(Regex("\\s+"))) {
            when (token) {
                "" -> {}
                "{name}" -> result.add(sessionName)
                "{dir}" -> result.add(workingDir)
                "{command}" -> if (!paneCommand.isNullOrBlank()) result.add(paneCommand)
                else -> result.add(token)
            }
        }
        return result
    }

    /**
     * Warns (off the EDT) if a tmux-enabled tab can't actually use tmux — the binary isn't
     * found or isn't really tmux (checked via `<binary> -V`). Notifies at most once per
     * distinct binary until it starts working again.
     */
    fun notifyIfBinaryUnusable(project: Project, binary: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = TmuxValidator.validate(binary)
            val key = binary.trim()
            if (result.ok) {
                notifiedBadBinaries.remove(key) // recovered — allow a future breakage to notify again
            } else if (notifiedBadBinaries.add(key)) {
                notify(
                    project,
                    "Terminal Manager: tmux unavailable",
                    "Could not use tmux for a terminal tab: ${result.error ?: "tmux is not available"} " +
                        "(binary: \"$binary\"). Check the tmux binary in Settings | Tools | Terminal Manager.",
                    NotificationType.WARNING
                )
            }
        }
    }

    /**
     * Verifies (off the EDT) that a launched tmux tab actually attached a client to
     * [sessionName]; if it never does, the command is likely invalid and we notify.
     * Skipped when the template targets a private socket (`-L`/`-S`, since our query
     * wouldn't reach that server) or doesn't reference `{name}` (we couldn't identify the
     * session created).
     */
    fun verifyLaunch(project: Project, binary: String, template: String, sessionName: String, tabName: String) {
        if (!template.contains("{name}") || usesPrivateSocket(template)) return

        ApplicationManager.getApplication().executeOnPooledThread {
            // Baseline client count BEFORE our tmux client can attach. A stale session with
            // the same name may already have clients attached (e.g. it persisted from a
            // previous run, or a restored tab re-attached), so we can't just check for
            // "attached >= 1" — that would mask a failure. Instead we require the count to
            // INCREASE, which only our successful attach produces. tmux's attach happens
            // after the process spawns, so this baseline read reliably precedes it.
            val baseline = TmuxValidator.sessionAttached(binary, sessionName)
            var everQueried = baseline != null
            val before = baseline ?: 0

            var attached = false
            for (i in 0 until ATTACH_CHECK_ATTEMPTS) {
                try {
                    Thread.sleep(ATTACH_CHECK_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@executeOnPooledThread
                }
                val count = TmuxValidator.sessionAttached(binary, sessionName)
                if (count != null) {
                    everQueried = true
                    if (count > before) {
                        attached = true
                        break
                    }
                }
            }

            // Only report a failure we're sure about: we could query tmux, and no client
            // ever attached. If tmux was never reachable, don't guess.
            if (!attached && everQueried) {
                notify(
                    project,
                    "Terminal Manager: tmux tab failed to open",
                    "The tmux terminal for tab \"$tabName\" did not start (no tmux client attached). " +
                        "The tmux command is likely invalid — check it in Settings | Tools | Terminal Manager.",
                    NotificationType.ERROR
                )
            }
        }
    }

    /** True if the template targets a private tmux socket (`-L name` / `-S path`). */
    private fun usesPrivateSocket(template: String): Boolean =
        template.split(Regex("\\s+")).any { it == "-L" || it == "-S" }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, type)
            .notify(project)
    }
}
