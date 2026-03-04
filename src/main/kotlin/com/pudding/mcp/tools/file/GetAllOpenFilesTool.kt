package com.pudding.mcp.tools.file

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.result
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

class GetAllOpenFilesTool : McpTool {
    override val name = "get_all_open_file_paths"
    override val description = "Get paths of all currently open files in the editor"
    override val inputSchema = JsonObject().apply { addProperty("type", "object") }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        var activeFile = ""
        val openFiles = JsonArray()

        ApplicationManager.getApplication().invokeAndWait {
            val fem = FileEditorManager.getInstance(project)
            fem.selectedFiles.firstOrNull()?.let {
                activeFile = PsiUtils.relativePath(project, it)
            }
            fem.openFiles.forEach { vf ->
                openFiles.add(PsiUtils.relativePath(project, vf))
            }
        }

        return result {
            addProperty("activeFilePath", activeFile)
            add("openFiles", openFiles)
        }
    }
}
