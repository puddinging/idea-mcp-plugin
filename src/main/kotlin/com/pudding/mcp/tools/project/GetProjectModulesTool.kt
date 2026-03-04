package com.pudding.mcp.tools.project

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.SchemaUtils.result
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project

class GetProjectModulesTool : McpTool {
    override val name = "get_project_modules"
    override val description = "Get all modules in the project with their types"
    override val inputSchema = JsonObject().apply { addProperty("type", "object") }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val modules = JsonArray()
        runReadAction {
            ModuleManager.getInstance(project).modules.forEach { module ->
                modules.add(JsonObject().apply {
                    addProperty("name", module.name)
                    addProperty("type", module.moduleTypeName)
                })
            }
        }
        return result { add("modules", modules) }
    }
}
