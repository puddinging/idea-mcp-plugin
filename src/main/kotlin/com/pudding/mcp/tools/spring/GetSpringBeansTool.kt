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

class GetSpringBeansTool : McpTool {
    override val name = "get_spring_beans"
    override val description = "Get all Spring beans in the project with their dependencies"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("beanType", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "Filter by type: Controller|Service|Repository|Component|Configuration")
            })
        })
    }

    companion object {
        private val SPRING_ANNOTATIONS = mapOf(
            "Component" to "org.springframework.stereotype.Component",
            "Service" to "org.springframework.stereotype.Service",
            "Controller" to "org.springframework.stereotype.Controller",
            "Repository" to "org.springframework.stereotype.Repository",
            "Configuration" to "org.springframework.context.annotation.Configuration",
            "RestController" to "org.springframework.web.bind.annotation.RestController"
        )
        private const val AUTOWIRED_FQN = "org.springframework.beans.factory.annotation.Autowired"
        private const val INJECT_FQN = "javax.inject.Inject"
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val beanTypeFilter = params.string("beanType")

        return runReadAction {
            val beans = JsonArray()
            val scope = GlobalSearchScope.projectScope(project)
            val facade = JavaPsiFacade.getInstance(project)

            val annotationsToScan = if (beanTypeFilter != null) {
                SPRING_ANNOTATIONS.filter { it.key.equals(beanTypeFilter, ignoreCase = true) }
            } else {
                SPRING_ANNOTATIONS
            }

            for ((typeName, fqn) in annotationsToScan) {
                val annClass = facade.findClass(fqn, GlobalSearchScope.allScope(project)) ?: continue

                AnnotatedElementsSearch.searchPsiClasses(annClass, scope).forEach { psiClass ->
                    val vf = psiClass.containingFile?.virtualFile
                    val doc = psiClass.containingFile?.let {
                        PsiDocumentManager.getInstance(project).getDocument(it)
                    }

                    // Collect dependencies
                    val deps = JsonArray()
                    // Constructor injection
                    psiClass.constructors.forEach { constructor ->
                        constructor.parameterList.parameters.forEach { param ->
                            deps.add(param.type.presentableText)
                        }
                    }
                    // Field injection
                    psiClass.fields.forEach { field ->
                        if (field.hasAnnotation(AUTOWIRED_FQN) || field.hasAnnotation(INJECT_FQN)) {
                            deps.add(field.type.presentableText)
                        }
                    }

                    // Determine scope annotation
                    val scopeAnno = psiClass.getAnnotation("org.springframework.context.annotation.Scope")
                    val scopeValue = scopeAnno?.findAttributeValue("value")?.text
                        ?.trim('"') ?: "singleton"

                    beans.add(JsonObject().apply {
                        addProperty("name", psiClass.name?.replaceFirstChar { it.lowercase() } ?: "")
                        addProperty("qualifiedClassName", psiClass.qualifiedName ?: "")
                        addProperty("filePath", if (vf != null) PsiUtils.relativePath(project, vf) else "")
                        addProperty("line", if (doc != null) doc.getLineNumber(psiClass.textOffset) + 1 else 0)
                        addProperty("scope", scopeValue)
                        addProperty("type", typeName)
                        add("dependencies", deps)
                    })
                }
            }

            result { add("beans", beans) }
        }
    }
}
