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

class SearchInFilesByTextTool : McpTool {
    override val name = "search_in_files_by_text"
    override val description = "Search for text in files with optional directory and file mask filters"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("searchText", JsonObject().apply { addProperty("type", "string") })
            add("directoryToSearch", JsonObject().apply { addProperty("type", "string") })
            add("fileMask", JsonObject().apply { addProperty("type", "string") })
            add("caseSensitive", JsonObject().apply { addProperty("type", "boolean") })
            add("maxUsageCount", JsonObject().apply { addProperty("type", "number") })
        })
        add("required", com.google.gson.JsonArray().apply { add("searchText") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val searchText = params.string("searchText") ?: return error("searchText is required")
        val directory = params.string("directoryToSearch")
        val fileMask = params.string("fileMask")
        val caseSensitive = params.boolOrDefault("caseSensitive", false)
        val maxCount = params.int("maxUsageCount") ?: 100

        val basePath = project.basePath ?: return error("Project base path not found")
        val searchIn = if (directory != null) "$basePath/$directory" else basePath
        val maskRegex = fileMask?.let { compileMask(it) }

        val entries = JsonArray()
        var hasMore = false
        var totalCount = 0

        runReadAction {
            ProjectFileIndex.getInstance(project).iterateContent { vf ->
                if (!vf.isDirectory && vf.path.startsWith(searchIn) && !vf.fileType.isBinary) {
                    if (maskRegex == null || vf.name.matches(maskRegex)) {
                        val content = try {
                            vf.contentsToByteArray().toString(Charsets.UTF_8)
                        } catch (_: Exception) { return@iterateContent true }

                        val lines = content.lines()
                        for ((idx, line) in lines.withIndex()) {
                            if (totalCount >= maxCount) { hasMore = true; break }
                            val found = if (caseSensitive) line.contains(searchText)
                                        else line.contains(searchText, ignoreCase = true)
                            if (found) {
                                val marked = line.replace(searchText, "||$searchText||",
                                    ignoreCase = !caseSensitive)
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

    private fun compileMask(mask: String): Regex {
        val pattern = mask.replace(".", "\\.").replace("*", ".*").replace("?", ".")
        return Regex(pattern)
    }
}
