package org.nanoya.terminalmanager.settings

import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.io.File
import java.util.UUID

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
                tabs = config.tabs.map { it.copy() }.toMutableList()
                // One-time migration: assign stable ids and persist if any were missing.
                if (assignMissingIds(tabs) { UUID.randomUUID().toString() }) {
                    save()
                }
            } catch (e: Exception) {
                enabled = true
                closeExistingTerminals = false
                skipResetConfirmation = false
                tabs = mutableListOf()
            }
        }
    }

    fun loadLocalConfig(): LocalTerminalManagerConfig? {
        val localFile = getLocalConfigFile()
        if (!localFile.exists()) return null
        return try {
            gson.fromJson(localFile.readText(), LocalTerminalManagerConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun saveLocalConfig(local: LocalTerminalManagerConfig?) {
        val localFile = getLocalConfigFile()
        if (ConfigMerger.isLocalEmpty(local)) {
            if (localFile.exists()) localFile.delete()
            return
        }
        val configDir = getConfigDir()
        if (!configDir.exists()) configDir.mkdirs()
        localFile.writeText(gson.toJson(local))
    }

    /**
     * Returns the effective configuration after merging the base config with
     * local overrides from startup-terminals.local.json. Orphaned overrides are excluded.
     */
    fun getEffectiveConfig(): TerminalManagerConfig {
        val baseConfig = TerminalManagerConfig(enabled, closeExistingTerminals, skipResetConfirmation, tabs.toList())
        return ConfigMerger.toRuntimeConfig(baseConfig, loadLocalConfig())
    }

    /** Full effective tab list including ORPHANED entries, for the settings preview. */
    fun getEffectiveTabs(): List<EffectiveTab> {
        val baseConfig = TerminalManagerConfig(enabled, closeExistingTerminals, skipResetConfirmation, tabs.toList())
        return ConfigMerger.mergeEffective(baseConfig, loadLocalConfig())
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

        /** Assigns [idSupplier] ids to any tab with a blank id. Returns true if anything changed. */
        fun assignMissingIds(tabs: MutableList<TerminalTabConfig>, idSupplier: () -> String): Boolean {
            var changed = false
            for (tab in tabs) {
                if (tab.id.isBlank()) {
                    tab.id = idSupplier()
                    changed = true
                }
            }
            return changed
        }
    }
}
