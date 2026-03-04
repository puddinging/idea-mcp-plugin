package com.pudding.mcp

import com.pudding.mcp.server.McpSseServer
import com.pudding.mcp.server.ToolRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class McpPluginStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        ToolRegistry.registerAll()
        if (McpPluginSettings.instance.state.enabled) {
            McpSseServer.getInstance().start(project, port = 19999)
        }
    }
}
