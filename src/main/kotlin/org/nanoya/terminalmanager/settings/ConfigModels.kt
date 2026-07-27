package org.nanoya.terminalmanager.settings

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
    val id: String? = null,            // identity for personal local-only tabs
    val targetId: String? = null,      // matches a base tab's id (override rows)
    val name: String? = null,          // personal tab name, or legacy name-match fallback
    val shellId: String? = null,
    val workingDirectory: String? = null,
    val enabled: Boolean? = null,
    val startupCommand: String? = null,
    val color: String? = null,
    val useTmux: Boolean? = null,
    val env: Map<String, String>? = null,
    val openIf: String? = null,
    val muted: Boolean? = null,
    val isPersonal: Boolean = false
)

enum class TabSource { SHARED, OVERRIDDEN, PERSONAL, ORPHANED }

data class EffectiveTab(
    val config: TerminalTabConfig,
    val source: TabSource
)
