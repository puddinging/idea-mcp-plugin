package com.pudding.mcp.tools.symbol

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

class GetSymbolInfoTool : McpTool {
    override val name = "get_symbol_info"
    override val description = "Get type, signature, and documentation for the symbol at a given position"
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
            val psiFile = PsiUtils.findPsiFile(project, path) ?: return@runReadAction error("File not found: $path")
            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runReadAction error("Cannot get document")
            val offset = PsiUtils.lineColumnToOffset(doc, line, column)
            val element = psiFile.findElementAt(offset) ?: return@runReadAction error("No element at position")
            val named = PsiTreeUtil.getParentOfType(element, PsiNamedElement::class.java)
                ?: return@runReadAction error("No named element at position")

            val docText = try {
                DocumentationProvider.EP_NAME.extensionList
                    .firstNotNullOfOrNull { it.generateDoc(named, element) }
            } catch (_: Exception) { null }

            result {
                addProperty("name", named.name ?: "")
                addProperty("type", named.javaClass.simpleName)
                addProperty("documentation", docText ?: "")
                addProperty("declaredIn", path)
                addProperty("line", line)
                addProperty("column", column)
            }
        }
    }
}
