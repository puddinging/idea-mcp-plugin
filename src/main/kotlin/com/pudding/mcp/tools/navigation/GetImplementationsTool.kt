package com.pudding.mcp.tools.navigation

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.util.PsiTreeUtil

class GetImplementationsTool : McpTool {
    override val name = "get_implementations"
    override val description = "Find all concrete implementations of an interface or abstract method"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("line", JsonObject().apply { addProperty("type", "number") })
            add("column", JsonObject().apply { addProperty("type", "number") })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("filePath"); add("line"); add("column")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val line = params.int("line") ?: return error("line is required")
        val column = params.int("column") ?: return error("column is required")

        return runReadAction {
            val psiFile = PsiUtils.findPsiFile(project, path)
                ?: return@runReadAction error("File not found: $path")
            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runReadAction error("Cannot get document")
            val offset = PsiUtils.lineColumnToOffset(doc, line, column)
            val element = psiFile.findElementAt(offset)
                ?: return@runReadAction error("No element at position")

            val implementations = JsonArray()
            val scope = GlobalSearchScope.projectScope(project)

            // Try as method first
            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            if (method != null) {
                OverridingMethodsSearch.search(method, scope, true).forEach { overrider ->
                    val vf = overrider.containingFile?.virtualFile ?: return@forEach
                    val overDoc = PsiDocumentManager.getInstance(project).getDocument(overrider.containingFile)
                    implementations.add(JsonObject().apply {
                        addProperty("filePath", PsiUtils.relativePath(project, vf))
                        addProperty("line", safeLineNumber(overDoc, overrider.textOffset))
                        addProperty("className", overrider.containingClass?.name ?: "")
                        addProperty("methodName", overrider.name)
                    })
                }
                return@runReadAction result { add("implementations", implementations) }
            }

            // Try as class/interface
            val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
            if (psiClass != null) {
                ClassInheritorsSearch.search(psiClass, scope, true).forEach { inheritor ->
                    val vf = inheritor.containingFile?.virtualFile ?: return@forEach
                    val inhDoc = PsiDocumentManager.getInstance(project).getDocument(inheritor.containingFile)
                    implementations.add(JsonObject().apply {
                        addProperty("filePath", PsiUtils.relativePath(project, vf))
                        addProperty("line", safeLineNumber(inhDoc, inheritor.textOffset))
                        addProperty("className", inheritor.name ?: "")
                        addProperty("methodName", "")
                    })
                }
                return@runReadAction result { add("implementations", implementations) }
            }

            error("No method or class at position")
        }
    }

    private fun safeLineNumber(doc: com.intellij.openapi.editor.Document?, offset: Int): Int {
        if (doc == null || offset < 0 || offset > doc.textLength) return 0
        return doc.getLineNumber(offset) + 1
    }
}
