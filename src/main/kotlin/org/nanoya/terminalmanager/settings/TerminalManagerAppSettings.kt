package org.nanoya.terminalmanager.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings for Terminal Manager.
 * These settings are stored per-machine, not per-project.
 */
@Service(Service.Level.APP)
@State(
    name = "TerminalManagerAppSettings",
    storages = [Storage(value = "TerminalManagerAppSettings.xml", roamingType = RoamingType.DEFAULT)]
)
class TerminalManagerAppSettings : PersistentStateComponent<TerminalManagerAppSettings.State> {

    data class State(
        var lockTerminalNavigationToSplitPanel: Boolean = false,
        var gitignorePromptDismissed: Boolean = false,
        var tmuxBinary: String = DEFAULT_TMUX_BINARY,
        var tmuxCommand: String = DEFAULT_TMUX_COMMAND
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var lockTerminalNavigationToSplitPanel: Boolean
        get() = myState.lockTerminalNavigationToSplitPanel
        set(value) {
            myState.lockTerminalNavigationToSplitPanel = value
        }

    var gitignorePromptDismissed: Boolean
        get() = myState.gitignorePromptDismissed
        set(value) {
            myState.gitignorePromptDismissed = value
        }

    /**
     * Path to the tmux binary. Defaults to "tmux" (resolved via PATH).
     * A bare name is looked up in PATH; an absolute/relative path is used as-is.
     */
    var tmuxBinary: String
        get() = myState.tmuxBinary.ifBlank { DEFAULT_TMUX_BINARY }
        set(value) {
            myState.tmuxBinary = value.ifBlank { DEFAULT_TMUX_BINARY }
        }

    /**
     * The tmux command template used to launch tmux tabs — the arguments after the
     * tmux binary. Supported placeholders:
     *   {name}    the tmux session name (derived from the tab name)
     *   {dir}     the working directory
     *   {command} the shell command run on session creation (startup command + shell,
     *             or the selected shell); inserted as a single argument, omitted if empty
     * Tokens are separated by whitespace; a standalone ";" separates tmux commands.
     */
    var tmuxCommand: String
        get() = myState.tmuxCommand.ifBlank { DEFAULT_TMUX_COMMAND }
        set(value) {
            myState.tmuxCommand = value.ifBlank { DEFAULT_TMUX_COMMAND }
        }

    companion object {
        const val DEFAULT_TMUX_BINARY: String = "tmux"

        const val DEFAULT_TMUX_COMMAND: String =
            "new-session -A -d -s {name} -c {dir} {command} ; set -t {name} mouse on ; attach-session -t {name}"

        fun getInstance(): TerminalManagerAppSettings {
            return ApplicationManager.getApplication().getService(TerminalManagerAppSettings::class.java)
        }
    }
}
