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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil

class TypeHierarchyTool : McpTool {
    override val name = "get_type_hierarchy"
    override val description = "Get the type hierarchy (supertypes or subtypes) for a class/interface"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("line", JsonObject().apply { addProperty("type", "number") })
            add("direction", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "supers|subtypes, default subtypes")
            })
            add("depth", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "Max depth, default 3")
            })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("filePath"); add("line")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val line = params.int("line") ?: return error("line is required")
        val direction = params.stringOrDefault("direction", "subtypes")
        val depth = params.intOrDefault("depth", 3).coerceIn(1, 10)

        return runReadAction {
            val psiFile = PsiUtils.findPsiFile(project, path)
                ?: return@runReadAction error("File not found: $path")
            val doc = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                ?: return@runReadAction error("Cannot get document")
            val offset = PsiUtils.lineColumnToOffset(doc, line, 1)
            val element = psiFile.findElementAt(offset)
                ?: return@runReadAction error("No element at position")
            val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                ?: return@runReadAction error("No class at position")

            val tree = if (direction == "supers") {
                buildSupersTree(project, psiClass, depth, 0)
            } else {
                buildSubtypesTree(project, psiClass, depth, 0)
            }

            result { add("tree", tree) }
        }
    }

    private fun buildSupersTree(project: Project, psiClass: PsiClass, maxDepth: Int, currentDepth: Int): JsonObject {
        val node = classToNode(project, psiClass)

        if (currentDepth >= maxDepth) {
            node.add("children", JsonArray())
            return node
        }

        val children = JsonArray()
        psiClass.superClass?.let { superClass ->
            if (superClass.qualifiedName != "java.lang.Object") {
                children.add(buildSupersTree(project, superClass, maxDepth, currentDepth + 1))
            }
        }
        psiClass.interfaces.forEach { iface ->
            children.add(buildSupersTree(project, iface, maxDepth, currentDepth + 1))
        }
        node.add("children", children)
        return node
    }

    private fun buildSubtypesTree(project: Project, psiClass: PsiClass, maxDepth: Int, currentDepth: Int): JsonObject {
        val node = classToNode(project, psiClass)

        if (currentDepth >= maxDepth) {
            node.add("children", JsonArray())
            return node
        }

        val children = JsonArray()
        val scope = GlobalSearchScope.projectScope(project)
        ClassInheritorsSearch.search(psiClass, scope, false).forEach { inheritor ->
            children.add(buildSubtypesTree(project, inheritor, maxDepth, currentDepth + 1))
        }
        node.add("children", children)
        return node
    }

    private fun classToNode(project: Project, psiClass: PsiClass): JsonObject {
        val kind = when {
            psiClass.isInterface -> "INTERFACE"
            psiClass.hasModifierProperty(PsiModifier.ABSTRACT) -> "ABSTRACT"
            else -> "CLASS"
        }
        val vf = psiClass.containingFile?.virtualFile
        val doc = psiClass.containingFile?.let { PsiDocumentManager.getInstance(project).getDocument(it) }
        return JsonObject().apply {
            addProperty("name", psiClass.name ?: "")
            addProperty("qualifiedName", psiClass.qualifiedName ?: "")
            addProperty("filePath", if (vf != null) PsiUtils.relativePath(project, vf) else "")
            addProperty("line", if (doc != null) doc.getLineNumber(psiClass.textOffset) + 1 else 0)
            addProperty("kind", kind)
        }
    }
}
