package org.nanoya.terminalmanager.settings

object ConfigMerger {

    /**
     * Produces the full effective tab list including ORPHANED entries (overrides whose
     * targetId matched no base tab). Runtime callers should use [toRuntimeConfig], which
     * excludes orphaned tabs.
     */
    fun mergeEffective(base: TerminalManagerConfig, local: LocalTerminalManagerConfig?): List<EffectiveTab> {
        val result = mutableListOf<EffectiveTab>()
        val overrides = local?.tabs?.filter { !it.isPersonal } ?: emptyList()
        val personals = local?.tabs?.filter { it.isPersonal } ?: emptyList()
        val usedOverrides = mutableSetOf<LocalTerminalTabOverride>()

        for (baseTab in base.tabs) {
            val ov = overrides.firstOrNull { it.targetId != null && it.targetId == baseTab.id }
                ?: overrides.firstOrNull { it.targetId == null && it.name != null && it.name == baseTab.name }
            if (ov == null) {
                result.add(EffectiveTab(baseTab.copy(), TabSource.SHARED))
                continue
            }
            usedOverrides.add(ov)
            if (ov.muted == true) continue
            result.add(EffectiveTab(applyOverride(baseTab, ov), TabSource.OVERRIDDEN))
        }

        for (p in personals) {
            result.add(EffectiveTab(personalToTab(p), TabSource.PERSONAL))
        }

        for (ov in overrides) {
            if (ov in usedOverrides) continue
            result.add(EffectiveTab(personalToTab(ov), TabSource.ORPHANED))
        }

        return result
    }

    fun toRuntimeConfig(base: TerminalManagerConfig, local: LocalTerminalManagerConfig?): TerminalManagerConfig {
        val tabs = mergeEffective(base, local)
            .filter { it.source != TabSource.ORPHANED }
            .map { it.config }
        return TerminalManagerConfig(
            enabled = local?.enabled ?: base.enabled,
            closeExistingTerminals = local?.closeExistingTerminals ?: base.closeExistingTerminals,
            skipResetConfirmation = local?.skipResetConfirmation ?: base.skipResetConfirmation,
            tabs = tabs
        )
    }

    fun isLocalEmpty(local: LocalTerminalManagerConfig?): Boolean {
        if (local == null) return true
        return local.enabled == null &&
            local.closeExistingTerminals == null &&
            local.skipResetConfirmation == null &&
            local.tabs.isNullOrEmpty()
    }

    private fun applyOverride(base: TerminalTabConfig, ov: LocalTerminalTabOverride): TerminalTabConfig {
        val mergedEnv = LinkedHashMap(base.env)
        ov.env?.forEach { (k, v) -> mergedEnv[k] = v }
        return TerminalTabConfig(
            id = base.id,
            name = base.name,
            shellId = ov.shellId ?: base.shellId,
            workingDirectory = ov.workingDirectory ?: base.workingDirectory,
            enabled = ov.enabled ?: base.enabled,
            startupCommand = ov.startupCommand ?: base.startupCommand,
            color = ov.color ?: base.color,
            useTmux = ov.useTmux ?: base.useTmux,
            env = mergedEnv,
            openIf = ov.openIf ?: base.openIf
        )
    }

    private fun personalToTab(ov: LocalTerminalTabOverride): TerminalTabConfig =
        TerminalTabConfig(
            id = ov.id ?: "",
            name = ov.name ?: "Terminal",
            shellId = ov.shellId ?: "default",
            workingDirectory = ov.workingDirectory ?: "",
            enabled = ov.enabled ?: true,
            startupCommand = ov.startupCommand ?: "",
            color = ov.color ?: "",
            useTmux = ov.useTmux ?: false,
            env = LinkedHashMap(ov.env ?: emptyMap()),
            openIf = ov.openIf ?: ""
        )
}
