package org.nanoya.terminalmanager.settings

import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class TerminalManagerSettings(private val project: Project) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    var enabled: Boolean = true
    var closeExistingTerminals: Boolean = false
    var skipResetConfirmation: Boolean = false
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

    fun getLocalConfigFile(): File {
        return File(getConfigDir(), "startup-terminals.local.json")
    }

    fun hasLocalOverrides(): Boolean {
        return getLocalConfigFile().exists()
    }

    fun load() {
        val configFile = getConfigFile()
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                val config = gson.fromJson(json, TerminalManagerConfig::class.java)
                enabled = config.enabled
                closeExistingTerminals = config.closeExistingTerminals
                skipResetConfirmation = config.skipResetConfirmation
                tabs = config.tabs.toMutableList()
            } catch (e: Exception) {
                enabled = true
                closeExistingTerminals = false
                skipResetConfirmation = false
                tabs = mutableListOf()
            }
        }
    }

    /**
     * Returns the effective configuration after merging the base config with
     * local overrides from startup-terminals.local.json.
     *
     * Local overrides use nullable fields — only non-null values replace the base.
     * Tabs are matched by name: a local tab with the same name as a base tab
     * overrides only the fields that are set. Local tabs with new names are appended.
     */
    fun getEffectiveConfig(): TerminalManagerConfig {
        val baseConfig = TerminalManagerConfig(enabled, closeExistingTerminals, skipResetConfirmation, tabs.toList())

        val localFile = getLocalConfigFile()
        if (!localFile.exists()) {
            return baseConfig
        }

        return try {
            val json = localFile.readText()
            val localConfig = gson.fromJson(json, LocalTerminalManagerConfig::class.java)
            mergeConfigs(baseConfig, localConfig)
        } catch (e: Exception) {
            baseConfig
        }
    }

    private fun mergeConfigs(base: TerminalManagerConfig, local: LocalTerminalManagerConfig): TerminalManagerConfig {
        val mergedEnabled = local.enabled ?: base.enabled
        val mergedCloseExisting = local.closeExistingTerminals ?: base.closeExistingTerminals
        val mergedSkipReset = local.skipResetConfirmation ?: base.skipResetConfirmation

        val mergedTabs = if (local.tabs != null) {
            mergeTabs(base.tabs, local.tabs)
        } else {
            base.tabs
        }

        return TerminalManagerConfig(mergedEnabled, mergedCloseExisting, mergedSkipReset, mergedTabs)
    }

    private fun mergeTabs(baseTabs: List<TerminalTabConfig>, localTabs: List<LocalTerminalTabOverride>): List<TerminalTabConfig> {
        val result = mutableListOf<TerminalTabConfig>()
        val matchedLocalNames = mutableSetOf<String>()

        // Merge base tabs with matching local overrides
        for (baseTab in baseTabs) {
            val localOverride = localTabs.find { it.name == baseTab.name }
            if (localOverride != null) {
                matchedLocalNames.add(localOverride.name)
                result.add(TerminalTabConfig(
                    name = baseTab.name,
                    shellId = localOverride.shellId ?: baseTab.shellId,
                    workingDirectory = localOverride.workingDirectory ?: baseTab.workingDirectory,
                    enabled = localOverride.enabled ?: baseTab.enabled,
                    startupCommand = localOverride.startupCommand ?: baseTab.startupCommand,
                    color = localOverride.color ?: baseTab.color
                ))
            } else {
                result.add(baseTab.copy())
            }
        }

        // Append local-only tabs (new tabs not in base)
        for (localTab in localTabs) {
            if (localTab.name !in matchedLocalNames) {
                result.add(TerminalTabConfig(
                    name = localTab.name,
                    shellId = localTab.shellId ?: "default",
                    workingDirectory = localTab.workingDirectory ?: "",
                    enabled = localTab.enabled ?: true,
                    startupCommand = localTab.startupCommand ?: "",
                    color = localTab.color ?: ""
                ))
            }
        }

        return result
    }

    fun save() {
        val configDir = getConfigDir()
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        val config = TerminalManagerConfig(enabled, closeExistingTerminals, skipResetConfirmation, tabs.toList())
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
    val skipResetConfirmation: Boolean = false,
    val tabs: List<TerminalTabConfig> = emptyList()
)

data class LocalTerminalManagerConfig(
    val enabled: Boolean? = null,
    val closeExistingTerminals: Boolean? = null,
    val skipResetConfirmation: Boolean? = null,
    val tabs: List<LocalTerminalTabOverride>? = null
)

data class LocalTerminalTabOverride(
    val name: String = "",
    val shellId: String? = null,
    val workingDirectory: String? = null,
    val enabled: Boolean? = null,
    val startupCommand: String? = null,
    val color: String? = null
)
