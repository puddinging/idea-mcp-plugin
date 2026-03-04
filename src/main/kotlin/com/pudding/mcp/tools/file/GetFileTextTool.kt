package com.pudding.mcp.tools.file

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project

class GetFileTextTool : McpTool {
    override val name = "get_file_text_by_path"
    override val description = "Read file content with optional truncation"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("pathInProject", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Path relative to project root")
            })
            add("maxLinesCount", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "Maximum number of lines to return")
            })
            add("truncateMode", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "START | MIDDLE | END | NONE")
            })
        })
        add("required", com.google.gson.JsonArray().apply { add("pathInProject") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("pathInProject") ?: return error("pathInProject is required")
        val maxLines = params.int("maxLinesCount")
        val truncateMode = params.string("truncateMode") ?: "END"

        val vf = PsiUtils.findVirtualFile(project, path) ?: return error("File not found: $path")
        if (vf.isDirectory) return error("Path is a directory: $path")

        val content = vf.contentsToByteArray().toString(Charsets.UTF_8)
        val lines = content.lines()
        val totalLines = lines.size

        val resultLines = if (maxLines == null || maxLines >= totalLines) {
            lines
        } else {
            when (truncateMode.uppercase()) {
                "START"  -> lines.takeLast(maxLines)
                "MIDDLE" -> {
                    val half = maxLines / 2
                    lines.take(half) + listOf("... [truncated] ...") + lines.takeLast(maxLines - half)
                }
                else     -> lines.take(maxLines) // END or NONE
            }
        }

        val numbered = resultLines.mapIndexed { i, line ->
            val lineNum = when (truncateMode.uppercase()) {
                "START" -> totalLines - resultLines.size + i + 1
                else    -> i + 1
            }
            "L$lineNum: $line"
        }.joinToString("\n")

        return result {
            addProperty("content", numbered)
            addProperty("totalLines", totalLines)
            addProperty("path", path)
        }
    }
}
