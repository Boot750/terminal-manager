package org.nanoya.terminalmanager.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory
import java.awt.KeyboardFocusManager

/**
 * Base action for splitting the current terminal.
 * Delegates to the built-in IntelliJ terminal split actions.
 */
abstract class SplitTerminalAction(
    private val actionId: String,
    text: String,
    description: String,
    private val fallbackActionId: String? = null
) : AnAction(text, description, null) {

    companion object {
        private val LOG = Logger.getInstance(SplitTerminalAction::class.java)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        LOG.info("SplitTerminalAction.actionPerformed called for actionId=$actionId")

        val project = e.project ?: run {
            LOG.warn("No project found")
            return
        }

        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: run {
            LOG.warn("Terminal tool window not found")
            return
        }

        LOG.info("Tool window found, activating...")

        // Ensure terminal tool window is active and focused
        toolWindow.activate {
            LOG.info("Tool window activated callback triggered")

            // Try to invoke the built-in split action
            val actionManager = ActionManager.getInstance()
            val action = actionManager.getAction(actionId)
            val usedActionId: String

            val resolvedAction = if (action != null) {
                usedActionId = actionId
                action
            } else {
                LOG.info("Action $actionId not found, trying fallback: $fallbackActionId")
                usedActionId = fallbackActionId ?: ""
                fallbackActionId?.let { actionManager.getAction(it) }
            }

            if (resolvedAction == null) {
                LOG.warn("No action found for $actionId or fallback $fallbackActionId")
                return@activate
            }

            LOG.info("Found action: ${resolvedAction.javaClass.name} for id=$usedActionId")

            // Get the actual focused component from keyboard focus manager
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
            LOG.info("Focus owner: ${focusOwner?.javaClass?.name ?: "null"}")

            val component = focusOwner ?: toolWindow.component
            LOG.info("Using component: ${component.javaClass.name}")

            // Get data context from the focused component
            val baseContext = DataManager.getInstance().getDataContext(component)
            LOG.info("Base context: ${baseContext.javaClass.name}")

            // Build context with project
            val context = SimpleDataContext.builder()
                .setParent(baseContext)
                .add(CommonDataKeys.PROJECT, project)
                .build()

            // Execute the action directly without checking if enabled
            LOG.info("Calling actionPerformed on ${resolvedAction.javaClass.name} for action $usedActionId")
            val event = AnActionEvent.createEvent(resolvedAction, context, null, ActionPlaces.TOOLWINDOW_CONTENT, ActionUiKind.NONE, null)
            try {
                resolvedAction.actionPerformed(event)
                LOG.info("actionPerformed completed successfully")
            } catch (ex: Exception) {
                LOG.error("actionPerformed threw exception", ex)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toolWindow = project?.let {
            ToolWindowManager.getInstance(it).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        }
        // In reworked terminal (2025.x), contentManager.contents may be empty even when terminals exist
        // Just check if tool window exists and is available
        val isEnabled = project != null && toolWindow != null && toolWindow.isAvailable
        e.presentation.isEnabled = isEnabled

        LOG.debug("update() called for $actionId: isEnabled=$isEnabled, project=${project != null}, toolWindow=${toolWindow != null}, isAvailable=${toolWindow?.isAvailable}")
    }
}

/**
 * Split the current terminal to the right (vertical split).
 * Shortcut: Alt+Shift+V (V for Vertical/side-by-side)
 */
class SplitTerminalRightAction : SplitTerminalAction(
    actionId = "Terminal.SplitVertically",
    text = "Terminal Manager: Split Right",
    description = "Split the current terminal to the right",
    fallbackActionId = "TW.SplitRight"
) {
    init {
        templatePresentation.icon = AllIcons.Actions.SplitVertically
    }
}

/**
 * Split the current terminal down (horizontal split).
 * Shortcut: Alt+Shift+H (H for Horizontal/top-bottom)
 */
class SplitTerminalDownAction : SplitTerminalAction(
    actionId = "Terminal.SplitHorizontally",
    text = "Terminal Manager: Split Down",
    description = "Split the current terminal downward",
    fallbackActionId = "TW.SplitDown"
) {
    init {
        templatePresentation.icon = AllIcons.Actions.SplitHorizontally
    }
}

/**
 * Move the current terminal to a split on the right.
 * Shortcut: Alt+Shift+Ctrl+V
 */
class MoveTerminalRightAction : SplitTerminalAction(
    actionId = "TW.SplitAndMoveRight",
    text = "Terminal Manager: Move to Right Split",
    description = "Move the current terminal to a split on the right"
) {
    init {
        templatePresentation.icon = AllIcons.Actions.SplitVertically
    }
}

/**
 * Move the current terminal to a split below.
 * Shortcut: Alt+Shift+Ctrl+H
 */
class MoveTerminalDownAction : SplitTerminalAction(
    actionId = "TW.SplitAndMoveDown",
    text = "Terminal Manager: Move to Bottom Split",
    description = "Move the current terminal to a split below"
) {
    init {
        templatePresentation.icon = AllIcons.Actions.SplitHorizontally
    }
}

/**
 * Remove the current split and merge terminals back.
 * Shortcut: Alt+Shift+U (U for Unsplit)
 */
class UnsplitTerminalAction : SplitTerminalAction(
    actionId = "Terminal.Unsplit",
    text = "Terminal Manager: Unsplit",
    description = "Remove the current terminal split",
    fallbackActionId = "TW.Unsplit"
) {
    init {
        templatePresentation.icon = AllIcons.General.CollapseComponent
    }
}

/**
 * Remove all splits in the terminal tool window.
 * Shortcut: Alt+Shift+Ctrl+U
 */
class UnsplitAllTerminalsAction : SplitTerminalAction(
    actionId = "Terminal.UnsplitAll",
    text = "Terminal Manager: Unsplit All",
    description = "Remove all terminal splits",
    fallbackActionId = "TW.UnsplitAll"
) {
    init {
        templatePresentation.icon = AllIcons.General.CollapseComponent
    }
}

/**
 * Navigate to the next split pane.
 * Finds terminal panels and cycles focus between them.
 */
class GotoNextSplitAction : AnAction(
    "Terminal Manager: Go to Next Split",
    "Navigate to the next terminal split pane",
    null
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return

        toolWindow.activate {
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                ?: return@activate

            // Find the OnePixelSplitter that contains the terminal splits
            // Hierarchy: TerminalToolWindowPanel -> ... -> InternalDecoratorImpl -> OnePixelSplitter -> InternalDecoratorImpl
            var splitter: java.awt.Component? = null
            var terminalToolWindowPanel: java.awt.Component? = null
            var internalDecoratorCount = 0
            var current: java.awt.Component? = focusOwner
            while (current != null) {
                val className = current.javaClass.name
                if (className.contains("TerminalToolWindowPanel")) {
                    terminalToolWindowPanel = current
                }
                if (className.contains("OnePixelSplitter")) {
                    splitter = current
                }
                if (className.contains("InternalDecoratorImpl")) {
                    internalDecoratorCount++
                    // Stop at the second InternalDecoratorImpl (the outer tool window boundary)
                    if (internalDecoratorCount >= 2) break
                }
                // Also stop at ThreeComponentsSplitter (IDE main splitter)
                if (className.contains("ThreeComponentsSplitter")) break
                current = current.parent
            }

            // Use the splitter if found, otherwise fall back to TerminalToolWindowPanel
            val searchRoot = splitter ?: terminalToolWindowPanel
            if (searchRoot == null) return@activate

            // Find all TerminalPanel components within the container
            val terminalPanels = mutableListOf<java.awt.Component>()
            findTerminalPanels(searchRoot, terminalPanels, 0)

            if (terminalPanels.size < 2) return@activate

            // Find which panel currently has focus
            var currentIndex = -1
            for (i in terminalPanels.indices) {
                if (isAncestorOf(terminalPanels[i], focusOwner)) {
                    currentIndex = i
                    break
                }
            }

            // Move to next panel
            val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % terminalPanels.size
            val nextPanel = terminalPanels[nextIndex]

            // Find a focusable child within the panel
            val focusTarget = findFocusableChild(nextPanel) ?: nextPanel
            focusTarget.requestFocusInWindow()
        }
    }

    private fun findTerminalPanels(component: java.awt.Component, panels: MutableList<java.awt.Component>, depth: Int = 0) {
        val className = component.javaClass.name

        // Look for TerminalPanel (the container for each terminal split)
        if (className.contains("TerminalPanel") && !className.contains("ToolWindow")) {
            panels.add(component)
            return // Don't recurse into this panel
        }

        if (component is java.awt.Container) {
            for (child in component.components) {
                findTerminalPanels(child, panels, depth + 1)
            }
        }
    }

    private fun findFocusableChild(component: java.awt.Component): java.awt.Component? {
        if (component.isFocusable && component.isVisible && component.isEnabled) {
            val className = component.javaClass.name
            // Prefer editor components for terminal focus
            if (className.contains("Editor") || className.contains("Terminal")) {
                return component
            }
        }
        if (component is java.awt.Container) {
            for (child in component.components) {
                val found = findFocusableChild(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun isAncestorOf(ancestor: java.awt.Component, descendant: java.awt.Component?): Boolean {
        var current = descendant
        while (current != null) {
            if (current == ancestor) return true
            current = current.parent
        }
        return false
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toolWindow = project?.let {
            ToolWindowManager.getInstance(it).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        }
        e.presentation.isEnabled = project != null && toolWindow != null && toolWindow.isAvailable
    }
}

/**
 * Navigate to the previous split pane.
 * Finds terminal panels and cycles focus between them.
 */
class GotoPreviousSplitAction : AnAction(
    "Terminal Manager: Go to Previous Split",
    "Navigate to the previous terminal split pane",
    null
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID) ?: return

        toolWindow.activate {
            val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                ?: return@activate

            // Find the OnePixelSplitter that contains the terminal splits
            // Hierarchy: TerminalToolWindowPanel -> ... -> InternalDecoratorImpl -> OnePixelSplitter -> InternalDecoratorImpl
            var splitter: java.awt.Component? = null
            var terminalToolWindowPanel: java.awt.Component? = null
            var internalDecoratorCount = 0
            var current: java.awt.Component? = focusOwner
            while (current != null) {
                val className = current.javaClass.name
                if (className.contains("TerminalToolWindowPanel")) {
                    terminalToolWindowPanel = current
                }
                if (className.contains("OnePixelSplitter")) {
                    splitter = current
                }
                if (className.contains("InternalDecoratorImpl")) {
                    internalDecoratorCount++
                    // Stop at the second InternalDecoratorImpl (the outer tool window boundary)
                    if (internalDecoratorCount >= 2) break
                }
                // Also stop at ThreeComponentsSplitter (IDE main splitter)
                if (className.contains("ThreeComponentsSplitter")) break
                current = current.parent
            }

            // Use the splitter if found, otherwise fall back to TerminalToolWindowPanel
            val searchRoot = splitter ?: terminalToolWindowPanel
            if (searchRoot == null) return@activate

            // Find all TerminalPanel components within the container
            val terminalPanels = mutableListOf<java.awt.Component>()
            findTerminalPanels(searchRoot, terminalPanels, 0)

            if (terminalPanels.size < 2) return@activate

            // Find which panel currently has focus
            var currentIndex = -1
            for (i in terminalPanels.indices) {
                if (isAncestorOf(terminalPanels[i], focusOwner)) {
                    currentIndex = i
                    break
                }
            }

            // Move to previous panel
            val prevIndex = if (currentIndex < 0) terminalPanels.size - 1 else (currentIndex - 1 + terminalPanels.size) % terminalPanels.size
            val prevPanel = terminalPanels[prevIndex]

            // Find a focusable child within the panel
            val focusTarget = findFocusableChild(prevPanel) ?: prevPanel
            focusTarget.requestFocusInWindow()
        }
    }

    private fun findTerminalPanels(component: java.awt.Component, panels: MutableList<java.awt.Component>, depth: Int = 0) {
        val className = component.javaClass.name

        // Look for TerminalPanel (the container for each terminal split)
        if (className.contains("TerminalPanel") && !className.contains("ToolWindow")) {
            panels.add(component)
            return // Don't recurse into this panel
        }

        if (component is java.awt.Container) {
            for (child in component.components) {
                findTerminalPanels(child, panels, depth + 1)
            }
        }
    }

    private fun findFocusableChild(component: java.awt.Component): java.awt.Component? {
        if (component.isFocusable && component.isVisible && component.isEnabled) {
            val className = component.javaClass.name
            // Prefer editor components for terminal focus
            if (className.contains("Editor") || className.contains("Terminal")) {
                return component
            }
        }
        if (component is java.awt.Container) {
            for (child in component.components) {
                val found = findFocusableChild(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun isAncestorOf(ancestor: java.awt.Component, descendant: java.awt.Component?): Boolean {
        var current = descendant
        while (current != null) {
            if (current == ancestor) return true
            current = current.parent
        }
        return false
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val toolWindow = project?.let {
            ToolWindowManager.getInstance(it).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID)
        }
        e.presentation.isEnabled = project != null && toolWindow != null && toolWindow.isAvailable
    }
}
