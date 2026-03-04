package com.pudding.mcp.tools.search

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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

class SearchSymbolTool : McpTool {
    override val name = "search_symbol"
    override val description = "Search for symbols (classes, methods, fields) by name"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("q", JsonObject().apply { addProperty("type", "string") })
            add("limit", JsonObject().apply { addProperty("type", "number") })
        })
        add("required", com.google.gson.JsonArray().apply { add("q") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val q = params.string("q") ?: return error("q is required")
        val limit = params.int("limit") ?: 50

        val results = JsonArray()

        runReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val cache = PsiShortNamesCache.getInstance(project)

            // 搜索类
            cache.getClassesByName(q, scope).take(limit / 3).forEach { psiClass ->
                val vf = psiClass.containingFile?.virtualFile ?: return@forEach
                val doc = PsiDocumentManager.getInstance(project).getDocument(psiClass.containingFile!!)
                val line = if (doc != null) doc.getLineNumber(psiClass.textOffset) + 1 else 0
                results.add(JsonObject().apply {
                    addProperty("path", PsiUtils.relativePath(project, vf))
                    addProperty("line", line)
                    addProperty("column", 1)
                    addProperty("preview", "class ${psiClass.name}")
                })
            }

            // 搜索方法
            cache.getMethodsByName(q, scope).take(limit / 3).forEach { method ->
                val vf = method.containingFile?.virtualFile ?: return@forEach
                val doc = PsiDocumentManager.getInstance(project).getDocument(method.containingFile!!)
                val line = if (doc != null) doc.getLineNumber(method.textOffset) + 1 else 0
                results.add(JsonObject().apply {
                    addProperty("path", PsiUtils.relativePath(project, vf))
                    addProperty("line", line)
                    addProperty("column", 1)
                    addProperty("preview", "${method.containingClass?.name}.${method.name}()")
                })
            }

            // 搜索字段
            cache.getFieldsByName(q, scope).take(limit / 3).forEach { field ->
                val vf = field.containingFile?.virtualFile ?: return@forEach
                val doc = PsiDocumentManager.getInstance(project).getDocument(field.containingFile!!)
                val line = if (doc != null) doc.getLineNumber(field.textOffset) + 1 else 0
                results.add(JsonObject().apply {
                    addProperty("path", PsiUtils.relativePath(project, vf))
                    addProperty("line", line)
                    addProperty("column", 1)
                    addProperty("preview", "${field.containingClass?.name}.${field.name}")
                })
            }
        }

        return result { add("results", results) }
    }
}
