package com.pudding.mcp.tools.project

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.SchemaUtils.result
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager

class GetProjectDepsTool : McpTool {
    override val name = "get_project_dependencies"
    override val description = "Get all library dependencies defined in the project"
    override val inputSchema = JsonObject().apply { addProperty("type", "object") }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val deps = JsonArray()
        runReadAction {
            ModuleManager.getInstance(project).modules.forEach { module ->
                ModuleRootManager.getInstance(module).orderEntries.forEach { entry ->
                    if (entry is LibraryOrderEntry) {
                        val name = entry.libraryName ?: entry.presentableName
                        if (name.isNotBlank() && !deps.contains(com.google.gson.JsonPrimitive(name))) {
                            deps.add(name)
                        }
                    }
                }
            }
        }
        return result { add("dependencies", deps) }
    }
}
