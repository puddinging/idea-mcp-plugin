package com.pudding.mcp.tools.spring

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch

class GetRequestMappingsTool : McpTool {
    override val name = "get_request_mappings"
    override val description = "Get all Spring MVC HTTP routes and parameter structures"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("httpMethod", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Filter by HTTP method: GET|POST|PUT|DELETE|PATCH")
            })
        })
    }

    companion object {
        private val MAPPING_ANNOTATIONS = mapOf(
            "RequestMapping" to "org.springframework.web.bind.annotation.RequestMapping",
            "GetMapping" to "org.springframework.web.bind.annotation.GetMapping",
            "PostMapping" to "org.springframework.web.bind.annotation.PostMapping",
            "PutMapping" to "org.springframework.web.bind.annotation.PutMapping",
            "DeleteMapping" to "org.springframework.web.bind.annotation.DeleteMapping",
            "PatchMapping" to "org.springframework.web.bind.annotation.PatchMapping"
        )

        private val ANNOTATION_TO_METHOD = mapOf(
            "GetMapping" to "GET",
            "PostMapping" to "POST",
            "PutMapping" to "PUT",
            "DeleteMapping" to "DELETE",
            "PatchMapping" to "PATCH"
        )
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val httpMethodFilter = params.string("httpMethod")?.uppercase()

        return runReadAction {
            val routes = JsonArray()
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)

            for ((annoName, fqn) in MAPPING_ANNOTATIONS) {
                val annClass = facade.findClass(fqn, GlobalSearchScope.allScope(project)) ?: continue

                // Search methods annotated with this mapping
                AnnotatedElementsSearch.searchPsiMethods(annClass, scope).forEach { method ->
                    val annotation = method.getAnnotation(fqn) ?: return@forEach

                    // Determine HTTP method
                    val httpMethod = if (annoName == "RequestMapping") {
                        val methodAttr = annotation.findAttributeValue("method")?.text
                            ?.replace("RequestMethod.", "")
                            ?.trim('{', '}', ' ')
                            ?: "GET"
                        methodAttr
                    } else {
                        ANNOTATION_TO_METHOD[annoName] ?: "GET"
                    }

                    if (httpMethodFilter != null && !httpMethod.contains(httpMethodFilter)) return@forEach

                    // Get path
                    val pathValue = extractAnnotationValue(annotation) ?: ""

                    // Get class-level RequestMapping path
                    val containingClass = method.containingClass
                    val classMapping = containingClass?.getAnnotation(
                        "org.springframework.web.bind.annotation.RequestMapping"
                    )
                    val classPath = if (classMapping != null) extractAnnotationValue(classMapping) ?: "" else ""

                    val fullPath = "${classPath.trimEnd('/')}/${pathValue.trimStart('/')}"
                        .replace("//", "/")

                    // Collect parameters
                    val parameters = JsonArray()
                    method.parameterList.parameters.forEach { param ->
                        val source = when {
                            param.hasAnnotation("org.springframework.web.bind.annotation.PathVariable") -> "PATH"
                            param.hasAnnotation("org.springframework.web.bind.annotation.RequestParam") -> "QUERY"
                            param.hasAnnotation("org.springframework.web.bind.annotation.RequestBody") -> "BODY"
                            param.hasAnnotation("org.springframework.web.bind.annotation.RequestHeader") -> "HEADER"
                            else -> "QUERY"
                        }
                        parameters.add(JsonObject().apply {
                            addProperty("name", param.name)
                            addProperty("type", param.type.presentableText)
                            addProperty("source", source)
                        })
                    }

                    val vf = method.containingFile?.virtualFile
                    val doc = method.containingFile?.let {
                        PsiDocumentManager.getInstance(project).getDocument(it)
                    }

                    routes.add(JsonObject().apply {
                        addProperty("path", fullPath)
                        addProperty("httpMethod", httpMethod)
                        addProperty("controllerClass", containingClass?.name ?: "")
                        addProperty("methodName", method.name)
                        addProperty("filePath", if (vf != null) PsiUtils.relativePath(project, vf) else "")
                        addProperty("line", if (doc != null) doc.getLineNumber(method.textOffset) + 1 else 0)
                        add("parameters", parameters)
                    })
                }
            }

            result { add("routes", routes) }
        }
    }

    private fun extractAnnotationValue(annotation: PsiAnnotation): String? {
        val value = annotation.findAttributeValue("value")
            ?: annotation.findAttributeValue("path")
        return when {
            value == null -> null
            value is PsiLiteralExpression -> value.value?.toString()
            value is PsiArrayInitializerMemberValue -> {
                value.initializers.firstOrNull()?.let {
                    (it as? PsiLiteralExpression)?.value?.toString()
                }
            }
            else -> value.text?.trim('"', '{', '}', ' ')
        }
    }
}
