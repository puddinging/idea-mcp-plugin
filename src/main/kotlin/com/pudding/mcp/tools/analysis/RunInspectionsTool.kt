package com.pudding.mcp.tools.analysis

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.pudding.mcp.util.SchemaUtils.stringOrDefault
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementVisitor

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
        val scope = params.stringOrDefault("scope", "file")
        val filePath = params.string("filePath")
        val severity = params.stringOrDefault("severity", "WARNING")

        if (scope == "file" && filePath == null) {
            return error("filePath is required when scope=file")
        }

        return runReadAction {
            val problems = JsonArray()
            val manager = InspectionManager.getInstance(project)

            val filesToInspect = if (scope == "file" && filePath != null) {
                val psiFile = PsiUtils.findPsiFile(project, filePath)
                    ?: return@runReadAction error("File not found: $filePath")
                listOf(psiFile)
            } else {
                // For project scope, we still need to iterate files
                // For simplicity, if a filePath is given, inspect that file
                if (filePath != null) {
                    val psiFile = PsiUtils.findPsiFile(project, filePath)
                        ?: return@runReadAction error("File not found: $filePath")
                    listOf(psiFile)
                } else {
                    return@runReadAction error("filePath is required for inspection")
                }
            }

            for (psiFile in filesToInspect) {
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                val path = psiFile.virtualFile?.let { PsiUtils.relativePath(project, it) } ?: ""

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
                            holder.results.forEach { pd ->
                                val isError = pd.highlightType == ProblemHighlightType.ERROR ||
                                        pd.highlightType == ProblemHighlightType.GENERIC_ERROR
                                val isWarning = pd.highlightType == ProblemHighlightType.WARNING ||
                                        pd.highlightType == ProblemHighlightType.GENERIC_ERROR_OR_WARNING

                                val severityStr = when {
                                    isError -> "ERROR"
                                    isWarning -> "WARNING"
                                    else -> "WEAK_WARNING"
                                }

                                val include = when (severity) {
                                    "ERROR" -> isError
                                    "WARNING" -> isError || isWarning
                                    else -> true
                                }

                                if (include) {
                                    val line = if (document != null && pd.psiElement != null)
                                        document.getLineNumber(pd.psiElement!!.textOffset) + 1 else 0
                                    val col = if (document != null && pd.psiElement != null) {
                                        val off = pd.psiElement!!.textOffset
                                        off - document.getLineStartOffset(document.getLineNumber(off)) + 1
                                    } else 0

                                    val hasFix = pd.fixes?.isNotEmpty() == true

                                    problems.add(JsonObject().apply {
                                        addProperty("filePath", path)
                                        addProperty("line", line)
                                        addProperty("column", col)
                                        addProperty("severity", severityStr)
                                        addProperty("inspectionId", tool.id)
                                        addProperty("description", pd.descriptionTemplate)
                                        addProperty("hasQuickFix", hasFix)
                                    })
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }
            }

            result { add("problems", problems) }
        }
    }
}
