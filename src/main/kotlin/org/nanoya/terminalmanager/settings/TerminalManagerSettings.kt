package org.nanoya.terminalmanager.settings

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class TerminalManagerSettings(private val project: Project) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    var enabled: Boolean = true
    var closeExistingTerminals: Boolean = false
    var tabs: MutableList<TerminalTabConfig> = mutableListOf()

    init {
        load()
    }

    private fun getConfigDir(): File {
        val basePath = project.basePath ?: return File(System.getProperty("user.home"), ".terminals")
        return File(basePath, ".terminals")
    }

    private fun getConfigFile(): File {
        return File(getConfigDir(), "startup-terminals.json")
    }

    fun load() {
        val configFile = getConfigFile()
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                val config = gson.fromJson(json, TerminalManagerConfig::class.java)
                enabled = config.enabled
                closeExistingTerminals = config.closeExistingTerminals
                tabs = config.tabs.toMutableList()
            } catch (e: Exception) {
                enabled = true
                closeExistingTerminals = false
                tabs = mutableListOf()
            }
        }
    }

    fun save() {
        val configDir = getConfigDir()
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        val config = TerminalManagerConfig(enabled, closeExistingTerminals, tabs.toList())
        val json = gson.toJson(config)
        getConfigFile().writeText(json)
    }

    companion object {
        fun getInstance(project: Project): TerminalManagerSettings {
            return project.service<TerminalManagerSettings>()
        }
    }
}

data class TerminalManagerConfig(
    val enabled: Boolean = true,
    val closeExistingTerminals: Boolean = false,
    val tabs: List<TerminalTabConfig> = emptyList()
)
