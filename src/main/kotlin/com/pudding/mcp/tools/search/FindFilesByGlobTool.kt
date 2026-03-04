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
import java.nio.file.FileSystems
import java.nio.file.Paths

class FindFilesByGlobTool : McpTool {
    override val name = "find_files_by_glob"
    override val description = "Find files matching a glob pattern, e.g. src/**/*.java"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("globPattern", JsonObject().apply { addProperty("type", "string") })
            add("fileCountLimit", JsonObject().apply { addProperty("type", "number") })
        })
        add("required", com.google.gson.JsonArray().apply { add("globPattern") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val pattern = params.string("globPattern") ?: return error("globPattern is required")
        val limit = params.int("fileCountLimit") ?: 200

        val matcher = try {
            FileSystems.getDefault().getPathMatcher("glob:$pattern")
        } catch (e: Exception) {
            return error("Invalid glob pattern: ${e.message}")
        }

        val files = JsonArray()
        var hasMore = false

        runReadAction {
            val index = ProjectFileIndex.getInstance(project)
            var count = 0
            index.iterateContent { vf ->
                if (!vf.isDirectory) {
                    val relative = PsiUtils.relativePath(project, vf)
                    val path = Paths.get(relative)
                    if (matcher.matches(path)) {
                        if (count < limit) {
                            files.add(relative)
                            count++
                        } else {
                            hasMore = true
                            return@iterateContent false
                        }
                    }
                }
                true
            }
        }

        return result {
            add("files", files)
            addProperty("probablyHasMoreMatchingFiles", hasMore)
        }
    }
}
