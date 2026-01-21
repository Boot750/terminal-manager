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
        var lockTerminalNavigationToSplitPanel: Boolean = false
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

    companion object {
        fun getInstance(): TerminalManagerAppSettings {
            return ApplicationManager.getApplication().getService(TerminalManagerAppSettings::class.java)
        }
    }
}
