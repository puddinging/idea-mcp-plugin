package com.pudding.mcp.tools.analysis

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.pudding.mcp.util.SchemaUtils.stringOrDefault
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

class RunInspectionsTool : McpTool {
    override val name = "run_inspections"
    override val description = "Run code inspections on the project or a specific file"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("scope", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "project|file, default file")
            })
            add("filePath", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Required when scope=file")
            })
            add("severity", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "ERROR|WARNING|ALL, default WARNING")
            })
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val filePath = params.string("filePath")
            ?: return error("filePath is required for inspection")
        val severity = params.stringOrDefault("severity", "WARNING")

        return runReadAction {
            val psiFile = PsiUtils.findPsiFile(project, filePath)
                ?: return@runReadAction error("File not found: $filePath")
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runReadAction error("Cannot get document for: $filePath")

            val path = psiFile.virtualFile?.let { PsiUtils.relativePath(project, it) } ?: filePath
            val problems = JsonArray()

            DaemonCodeAnalyzerEx.processHighlights(
                document, project, null, 0, document.textLength
            ) { info ->
                val sev = info.severity
                val isError = sev >= HighlightSeverity.ERROR
                val isWarning = sev >= HighlightSeverity.WARNING

                val include = when (severity) {
                    "ERROR" -> isError
                    "WARNING" -> isWarning
                    else -> sev >= HighlightSeverity.WEAK_WARNING
                }
                if (!include) return@processHighlights true

                val desc = info.description
                if (desc.isNullOrBlank()) return@processHighlights true

                val line = document.getLineNumber(info.startOffset) + 1
                val col = info.startOffset - document.getLineStartOffset(line - 1) + 1

                val severityStr = when {
                    isError -> "ERROR"
                    isWarning -> "WARNING"
                    else -> "WEAK_WARNING"
                }

                problems.add(JsonObject().apply {
                    addProperty("filePath", path)
                    addProperty("line", line)
                    addProperty("column", col)
                    addProperty("severity", severityStr)
                    addProperty("description", desc)
                })
                true
            }

            result { add("problems", problems) }
        }
    }
}
