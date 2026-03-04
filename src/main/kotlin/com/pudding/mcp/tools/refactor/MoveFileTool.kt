package com.pudding.mcp.tools.refactor

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor

class MoveFileTool : McpTool {
    override val name = "move_file"
    override val description = "Move a file/class to a new package, updating all imports and references"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("targetPackage", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Target package, e.g. com.example.newpackage")
            })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("filePath"); add("targetPackage")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val targetPackage = params.string("targetPackage") ?: return error("targetPackage is required")

        var success = false
        var errorMsg = ""

        ApplicationManager.getApplication().invokeAndWait {
            com.intellij.openapi.application.runReadAction {
                val psiFile = PsiUtils.findPsiFile(project, path)
                if (psiFile == null) {
                    errorMsg = "File not found: $path"
                    return@runReadAction
                }

                // Find or create the target directory for the package
                val psiPackage = JavaPsiFacade.getInstance(project).findPackage(targetPackage)
                val targetDir = psiPackage?.directories?.firstOrNull()

                if (targetDir == null) {
                    // Try to create the target directory
                    val sourceRoots = com.intellij.openapi.roots.ProjectRootManager.getInstance(project)
                        .contentSourceRoots
                    if (sourceRoots.isEmpty()) {
                        errorMsg = "No source roots found"
                        return@runReadAction
                    }

                    WriteCommandAction.runWriteCommandAction(project) {
                        try {
                            val sourceRoot = sourceRoots.first()
                            var psiDir: PsiDirectory = PsiManager.getInstance(project).findDirectory(sourceRoot)
                                ?: run {
                                    errorMsg = "Cannot access source root"
                                    return@runWriteCommandAction
                                }
                            // Create subdirectories for each package segment
                            for (part in targetPackage.split(".")) {
                                psiDir = psiDir.findSubdirectory(part)
                                    ?: psiDir.createSubdirectory(part)
                            }
                            val createdDir = psiDir

                            MoveFilesOrDirectoriesProcessor(
                                project,
                                arrayOf(psiFile),
                                createdDir,
                                false, true, true, null, null
                            ).run()
                            success = true
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Move failed"
                        }
                    }
                } else {
                    WriteCommandAction.runWriteCommandAction(project) {
                        try {
                            MoveFilesOrDirectoriesProcessor(
                                project,
                                arrayOf(psiFile),
                                targetDir,
                                false, true, true, null, null
                            ).run()
                            success = true
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Move failed"
                        }
                    }
                }
            }
        }

        return if (success) result { addProperty("success", true) }
        else error(errorMsg)
    }
}
