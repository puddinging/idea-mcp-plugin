package com.pudding.mcp.tools.analysis

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.intOrDefault
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

class ApplyQuickFixTool : McpTool {
    override val name = "apply_quick_fix"
    override val description = "Apply a QuickFix to a problem at the specified position"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("line", JsonObject().apply { addProperty("type", "number") })
            add("column", JsonObject().apply { addProperty("type", "number") })
            add("fixIndex", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "Index of the fix to apply, default 0")
            })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("filePath"); add("line"); add("column")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val line = params.int("line") ?: return error("line is required")
        val column = params.int("column") ?: return error("column is required")
        val fixIndex = params.intOrDefault("fixIndex", 0)

        var fixName = ""
        var success = false
        var errorMsg = ""

        ApplicationManager.getApplication().invokeAndWait {
            com.intellij.openapi.application.runReadAction {
                val psiFile = PsiUtils.findPsiFile(project, path)
                if (psiFile == null) {
                    errorMsg = "File not found: $path"
                    return@runReadAction
                }
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                if (document == null) {
                    errorMsg = "Cannot get document"
                    return@runReadAction
                }

                val targetOffset = PsiUtils.lineColumnToOffset(document, line, column)

                // Collect highlights with fixes at the target line from cached analysis
                val candidates = mutableListOf<Pair<HighlightInfo, List<IntentionAction>>>()

                DaemonCodeAnalyzerEx.processHighlights(
                    document, project, null, 0, document.textLength
                ) { info ->
                    if (info.severity < HighlightSeverity.WARNING) return@processHighlights true
                    val infoLine = document.getLineNumber(info.startOffset) + 1
                    if (infoLine != line) return@processHighlights true

                    val fixes = mutableListOf<IntentionAction>()
                    info.findRegisteredQuickFix<Any?> { desc, _ ->
                        fixes.add(desc.action)
                        null
                    }
                    if (fixes.isNotEmpty()) {
                        candidates.add(info to fixes)
                    }
                    true
                }

                if (candidates.isEmpty()) {
                    errorMsg = "No fixable problems found at line $line"
                    return@runReadAction
                }

                // Pick the highlight closest to the target column
                val best = candidates.minByOrNull { (info, _) ->
                    kotlin.math.abs(info.startOffset - targetOffset)
                }!!

                val fixes = best.second
                if (fixIndex >= fixes.size) {
                    errorMsg = "Fix index $fixIndex out of bounds (${fixes.size} fixes available)"
                    return@runReadAction
                }

                val fix = fixes[fixIndex]
                fixName = fix.text

                // Create a temporary editor for the fix to operate on
                val editor = EditorFactory.getInstance().createEditor(document, project)
                try {
                    WriteCommandAction.runWriteCommandAction(project) {
                        try {
                            fix.invoke(project, editor, psiFile)
                            success = true
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Fix failed"
                        }
                    }
                } finally {
                    EditorFactory.getInstance().releaseEditor(editor)
                }
            }
        }

        return if (success) result {
            addProperty("success", true)
            addProperty("fixName", fixName)
        } else error(errorMsg)
    }
}
