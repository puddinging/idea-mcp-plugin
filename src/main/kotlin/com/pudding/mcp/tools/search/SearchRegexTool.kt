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

class SearchRegexTool : McpTool {
    override val name = "search_regex"
    override val description = "Search for a regex pattern across the entire project"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("q", JsonObject().apply { addProperty("type", "string") })
            add("limit", JsonObject().apply { addProperty("type", "number") })
            add("paths", JsonObject().apply {
                addProperty("type", "array")
                add("items", JsonObject().apply { addProperty("type", "string") })
            })
        })
        add("required", com.google.gson.JsonArray().apply { add("q") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val pattern = params.string("q") ?: return error("q is required")
        val limit = params.int("limit") ?: 100

        val regex = try { Regex(pattern) } catch (e: Exception) {
            return error("Invalid regex: ${e.message}")
        }

        val results = JsonArray()
        var count = 0

        runReadAction {
            ProjectFileIndex.getInstance(project).iterateContent { vf ->
                if (!vf.isDirectory && count < limit) {
                    try {
                        val content = vf.contentsToByteArray().toString(Charsets.UTF_8)
                        content.lines().forEachIndexed { idx, line ->
                            if (count < limit) {
                                val match = regex.find(line)
                                if (match != null) {
                                    results.add(JsonObject().apply {
                                        addProperty("path", PsiUtils.relativePath(project, vf))
                                        addProperty("line", idx + 1)
                                        addProperty("column", match.range.first + 1)
                                        addProperty("preview", line.trim())
                                    })
                                    count++
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                count < limit
            }
        }

        return result { add("results", results) }
    }
}
