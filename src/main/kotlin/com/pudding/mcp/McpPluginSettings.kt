package com.pudding.mcp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "McpPluginSettings", storages = [Storage("McpPluginSettings.xml")])
class McpPluginSettings : PersistentStateComponent<McpPluginSettings.State> {

    data class State(var enabled: Boolean = false)

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        val instance: McpPluginSettings
            get() = ApplicationManager.getApplication().getService(McpPluginSettings::class.java)
    }
}
