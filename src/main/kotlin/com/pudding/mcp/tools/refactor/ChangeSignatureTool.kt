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
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.JavaChangeSignatureUsageProcessor
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo

class ChangeSignatureTool : McpTool {
    override val name = "change_signature"
    override val description = "Change a method's signature (name, parameters, return type, visibility) and update all call sites"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("line", JsonObject().apply { addProperty("type", "number") })
            add("newName", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "New method name, omit to keep current")
            })
            add("visibility", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "private|protected|public|package")
            })
            add("parameters", JsonObject().apply {
                addProperty("type", "array")
                addProperty("description", "New parameter list [{name, type, defaultValue?}]")
                add("items", JsonObject().apply {
                    addProperty("type", "object")
                    add("properties", JsonObject().apply {
                        add("name", JsonObject().apply { addProperty("type", "string") })
                        add("type", JsonObject().apply { addProperty("type", "string") })
                        add("defaultValue", JsonObject().apply { addProperty("type", "string") })
                    })
                })
            })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("filePath"); add("line")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val line = params.int("line") ?: return error("line is required")
        val newName = params.string("newName")
        val visibility = params.string("visibility")

        var success = false
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
                val offset = PsiUtils.lineColumnToOffset(document, line, 1)
                val element = psiFile.findElementAt(offset)
                    ?: run {
                        errorMsg = "No element at line $line"
                        return@runReadAction
                    }

                val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
                    ?: run {
                        errorMsg = "No method at line $line"
                        return@runReadAction
                    }

                val methodName = newName ?: method.name
                val vis = when (visibility) {
                    "public" -> PsiModifier.PUBLIC
                    "protected" -> PsiModifier.PROTECTED
                    "private" -> PsiModifier.PRIVATE
                    "package" -> PsiModifier.PACKAGE_LOCAL
                    else -> method.modifierList.let {
                        when {
                            it.hasModifierProperty(PsiModifier.PUBLIC) -> PsiModifier.PUBLIC
                            it.hasModifierProperty(PsiModifier.PROTECTED) -> PsiModifier.PROTECTED
                            it.hasModifierProperty(PsiModifier.PRIVATE) -> PsiModifier.PRIVATE
                            else -> PsiModifier.PACKAGE_LOCAL
                        }
                    }
                }

                // Build parameter info
                val paramInfos: Array<ParameterInfoImpl>
                val parametersJson = params.getAsJsonArray("parameters")
                if (parametersJson != null && parametersJson.size() > 0) {
                    val factory = JavaPsiFacade.getElementFactory(project)
                    paramInfos = Array(parametersJson.size()) { i ->
                        val p = parametersJson[i].asJsonObject
                        val pName = p.get("name")?.asString ?: "param$i"
                        val pType = p.get("type")?.asString ?: "Object"
                        val defaultValue = p.get("defaultValue")?.asString ?: ""
                        val psiType = factory.createTypeFromText(pType, method)

                        // Check if this parameter existed before (by index)
                        val oldIndex = if (i < method.parameterList.parametersCount) i else -1
                        ParameterInfoImpl(oldIndex, pName, psiType, defaultValue)
                    }
                } else {
                    // Keep existing parameters
                    paramInfos = method.parameterList.parameters.mapIndexed { i, p ->
                        ParameterInfoImpl(i, p.name, p.type)
                    }.toTypedArray()
                }

                val returnType = method.returnType ?: PsiTypes.voidType()

                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        val processor = ChangeSignatureProcessor(
                            project,
                            method,
                            false,  // generateDelegate
                            vis,
                            methodName,
                            returnType,
                            paramInfos,
                            emptyArray<ThrownExceptionInfo>()
                        )
                        processor.run()
                        success = true
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Change signature failed"
                    }
                }
            }
        }

        return if (success) result { addProperty("success", true) }
        else error(errorMsg)
    }
}
