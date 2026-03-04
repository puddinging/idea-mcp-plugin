package com.pudding.mcp.tools.file

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.boolOrDefault
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil

class ReplaceTextInFileTool : McpTool {
    override val name = "replace_text_in_file"
    override val description = "Find and replace text in a file"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("pathInProject", JsonObject().apply { addProperty("type", "string") })
            add("oldText", JsonObject().apply { addProperty("type", "string") })
            add("newText", JsonObject().apply { addProperty("type", "string") })
            add("replaceAll", JsonObject().apply { addProperty("type", "boolean") })
            add("caseSensitive", JsonObject().apply { addProperty("type", "boolean") })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("pathInProject"); add("oldText"); add("newText")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("pathInProject") ?: return error("pathInProject is required")
        val oldText = params.string("oldText") ?: return error("oldText is required")
        val newText = params.string("newText") ?: return error("newText is required")
        val replaceAll = params.boolOrDefault("replaceAll", true)
        val caseSensitive = params.boolOrDefault("caseSensitive", true)

        val vf = PsiUtils.findVirtualFile(project, path) ?: return error("File not found: $path")

        var occurrences = 0
        var errorMsg = ""

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.runWriteCommandAction(project) {
                try {
                    val content = VfsUtil.loadText(vf)
                    val ignoreCase = !caseSensitive

                    val newContent = if (replaceAll) {
                        var count = 0
                        var idx = content.indexOf(oldText, ignoreCase = ignoreCase)
                        val sb = StringBuilder(content)
                        var offset = 0
                        while (idx >= 0) {
                            sb.replace(idx + offset, idx + offset + oldText.length, newText)
                            offset += newText.length - oldText.length
                            count++
                            idx = content.indexOf(oldText, idx + oldText.length, ignoreCase = ignoreCase)
                        }
                        occurrences = count
                        sb.toString()
                    } else {
                        val idx = content.indexOf(oldText, ignoreCase = ignoreCase)
                        if (idx >= 0) {
                            occurrences = 1
                            content.substring(0, idx) + newText + content.substring(idx + oldText.length)
                        } else content
                    }

                    VfsUtil.saveText(vf, newContent)
                } catch (e: Exception) {
                    errorMsg = e.message ?: "Unknown error"
                }
            }
        }

        return if (errorMsg.isEmpty()) result {
            addProperty("result", "ok")
            addProperty("occurrences", occurrences)
        } else error(errorMsg)
    }
}
