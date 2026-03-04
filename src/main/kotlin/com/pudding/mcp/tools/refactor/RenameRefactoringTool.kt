package com.pudding.mcp.tools.refactor

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.refactoring.rename.RenameProcessor

class RenameRefactoringTool : McpTool {
    override val name = "rename_refactoring"
    override val description = "Rename a symbol and update all references throughout the project"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("pathInProject", JsonObject().apply { addProperty("type", "string") })
            add("symbolName", JsonObject().apply { addProperty("type", "string") })
            add("newName", JsonObject().apply { addProperty("type", "string") })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("pathInProject"); add("symbolName"); add("newName")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val symbolName = params.string("symbolName") ?: return error("symbolName is required")
        val newName = params.string("newName") ?: return error("newName is required")

        var success = false
        var errorMsg = ""

        ApplicationManager.getApplication().invokeAndWait {
            com.intellij.openapi.application.runReadAction {
                val scope = GlobalSearchScope.projectScope(project)
                val cache = PsiShortNamesCache.getInstance(project)

                // 先找类，再找方法，再找字段
                val element: PsiNamedElement? =
                    cache.getClassesByName(symbolName, scope).firstOrNull()
                    ?: cache.getMethodsByName(symbolName, scope).firstOrNull()
                    ?: cache.getFieldsByName(symbolName, scope).firstOrNull()

                if (element == null) {
                    errorMsg = "Symbol not found: $symbolName"
                    return@runReadAction
                }

                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        RenameProcessor(project, element, newName, false, false).run()
                        success = true
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Rename failed"
                    }
                }
            }
        }

        return if (success) result { addProperty("success", true) }
               else error(errorMsg)
    }
}
