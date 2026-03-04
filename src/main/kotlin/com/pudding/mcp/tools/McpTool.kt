package com.pudding.mcp.tools

import com.google.gson.JsonObject
import com.intellij.openapi.project.Project

interface McpTool {
    val name: String
    val description: String
    val inputSchema: JsonObject
    fun execute(project: Project, params: JsonObject): JsonObject
}
