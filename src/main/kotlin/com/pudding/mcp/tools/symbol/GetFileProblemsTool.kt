package com.pudding.mcp.tools.symbol

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.boolOrDefault
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiRecursiveElementVisitor

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
            val manager = InspectionManager.getInstance(project)
            val problems = JsonArray()

            com.intellij.codeInspection.ex.InspectionToolRegistrar.getInstance().createTools()
                .mapNotNull { it.tool as? LocalInspectionTool }
                .forEach { tool ->
                    try {
                        val holder = ProblemsHolder(manager, psiFile, false)
                        val visitor: PsiElementVisitor = tool.buildVisitor(holder, false)
                        psiFile.accept(object : PsiRecursiveElementVisitor() {
                            override fun visitElement(element: com.intellij.psi.PsiElement) {
                                element.accept(visitor)
                                super.visitElement(element)
                            }
                        })
                        holder.results.forEach { pd ->
                            val isError = pd.highlightType == ProblemHighlightType.ERROR ||
                                          pd.highlightType == ProblemHighlightType.GENERIC_ERROR
                            if (!errorsOnly || isError) {
                                val line = if (document != null && pd.psiElement != null)
                                    document.getLineNumber(pd.psiElement!!.textOffset) + 1 else 0
                                problems.add(JsonObject().apply {
                                    addProperty("message", pd.descriptionTemplate)
                                    addProperty("kind", if (isError) "ERROR" else "WARNING")
                                    addProperty("file", path)
                                    addProperty("line", line)
                                })
                            }
                        }
                    } catch (_: Exception) {}
                }

            result { add("problems", problems) }
        }
    }
}
