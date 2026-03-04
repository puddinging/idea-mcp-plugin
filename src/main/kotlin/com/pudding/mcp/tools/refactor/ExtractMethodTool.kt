package com.pudding.mcp.tools.refactor

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.int
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor

class ExtractMethodTool : McpTool {
    override val name = "extract_method"
    override val description = "Extract a code block into a new method, automatically analyzing parameters and return values"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("startLine", JsonObject().apply { addProperty("type", "number") })
            add("startColumn", JsonObject().apply { addProperty("type", "number") })
            add("endLine", JsonObject().apply { addProperty("type", "number") })
            add("endColumn", JsonObject().apply { addProperty("type", "number") })
            add("methodName", JsonObject().apply { addProperty("type", "string") })
            add("visibility", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "private|protected|public, default private")
            })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("filePath"); add("startLine"); add("startColumn")
            add("endLine"); add("endColumn"); add("methodName")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val startLine = params.int("startLine") ?: return error("startLine is required")
        val startColumn = params.int("startColumn") ?: return error("startColumn is required")
        val endLine = params.int("endLine") ?: return error("endLine is required")
        val endColumn = params.int("endColumn") ?: return error("endColumn is required")
        val methodName = params.string("methodName") ?: return error("methodName is required")

        var success = false
        var newMethodLine = 0
        var errorMsg = ""

        ApplicationManager.getApplication().invokeAndWait {
            com.intellij.openapi.application.runReadAction {
                val psiFile = PsiUtils.findPsiFile(project, path)
                if (psiFile == null) {
                    errorMsg = "File not found: $path"
                    return@runReadAction
                }
                val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                if (document == null) {
                    errorMsg = "Cannot get document"
                    return@runReadAction
                }

                val startOffset = PsiUtils.lineColumnToOffset(document, startLine, startColumn)
                val endOffset = PsiUtils.lineColumnToOffset(document, endLine, endColumn)

                // Collect all elements in the range
                val elements = mutableListOf<PsiElement>()
                var current = psiFile.findElementAt(startOffset)
                while (current != null && current.textOffset < endOffset) {
                    elements.add(current)
                    current = PsiTreeUtil.nextLeaf(current)
                }

                if (elements.isEmpty()) {
                    errorMsg = "No elements found in the specified range"
                    return@runReadAction
                }

                // Find the common parent statements
                val firstElement = psiFile.findElementAt(startOffset)
                val lastElement = psiFile.findElementAt(endOffset - 1)
                if (firstElement == null || lastElement == null) {
                    errorMsg = "Cannot find elements at the specified range"
                    return@runReadAction
                }

                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        val processor = ExtractMethodProcessor(
                            project, null,
                            arrayOf(firstElement, lastElement),
                            null,
                            "Extract Method",
                            methodName,
                            null
                        )

                        if (processor.prepare()) {
                            processor.doRefactoring()
                            success = true
                            newMethodLine = startLine
                        } else {
                            errorMsg = "Cannot extract method from the selected code"
                        }
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Extract method failed"
                    }
                }
            }
        }

        return if (success) result {
            addProperty("success", true)
            addProperty("newMethodLine", newMethodLine)
        } else error(errorMsg)
    }
}
