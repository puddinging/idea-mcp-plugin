package com.pudding.mcp.tools.project

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.SchemaUtils.result
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager

class GetRepositoriesTool : McpTool {
    override val name = "get_repositories"
    override val description = "Get list of VCS repositories in the project"
    override val inputSchema = JsonObject().apply { addProperty("type", "object") }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val repos = JsonArray()
        ProjectLevelVcsManager.getInstance(project).allVcsRoots.forEach { root ->
            repos.add(JsonObject().apply {
                addProperty("path", root.path.path)
                addProperty("type", root.vcs?.name ?: "unknown")
            })
        }
        return result { add("repositories", repos) }
    }
}
