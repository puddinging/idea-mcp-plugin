package com.pudding.mcp.tools.file

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.SchemaUtils.boolOrDefault
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

class CreateNewFileTool : McpTool {
    override val name = "create_new_file"
    override val description = "Create a new file with optional initial content, auto-creates parent directories"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("pathInProject", JsonObject().apply { addProperty("type", "string") })
            add("text", JsonObject().apply { addProperty("type", "string") })
            add("overwrite", JsonObject().apply { addProperty("type", "boolean") })
        })
        add("required", com.google.gson.JsonArray().apply { add("pathInProject") })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val relativePath = params.string("pathInProject") ?: return error("pathInProject is required")
        val text = params.string("text") ?: ""
        val overwrite = params.boolOrDefault("overwrite", false)
        val basePath = project.basePath ?: return error("Project base path not found")

        val file = File("$basePath/$relativePath")
        if (file.exists() && !overwrite) return error("File already exists: $relativePath")

        var success = false
        var errorMsg = ""

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    file.parentFile?.mkdirs()
                    file.writeText(text)
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                    success = true
                } catch (e: Exception) {
                    errorMsg = e.message ?: "Unknown error"
                }
            }
        }

        return if (success) result {
            addProperty("success", true)
            addProperty("path", relativePath)
        } else error(errorMsg)
    }
}
