package com.pudding.mcp

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.pudding.mcp.server.McpSocketServer
import com.pudding.mcp.server.McpSseServer
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*

class McpPluginConfigurable : Configurable {

    private var enabledCheckbox: JCheckBox? = null
    private var statusLabel: JLabel? = null

    override fun getDisplayName() = "MCP Server"

    override fun createComponent(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        // Row 1: Enable checkbox + status
        val topRow = Box.createHorizontalBox()
        enabledCheckbox = JCheckBox("Enable MCP Server", McpPluginSettings.instance.state.enabled)
        statusLabel = JLabel()
        updateStatusLabel()
        enabledCheckbox!!.addActionListener {
            val enabled = enabledCheckbox!!.isSelected
            enabledCheckbox!!.isEnabled = false
            McpPluginSettings.instance.state.enabled = enabled
            ApplicationManager.getApplication().executeOnPooledThread {
                val sseServer = McpSseServer.getInstance()
                val socketServer = McpSocketServer.getInstance()
                if (enabled) {
                    if (!sseServer.isRunning()) sseServer.start(port = 19999)
                    if (!socketServer.isRunning()) socketServer.start(project = null)
                } else {
                    if (sseServer.isRunning()) sseServer.stop()
                    if (socketServer.isRunning()) socketServer.stop()
                }
                SwingUtilities.invokeLater {
                    enabledCheckbox!!.isEnabled = true
                    updateStatusLabel()
                }
            }
        }
        topRow.add(enabledCheckbox)
        topRow.add(Box.createHorizontalStrut(12))
        topRow.add(statusLabel)
        topRow.add(Box.createHorizontalGlue())
        panel.add(topRow)
        panel.add(Box.createVerticalStrut(16))

        // Row 2: Copy buttons
        val btnRow = Box.createHorizontalBox()
        val copySseBtn = JButton("Copy SSE Config")
        copySseBtn.addActionListener { copyToClipboard(buildSseConfig()) }
        btnRow.add(copySseBtn)
        btnRow.add(Box.createHorizontalStrut(8))
        val copyStdioBtn = JButton("Copy Stdio Config")
        copyStdioBtn.addActionListener { copyToClipboard(buildStdioConfig()) }
        btnRow.add(copyStdioBtn)
        btnRow.add(Box.createHorizontalGlue())
        panel.add(btnRow)

        // Push everything to top
        panel.add(Box.createVerticalGlue())

        return panel
    }

    override fun isModified() = false

    override fun apply() {}

    override fun reset() {
        enabledCheckbox?.isSelected = McpPluginSettings.instance.state.enabled
        updateStatusLabel()
    }

    private fun updateStatusLabel() {
        val sseRunning = McpSseServer.getInstance().isRunning()
        val socketRunning = McpSocketServer.getInstance().isRunning()
        statusLabel?.text = when {
            sseRunning && socketRunning -> "Status: Running"
            sseRunning || socketRunning -> "Status: Partially Running"
            else -> "Status: Stopped"
        }
    }

    private fun copyToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    // ---- Config builders ----

    private fun buildSseConfig(): String {
        return """{
  "mcpServers": {
    "intellij-idea-mcp": {
      "url": "http://127.0.0.1:19999/sse"
    }
  }
}"""
    }

    private fun buildStdioConfig(): String {
        val javaPath = escapeJson(getJbrJavaPath())
        val classpath = escapeJson(getPluginClasspath())
        return """{
  "mcpServers": {
    "intellij-idea-mcp": {
      "type": "stdio",
      "env": { "MCP_SOCKET_PORT": "19998" },
      "command": "$javaPath",
      "args": ["-classpath", "$classpath", "com.pudding.mcp.stdio.McpStdioRunner"]
    }
  }
}"""
    }

    private fun getJbrJavaPath(): String {
        val javaHome = System.getProperty("java.home") ?: return "java"
        val bin = File(javaHome, "bin/java")
        return if (bin.exists()) bin.absolutePath else "java"
    }

    private fun getPluginClasspath(): String {
        val descriptor = PluginManagerCore.getPlugin(PluginId.getId("com.pudding.mcp")) ?: return ""
        val libDir = descriptor.pluginPath.resolve("lib").toFile()
        if (!libDir.isDirectory) return descriptor.pluginPath.resolve("lib").toString()
        val jars = libDir.listFiles { f -> f.extension == "jar" } ?: return ""
        return jars.joinToString(File.pathSeparator) { it.absolutePath }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
