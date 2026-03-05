package com.pudding.mcp

import com.pudding.mcp.server.McpSocketServer
import com.pudding.mcp.server.McpSseServer
import com.pudding.mcp.server.ToolRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class McpPluginStartupActivity : StartupActivity.DumbAware {
    companion object {
        private var toolsRegistered = false
    }

    override fun runActivity(project: Project) {
        synchronized(McpPluginStartupActivity::class.java) {
            if (!toolsRegistered) {
                ToolRegistry.registerAll()
                toolsRegistered = true
            }
        }
        if (McpPluginSettings.instance.state.enabled) {
            McpSseServer.getInstance().start(project, port = 19999)
            McpSocketServer.getInstance().start(project)
        }
    }
}
