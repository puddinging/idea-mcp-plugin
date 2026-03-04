package com.pudding.mcp.tools.navigation

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.pudding.mcp.util.SchemaUtils.stringOrDefault
import com.pudding.mcp.util.SchemaUtils.intOrDefault
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

class CallHierarchyTool : McpTool {
    override val name = "get_call_hierarchy"
    override val description = "Get the call hierarchy of a method (callers or callees)"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("line", JsonObject().apply { addProperty("type", "number") })
            add("column", JsonObject().apply { addProperty("type", "number") })
            add("direction", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "callers|callees, default callers")
            })
            add("depth", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "Max depth, default 3, max 10")
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
        val direction = params.stringOrDefault("direction", "callers")
        val depth = params.intOrDefault("depth", 3).coerceIn(1, 10)

        return runReadAction {
            val psiFile = PsiUtils.findPsiFile(project, path)
                ?: return@runReadAction error("File not found: $path")
            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runReadAction error("Cannot get document")
            val offset = PsiUtils.lineColumnToOffset(doc, line, column)
            val element = psiFile.findElementAt(offset)
                ?: return@runReadAction error("No element at position")
            val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
                ?: return@runReadAction error("No method at position")

            val tree = if (direction == "callees") {
                buildCalleesTree(project, method, depth, 0)
            } else {
                buildCallersTree(project, method, depth, 0)
            }

            result { add("tree", tree) }
        }
    }

    private fun buildCallersTree(project: Project, method: PsiMethod, maxDepth: Int, currentDepth: Int): JsonObject {
        val node = JsonObject().apply {
            addProperty("name", "${method.containingClass?.name ?: ""}.${method.name}")
            val vf = method.containingFile?.virtualFile
            addProperty("filePath", if (vf != null) PsiUtils.relativePath(project, vf) else "")
            val doc = PsiDocumentManager.getInstance(project).getDocument(method.containingFile)
            addProperty("line", if (doc != null) doc.getLineNumber(method.textOffset) + 1 else 0)
        }

        if (currentDepth >= maxDepth) {
            node.add("children", JsonArray())
            return node
        }

        val children = JsonArray()
        val scope = GlobalSearchScope.projectScope(project)
        MethodReferencesSearch.search(method, scope, true).forEach { ref ->
            val callerMethod = PsiTreeUtil.getParentOfType(ref.element, PsiMethod::class.java)
            if (callerMethod != null && callerMethod != method) {
                children.add(buildCallersTree(project, callerMethod, maxDepth, currentDepth + 1))
            }
        }
        node.add("children", children)
        return node
    }

    private fun buildCalleesTree(project: Project, method: PsiMethod, maxDepth: Int, currentDepth: Int): JsonObject {
        val node = JsonObject().apply {
            addProperty("name", "${method.containingClass?.name ?: ""}.${method.name}")
            val vf = method.containingFile?.virtualFile
            addProperty("filePath", if (vf != null) PsiUtils.relativePath(project, vf) else "")
            val doc = PsiDocumentManager.getInstance(project).getDocument(method.containingFile)
            addProperty("line", if (doc != null) doc.getLineNumber(method.textOffset) + 1 else 0)
        }

        if (currentDepth >= maxDepth) {
            node.add("children", JsonArray())
            return node
        }

        val children = JsonArray()
        val callExpressions = PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java)
        val visited = mutableSetOf<PsiMethod>()
        for (call in callExpressions) {
            val resolved = call.resolveMethod() ?: continue
            if (resolved in visited || resolved == method) continue
            // Only include project methods, not library methods
            if (resolved.containingFile?.virtualFile?.let {
                    GlobalSearchScope.projectScope(project).contains(it)
                } == true) {
                visited.add(resolved)
                children.add(buildCalleesTree(project, resolved, maxDepth, currentDepth + 1))
            }
        }
        node.add("children", children)
        return node
    }
}
