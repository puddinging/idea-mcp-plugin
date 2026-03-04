package com.pudding.mcp.tools.file

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.boolOrDefault
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ListDirectoryTreeTool : McpTool {
    override val name = "list_directory_tree"
    override val description = "List directory contents as a tree structure"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("directoryPath", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Path relative to project root, empty string for root")
            })
            add("maxDepth", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "Maximum recursion depth")
            })
        })
        add("required", com.google.gson.JsonArray().apply { add("directoryPath") })
    }

    private val SKIP_DIRS = setOf(".git", ".idea", "node_modules", ".gradle", "build", "out", "target", ".DS_Store")

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val dirPath = params.string("directoryPath") ?: ""
        val maxDepth = params.int("maxDepth")

        val basePath = project.basePath ?: return error("Project base path not found")
        val targetPath = if (dirPath.isEmpty()) basePath else "$basePath/$dirPath"

        val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(targetPath) ?: return error("Directory not found: $dirPath")

        if (!vf.isDirectory) return error("Not a directory: $dirPath")

        val sb = StringBuilder()
        sb.appendLine(vf.path)
        buildTree(vf, sb, "", 0, maxDepth)

        return result { addProperty("tree", sb.toString()) }
    }

    private fun buildTree(dir: VirtualFile, sb: StringBuilder, prefix: String, depth: Int, maxDepth: Int?) {
        if (maxDepth != null && depth >= maxDepth) return
        val children = dir.children
            .filter { it.name !in SKIP_DIRS }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name }))

        children.forEachIndexed { index, child ->
            val isLast = index == children.size - 1
            val connector = if (isLast) "└── " else "├── "
            sb.appendLine("$prefix$connector${child.name}${if (child.isDirectory) "/" else ""}")
            if (child.isDirectory) {
                val newPrefix = prefix + if (isLast) "    " else "│   "
                buildTree(child, sb, newPrefix, depth + 1, maxDepth)
            }
        }
    }
}
