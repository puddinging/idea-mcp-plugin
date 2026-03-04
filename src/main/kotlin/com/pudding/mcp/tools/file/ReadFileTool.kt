package com.pudding.mcp.tools.file

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project

class ReadFileTool : McpTool {
    override val name = "read_file"
    override val description = "Read a specific range of a file by line numbers or offsets"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("file_path", JsonObject().apply { addProperty("type", "string") })
            add("mode", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "slice | lines | line_columns | offsets")
            })
            add("start_line", JsonObject().apply { addProperty("type", "number") })
            add("end_line", JsonObject().apply { addProperty("type", "number") })
            add("start_column", JsonObject().apply { addProperty("type", "number") })
            add("end_column", JsonObject().apply { addProperty("type", "number") })
            add("start_offset", JsonObject().apply { addProperty("type", "number") })
            add("end_offset", JsonObject().apply { addProperty("type", "number") })
            add("max_lines", JsonObject().apply { addProperty("type", "number") })
            add("context_lines", JsonObject().apply { addProperty("type", "number") })
        })
        add("required", com.google.gson.JsonArray().apply { add("file_path") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("file_path") ?: return error("file_path is required")
        val mode = params.string("mode") ?: "slice"
        val maxLines = params.int("max_lines")
        val contextLines = params.int("context_lines") ?: 0

        val vf = PsiUtils.findVirtualFile(project, path) ?: return error("File not found: $path")
        val content = vf.contentsToByteArray().toString(Charsets.UTF_8)
        val lines = content.lines()
        val totalLines = lines.size

        val selectedLines: List<Pair<Int, String>> = when (mode) {
            "lines", "slice" -> {
                val start = (params.int("start_line") ?: 1).coerceAtLeast(1)
                val end = (params.int("end_line") ?: minOf(start + (maxLines ?: 100) - 1, totalLines))
                    .coerceAtMost(totalLines)
                val startWithCtx = (start - contextLines).coerceAtLeast(1)
                val endWithCtx = (end + contextLines).coerceAtMost(totalLines)
                (startWithCtx..endWithCtx).map { it to lines[it - 1] }
            }
            "offsets" -> {
                val startOff = params.int("start_offset") ?: 0
                val endOff = params.int("end_offset") ?: content.length
                val startLine = content.substring(0, startOff.coerceAtMost(content.length)).count { it == '\n' } + 1
                val endLine = content.substring(0, endOff.coerceAtMost(content.length)).count { it == '\n' } + 1
                val s = (startLine - contextLines).coerceAtLeast(1)
                val e = (endLine + contextLines).coerceAtMost(totalLines)
                (s..e).map { it to lines[it - 1] }
            }
            else -> {
                val start = (params.int("start_line") ?: 1).coerceAtLeast(1)
                val end = (params.int("end_line") ?: start).coerceAtMost(totalLines)
                (start..end).map { it to lines[it - 1] }
            }
        }

        val capped = if (maxLines != null) selectedLines.take(maxLines) else selectedLines
        val numbered = capped.joinToString("\n") { (lineNum, text) -> "L$lineNum: $text" }

        return result {
            addProperty("content", numbered)
            addProperty("totalLines", totalLines)
        }
    }
}
