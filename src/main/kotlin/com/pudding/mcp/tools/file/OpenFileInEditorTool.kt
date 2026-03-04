package com.pudding.mcp.tools.file

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

class OpenFileInEditorTool : McpTool {
    override val name = "open_file_in_editor"
    override val description = "Open a file in the IDE editor"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
        })
        add("required", com.google.gson.JsonArray().apply { add("filePath") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val vf = PsiUtils.findVirtualFile(project, path) ?: return error("File not found: $path")

        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openFile(vf, true)
        }
        return result { addProperty("success", true) }
    }
}
