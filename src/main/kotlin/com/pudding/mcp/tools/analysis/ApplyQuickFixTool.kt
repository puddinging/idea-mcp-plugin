package com.pudding.mcp.tools.analysis

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.intOrDefault
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiRecursiveElementVisitor

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
                val manager = InspectionManager.getInstance(project)

                // Collect all problems at the target line
                val problemsAtLine = mutableListOf<Pair<ProblemDescriptor, LocalInspectionTool>>()

                InspectionToolRegistrar.getInstance().createTools()
                    .mapNotNull { it.tool as? LocalInspectionTool }
                    .forEach { tool ->
                        try {
                            val holder = ProblemsHolder(manager, psiFile, false)
                            val visitor: PsiElementVisitor = tool.buildVisitor(holder, false)
                            psiFile.accept(object : PsiRecursiveElementVisitor() {
                                override fun visitElement(element: PsiElement) {
                                    element.accept(visitor)
                                    super.visitElement(element)
                                }
                            })
                            for (pd in holder.results) {
                                val pdElement = pd.psiElement ?: continue
                                val pdLine = document.getLineNumber(pdElement.textOffset) + 1
                                if (pdLine == line && pd.fixes?.isNotEmpty() == true) {
                                    problemsAtLine.add(pd to tool)
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }

                if (problemsAtLine.isEmpty()) {
                    errorMsg = "No fixable problems found at line $line"
                    return@runReadAction
                }

                // Find the fix closest to the column
                val bestProblem = problemsAtLine.minByOrNull { pd ->
                    val pdOff = pd.first.psiElement?.textOffset ?: 0
                    val pdCol = pdOff - document.getLineStartOffset(line - 1) + 1
                    kotlin.math.abs(pdCol - column)
                }

                if (bestProblem == null) {
                    errorMsg = "No fixable problems at position"
                    return@runReadAction
                }

                val fixes = bestProblem.first.fixes ?: run {
                    errorMsg = "No fixes available"
                    return@runReadAction
                }

                if (fixIndex >= fixes.size) {
                    errorMsg = "Fix index $fixIndex out of bounds (${fixes.size} fixes available)"
                    return@runReadAction
                }

                val fix = fixes[fixIndex]
                fixName = fix.name

                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        fix.applyFix(project, bestProblem.first)
                        success = true
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Fix failed"
                    }
                }
            }
        }

        return if (success) result {
            addProperty("success", true)
            addProperty("fixName", fixName)
        } else error(errorMsg)
    }
}
