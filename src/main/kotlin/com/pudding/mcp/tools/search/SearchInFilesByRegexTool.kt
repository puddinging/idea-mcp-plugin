package com.pudding.mcp.tools.search

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.boolOrDefault
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex

class SearchInFilesByRegexTool : McpTool {
    override val name = "search_in_files_by_regex"
    override val description = "Search for a regex pattern in files with optional directory and file mask filters"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("regexPattern", JsonObject().apply { addProperty("type", "string") })
            add("directoryToSearch", JsonObject().apply { addProperty("type", "string") })
            add("fileMask", JsonObject().apply { addProperty("type", "string") })
            add("caseSensitive", JsonObject().apply { addProperty("type", "boolean") })
            add("maxUsageCount", JsonObject().apply { addProperty("type", "number") })
        })
        add("required", com.google.gson.JsonArray().apply { add("regexPattern") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val pattern = params.string("regexPattern") ?: return error("regexPattern is required")
        val directory = params.string("directoryToSearch")
        val fileMask = params.string("fileMask")
        val caseSensitive = params.boolOrDefault("caseSensitive", true)
        val maxCount = params.int("maxUsageCount") ?: 100
        val basePath = project.basePath ?: return error("Project base path not found")

        val regex = try {
            if (caseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            return error("Invalid regex: ${e.message}")
        }

        val searchIn = if (directory != null) "$basePath/$directory" else basePath
        val entries = JsonArray()
        var hasMore = false
        var totalCount = 0

        runReadAction {
            ProjectFileIndex.getInstance(project).iterateContent { vf ->
                if (!vf.isDirectory && vf.path.startsWith(searchIn)) {
                    if (fileMask == null || matchesMask(vf.name, fileMask)) {
                        val content = try {
                            vf.contentsToByteArray().toString(Charsets.UTF_8)
                        } catch (_: Exception) { return@iterateContent true }

                        content.lines().forEachIndexed { idx, line ->
                            if (totalCount >= maxCount) { hasMore = true; return@forEachIndexed }
                            val match = regex.find(line)
                            if (match != null) {
                                val marked = regex.find(line)?.let { m -> line.replaceRange(m.range, "||${m.value}||") } ?: line
                                entries.add(JsonObject().apply {
                                    addProperty("filePath", PsiUtils.relativePath(project, vf))
                                    addProperty("lineNumber", idx + 1)
                                    addProperty("lineText", marked)
                                })
                                totalCount++
                            }
                        }
                    }
                }
                totalCount < maxCount
            }
        }

        return result {
            add("entries", entries)
            addProperty("probablyHasMoreEntries", hasMore)
        }
    }

    private fun matchesMask(fileName: String, mask: String): Boolean {
        val regex = mask.replace(".", "\\.").replace("*", ".*").replace("?", ".")
        return fileName.matches(Regex(regex))
    }
}
