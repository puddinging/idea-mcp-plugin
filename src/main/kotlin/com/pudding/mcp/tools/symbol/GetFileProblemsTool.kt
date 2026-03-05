package com.pudding.mcp.tools.symbol

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.boolOrDefault
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

class GetFileProblemsTool : McpTool {
    override val name = "get_file_problems"
    override val description = "Get errors and warnings for a specific file using IntelliJ inspections"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("errorsOnly", JsonObject().apply { addProperty("type", "boolean") })
        })
        add("required", com.google.gson.JsonArray().apply { add("filePath") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val errorsOnly = params.boolOrDefault("errorsOnly", false)

        return runReadAction {
            val psiFile = PsiUtils.findPsiFile(project, path)
                ?: return@runReadAction error("File not found: $path")
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runReadAction error("Cannot get document for: $path")

            val problems = JsonArray()

            DaemonCodeAnalyzerEx.processHighlights(
                document, project, null, 0, document.textLength
            ) { info ->
                val severity = info.severity
                val isError = severity >= HighlightSeverity.ERROR
                val isWarning = severity >= HighlightSeverity.WARNING

                if (errorsOnly && !isError) return@processHighlights true
                if (!isWarning) return@processHighlights true

                val desc = info.description
                if (desc.isNullOrBlank()) return@processHighlights true

                val line = document.getLineNumber(info.startOffset) + 1
                val col = info.startOffset - document.getLineStartOffset(line - 1) + 1

                problems.add(JsonObject().apply {
                    addProperty("message", desc)
                    addProperty("kind", if (isError) "ERROR" else "WARNING")
                    addProperty("file", path)
                    addProperty("line", line)
                    addProperty("column", col)
                })
                true
            }

            result { add("problems", problems) }
        }
    }
}
