package com.pudding.mcp.tools.generate

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil

class GenerateCodeTool : McpTool {
    override val name = "generate_code"
    override val description = "Generate boilerplate code (constructor, getter/setter, toString, equals/hashCode, override methods)"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply { addProperty("type", "string") })
            add("type", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "constructor|getter_setter|toString|equals_hashcode|override_methods")
            })
            add("targetClass", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Class name, defaults to the primary class in the file")
            })
        })
        add("required", com.google.gson.JsonArray().apply {
            add("filePath"); add("type")
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath") ?: return error("filePath is required")
        val type = params.string("type") ?: return error("type is required")
        val targetClassName = params.string("targetClass")

        var success = false
        var errorMsg = ""

        ApplicationManager.getApplication().invokeAndWait {
            com.intellij.openapi.application.runReadAction {
                val psiFile = PsiUtils.findPsiFile(project, path)
                if (psiFile !is PsiJavaFile) {
                    errorMsg = "Not a Java file: $path"
                    return@runReadAction
                }

                val psiClass = if (targetClassName != null) {
                    PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                        .find { it.name == targetClassName }
                } else {
                    psiFile.classes.firstOrNull()
                }

                if (psiClass == null) {
                    errorMsg = "Class not found in file"
                    return@runReadAction
                }

                WriteCommandAction.runWriteCommandAction(project) {
                    try {
                        val factory = JavaPsiFacade.getElementFactory(project)
                        val codeStyleManager = CodeStyleManager.getInstance(project)

                        when (type) {
                            "constructor" -> {
                                val fields = psiClass.fields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
                                val paramList = fields.joinToString(", ") { "${it.type.presentableText} ${it.name}" }
                                val bodyLines = fields.joinToString("\n") { "    this.${it.name} = ${it.name};" }
                                val constructorText = """
                                    public ${psiClass.name}($paramList) {
                                    $bodyLines
                                    }
                                """.trimIndent()
                                val constructor = factory.createMethodFromText(constructorText, psiClass)
                                val added = psiClass.addAfter(constructor, psiClass.lBrace)
                                codeStyleManager.reformat(added)
                                success = true
                            }

                            "getter_setter" -> {
                                val fields = psiClass.fields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
                                for (field in fields) {
                                    val fieldName = field.name
                                    val typeName = field.type.presentableText
                                    val capitalName = fieldName.replaceFirstChar { it.uppercase() }

                                    // Getter
                                    val prefix = if (typeName == "boolean") "is" else "get"
                                    val getterText = "public $typeName $prefix$capitalName() { return this.$fieldName; }"
                                    val getter = factory.createMethodFromText(getterText, psiClass)
                                    val addedGetter = psiClass.addBefore(getter, psiClass.rBrace)
                                    codeStyleManager.reformat(addedGetter)

                                    // Setter
                                    val setterText = "public void set$capitalName($typeName $fieldName) { this.$fieldName = $fieldName; }"
                                    val setter = factory.createMethodFromText(setterText, psiClass)
                                    val addedSetter = psiClass.addBefore(setter, psiClass.rBrace)
                                    codeStyleManager.reformat(addedSetter)
                                }
                                success = true
                            }

                            "toString" -> {
                                val fields = psiClass.fields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
                                val fieldsStr = fields.joinToString(" + \", \" + ") {
                                    "\"${it.name}=\" + ${it.name}"
                                }
                                val toStringText = """
                                    @Override
                                    public String toString() {
                                        return "${psiClass.name}{" + $fieldsStr + "}";
                                    }
                                """.trimIndent()
                                val method = factory.createMethodFromText(toStringText, psiClass)
                                val added = psiClass.addBefore(method, psiClass.rBrace)
                                codeStyleManager.reformat(added)
                                success = true
                            }

                            "equals_hashcode" -> {
                                val fields = psiClass.fields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }
                                val className = psiClass.name

                                // equals
                                val equalsChecks = fields.joinToString(" && ") { field ->
                                    val name = field.name
                                    if (field.type is PsiPrimitiveType) {
                                        "$name == that.$name"
                                    } else {
                                        "java.util.Objects.equals($name, that.$name)"
                                    }
                                }
                                val equalsText = """
                                    @Override
                                    public boolean equals(Object o) {
                                        if (this == o) return true;
                                        if (o == null || getClass() != o.getClass()) return false;
                                        $className that = ($className) o;
                                        return ${if (equalsChecks.isEmpty()) "true" else equalsChecks};
                                    }
                                """.trimIndent()

                                // hashCode
                                val hashFields = fields.joinToString(", ") { it.name }
                                val hashCodeText = """
                                    @Override
                                    public int hashCode() {
                                        return java.util.Objects.hash($hashFields);
                                    }
                                """.trimIndent()

                                val equalsMethod = factory.createMethodFromText(equalsText, psiClass)
                                val hashMethod = factory.createMethodFromText(hashCodeText, psiClass)
                                val addedEquals = psiClass.addBefore(equalsMethod, psiClass.rBrace)
                                codeStyleManager.reformat(addedEquals)
                                val addedHash = psiClass.addBefore(hashMethod, psiClass.rBrace)
                                codeStyleManager.reformat(addedHash)
                                success = true
                            }

                            "override_methods" -> {
                                // Find all abstract/interface methods that need implementation
                                val methodsToOverride = mutableListOf<PsiMethod>()

                                psiClass.superClass?.allMethods?.forEach { method ->
                                    if (method.hasModifierProperty(PsiModifier.ABSTRACT) &&
                                        psiClass.findMethodBySignature(method, false) == null) {
                                        methodsToOverride.add(method)
                                    }
                                }
                                psiClass.interfaces.forEach { iface ->
                                    iface.allMethods.forEach { method ->
                                        if (psiClass.findMethodBySignature(method, false) == null &&
                                            !method.hasModifierProperty(PsiModifier.DEFAULT)) {
                                            methodsToOverride.add(method)
                                        }
                                    }
                                }

                                for (method in methodsToOverride) {
                                    val returnType = method.returnType?.presentableText ?: "void"
                                    val paramList = method.parameterList.parameters.joinToString(", ") {
                                        "${it.type.presentableText} ${it.name}"
                                    }
                                    val body = if (returnType == "void") "" else {
                                        when (returnType) {
                                            "int", "long", "short", "byte", "char" -> "return 0;"
                                            "float", "double" -> "return 0.0;"
                                            "boolean" -> "return false;"
                                            else -> "return null;"
                                        }
                                    }
                                    val methodText = """
                                        @Override
                                        public $returnType ${method.name}($paramList) {
                                            $body
                                        }
                                    """.trimIndent()
                                    val newMethod = factory.createMethodFromText(methodText, psiClass)
                                    val added = psiClass.addBefore(newMethod, psiClass.rBrace)
                                    codeStyleManager.reformat(added)
                                }
                                success = true
                            }

                            else -> errorMsg = "Unknown generation type: $type"
                        }
                    } catch (e: Exception) {
                        errorMsg = e.message ?: "Code generation failed"
                    }
                }
            }
        }

        return if (success) result {
            addProperty("success", true)
        } else error(errorMsg)
    }
}
