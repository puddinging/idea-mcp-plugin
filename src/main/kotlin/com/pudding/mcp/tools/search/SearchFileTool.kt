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

class SearchFileTool : McpTool {
    override val name = "search_file"
    override val description = "Search files by glob pattern with optional path filters (supports ! excludes)"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("q", JsonObject().apply { addProperty("type", "string") })
            add("paths", JsonObject().apply {
                addProperty("type", "array")
                add("items", JsonObject().apply { addProperty("type", "string") })
            })
            add("limit", JsonObject().apply { addProperty("type", "number") })
        })
        add("required", com.google.gson.JsonArray().apply { add("q") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val pattern = params.string("q") ?: return error("q is required")
        val limit = params.int("limit") ?: 200
        val pathFilters = params.getAsJsonArray("paths")
            ?.map { it.asString } ?: emptyList()

        val matcher = try {
            FileSystems.getDefault().getPathMatcher("glob:$pattern")
        } catch (e: Exception) {
            return error("Invalid glob pattern: ${e.message}")
        }

        val includeMatchers = pathFilters.filter { !it.startsWith("!") }
            .map { FileSystems.getDefault().getPathMatcher("glob:$it") }
        val excludeMatchers = pathFilters.filter { it.startsWith("!") }
            .map { FileSystems.getDefault().getPathMatcher("glob:${it.removePrefix("!")}") }

        val files = JsonArray()

        runReadAction {
            var count = 0
            ProjectFileIndex.getInstance(project).iterateContent { vf ->
                if (!vf.isDirectory && count < limit) {
                    val relative = PsiUtils.relativePath(project, vf)
                    val path = Paths.get(relative)
                    if (matcher.matches(path)) {
                        val included = includeMatchers.isEmpty() || includeMatchers.any { it.matches(path) }
                        val excluded = excludeMatchers.any { it.matches(path) }
                        if (included && !excluded) {
                            files.add(JsonObject().apply { addProperty("path", relative) })
                            count++
                        }
                    }
                }
                count < limit
            }
        }

        return result { add("files", files) }
    }
}
