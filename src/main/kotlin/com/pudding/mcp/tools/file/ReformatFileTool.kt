package com.pudding.mcp.tools.file

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager

class ReformatFileTool : McpTool {
    override val name = "reformat_file"
    override val description = "Reformat a file according to the project code style"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("path", JsonObject().apply { addProperty("type", "string") })
        })
        add("required", com.google.gson.JsonArray().apply { add("path") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("path") ?: return error("path is required")
        val psiFile = PsiUtils.findPsiFile(project, path) ?: return error("File not found: $path")

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                CodeStyleManager.getInstance(project).reformat(psiFile)
            }
        }
        return result { addProperty("success", true) }
    }
}
