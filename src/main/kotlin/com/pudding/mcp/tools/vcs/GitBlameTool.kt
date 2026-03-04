package com.pudding.mcp.tools.vcs

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import git4idea.GitVcs
import java.text.SimpleDateFormat

class GitBlameTool : McpTool {
    override val name = "git_blame"
    override val description = "Get git blame information for each line in a file"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("startLine", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "Start line, default 1")
            })
            add("endLine", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "End line, default last line")
            })
        })
        add("required", com.google.gson.JsonArray().apply { add("filePath") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val startLine = params.int("startLine") ?: 1
        val endLine = params.int("endLine")

        val virtualFile = PsiUtils.findVirtualFile(project, path)
            ?: return error("File not found: $path")

        val gitVcs = GitVcs.getInstance(project)
        val annotationProvider = gitVcs.annotationProvider

        return try {
            val annotation: FileAnnotation = annotationProvider.annotate(virtualFile)
            val lineCount = annotation.lineCount
            val actualEnd = endLine ?: lineCount
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

            // Find AUTHOR and DATE aspects
            val aspects = annotation.aspects
            val authorAspect = aspects.find { it.id == LineAnnotationAspect.AUTHOR }

            val blame = JsonArray()
            for (lineIdx in (startLine - 1) until actualEnd.coerceAtMost(lineCount)) {
                val revisionNumber = annotation.getLineRevisionNumber(lineIdx)
                val date = annotation.getLineDate(lineIdx)
                val author = authorAspect?.getValue(lineIdx) ?: ""

                blame.add(JsonObject().apply {
                    addProperty("line", lineIdx + 1)
                    addProperty("author", author)
                    addProperty("commitHash", revisionNumber?.asString() ?: "")
                    addProperty("date", if (date != null) dateFormat.format(date) else "")
                })
            }

            @Suppress("DEPRECATION")
            annotation.dispose()
            result { add("blame", blame) }
        } catch (e: Exception) {
            error("Git blame failed: ${e.message}")
        }
    }
}
