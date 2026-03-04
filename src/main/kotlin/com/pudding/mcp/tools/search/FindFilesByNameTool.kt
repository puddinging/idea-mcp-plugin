package com.pudding.mcp.tools.search

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex

class FindFilesByNameTool : McpTool {
    override val name = "find_files_by_name_keyword"
    override val description = "Search for files whose names contain the specified keyword (case-insensitive)"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("nameKeyword", JsonObject().apply { addProperty("type", "string") })
            add("fileCountLimit", JsonObject().apply { addProperty("type", "number") })
        })
        add("required", com.google.gson.JsonArray().apply { add("nameKeyword") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val keyword = params.string("nameKeyword") ?: return error("nameKeyword is required")
        val limit = params.int("fileCountLimit") ?: 100

        val files = JsonArray()
        var hasMore = false

        runReadAction {
            val index = ProjectFileIndex.getInstance(project)
            var count = 0
            index.iterateContent { vf ->
                if (!vf.isDirectory && vf.name.contains(keyword, ignoreCase = true)) {
                    if (count < limit) {
                        files.add(PsiUtils.relativePath(project, vf))
                        count++
                    } else {
                        hasMore = true
                        return@iterateContent false
                    }
                }
                true
            }
        }

        return result {
            add("files", files)
            addProperty("probablyHasMoreFiles", hasMore)
        }
    }
}
