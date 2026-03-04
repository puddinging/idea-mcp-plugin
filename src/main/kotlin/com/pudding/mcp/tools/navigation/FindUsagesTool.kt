package com.pudding.mcp.tools.navigation

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.pudding.mcp.util.SchemaUtils.stringOrDefault
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

class FindUsagesTool : McpTool {
    override val name = "find_usages"
    override val description = "Find all references to a symbol at the given position, distinguishing read/write/call/import/override"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("line", JsonObject().apply { addProperty("type", "number") })
            add("column", JsonObject().apply { addProperty("type", "number") })
            add("scope", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "project|module|file, default project")
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
        val scopeType = params.stringOrDefault("scope", "project")

        return runReadAction {
            val psiFile = PsiUtils.findPsiFile(project, path)
                ?: return@runReadAction error("File not found: $path")
            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runReadAction error("Cannot get document")
            val offset = PsiUtils.lineColumnToOffset(doc, line, column)
            val element = psiFile.findElementAt(offset)
                ?: return@runReadAction error("No element at position")
            val named = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
                ?: return@runReadAction error("No named element at position")

            val searchScope = when (scopeType) {
                "file" -> LocalSearchScope(psiFile)
                else -> GlobalSearchScope.projectScope(project)
            }

            val usages = JsonArray()
            ReferencesSearch.search(named, searchScope).forEach { ref: PsiReference ->
                val refElement = ref.element
                val refFile = refElement.containingFile?.virtualFile ?: return@forEach
                val refDoc = PsiDocumentManager.getInstance(project).getDocument(refElement.containingFile)
                    ?: return@forEach
                val refLine = refDoc.getLineNumber(refElement.textOffset) + 1
                val refCol = refElement.textOffset - refDoc.getLineStartOffset(refLine - 1) + 1

                // Determine usage type
                val usageType = resolveUsageType(ref)

                // Get preview line text
                val lineStart = refDoc.getLineStartOffset(refLine - 1)
                val lineEnd = refDoc.getLineEndOffset(refLine - 1)
                val preview = refDoc.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd)).trim()

                usages.add(JsonObject().apply {
                    addProperty("filePath", PsiUtils.relativePath(project, refFile))
                    addProperty("line", refLine)
                    addProperty("column", refCol)
                    addProperty("preview", preview)
                    addProperty("type", usageType)
                })
            }

            result { add("usages", usages) }
        }
    }

    private fun resolveUsageType(ref: PsiReference): String {
        val element = ref.element
        val parent = element.parent

        // Check import
        if (parent?.javaClass?.simpleName?.contains("Import") == true) return "IMPORT"

        // Check if it's a method call
        if (parent?.javaClass?.simpleName?.contains("MethodCall") == true ||
            parent?.javaClass?.simpleName?.contains("Call") == true) return "CALL"

        // Check for assignment (write)
        if (parent?.javaClass?.simpleName?.contains("Assignment") == true) {
            // If element is on the left side of assignment, it's a write
            val children = parent.children
            if (children.isNotEmpty() && children[0] == element) return "WRITE"
        }

        // Check override
        if (parent?.javaClass?.simpleName?.contains("Override") == true) return "OVERRIDE"

        return "READ"
    }
}
