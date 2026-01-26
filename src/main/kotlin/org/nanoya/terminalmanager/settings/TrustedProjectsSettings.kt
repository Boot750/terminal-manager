package org.nanoya.terminalmanager.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

/**
 * Application-level service that stores which projects are trusted to run startup commands.
 * Stored in IDE config directory, not synced across machines (RoamingType.DISABLED).
 */
@Service(Service.Level.APP)
@State(
    name = "TerminalManagerTrustedProjects",
    storages = [Storage(value = "TerminalManagerTrustedProjects.xml", roamingType = RoamingType.DISABLED)]
)
class TrustedProjectsSettings : PersistentStateComponent<TrustedProjectsSettings.State> {

    data class State(
        var trustedProjectPaths: MutableSet<String> = mutableSetOf()
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun isProjectTrusted(project: Project): Boolean {
        val projectPath = project.basePath ?: return false
        return myState.trustedProjectPaths.contains(normalizeProjectPath(projectPath))
    }

    fun trustProject(project: Project) {
        val projectPath = project.basePath ?: return
        myState.trustedProjectPaths.add(normalizeProjectPath(projectPath))
    }

    fun untrustProject(project: Project) {
        val projectPath = project.basePath ?: return
        myState.trustedProjectPaths.remove(normalizeProjectPath(projectPath))
    }

    private fun normalizeProjectPath(path: String): String {
        // Normalize path separators and remove trailing slashes for consistent comparison
        return path.replace("\\", "/").trimEnd('/')
    }

    companion object {
        fun getInstance(): TrustedProjectsSettings {
            return ApplicationManager.getApplication().getService(TrustedProjectsSettings::class.java)
        }
    }
}
