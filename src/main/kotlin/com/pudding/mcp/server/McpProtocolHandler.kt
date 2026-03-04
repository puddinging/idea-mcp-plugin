package com.pudding.mcp.server

import com.google.gson.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

object McpProtocolHandler {
    private val LOG = Logger.getInstance(McpProtocolHandler::class.java)
    private val gson = Gson()

    fun handle(project: Project, body: String): String {
        return try {
            val req = JsonParser.parseString(body).asJsonObject
            val id = req.get("id")
            when (req.get("method")?.asString) {
                "initialize"        -> handleInitialize(id)
                "notifications/initialized" -> "{}" // no-op
                "tools/list"        -> handleToolsList(id)
                "tools/call"        -> handleToolCall(project, id, req)
                else                -> error(id, -32601, "Method not found: ${req.get("method")?.asString}")
            }
        } catch (e: Exception) {
            LOG.warn("MCP handle error", e)
            error(JsonNull.INSTANCE, -32700, "Parse error: ${e.message}")
        }
    }

    private fun handleInitialize(id: JsonElement) = gson.toJson(mapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "result" to mapOf(
            "protocolVersion" to "2024-11-05",
            "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
            "serverInfo" to mapOf("name" to "idea-mcp", "version" to "1.0.0")
        )
    ))

    private fun handleToolsList(id: JsonElement) = gson.toJson(mapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "result" to mapOf("tools" to ToolRegistry.listAll().map { tool ->
            mapOf(
                "name" to tool.name,
                "description" to tool.description,
                "inputSchema" to tool.inputSchema
            )
        })
    ))

    private fun handleToolCall(project: Project, id: JsonElement, req: JsonObject): String {
        val params = req.getAsJsonObject("params")
            ?: return error(id, -32602, "Missing params")
        val toolName = params.get("name")?.asString
            ?: return error(id, -32602, "Missing tool name")
        val args = params.getAsJsonObject("arguments") ?: JsonObject()

        val tool = ToolRegistry.get(toolName)
            ?: return error(id, -32602, "Unknown tool: $toolName")

        return try {
            val result = tool.execute(project, args)
            gson.toJson(mapOf(
                "jsonrpc" to "2.0",
                "id" to id,
                "result" to mapOf(
                    "content" to listOf(mapOf("type" to "text", "text" to result.toString()))
                )
            ))
        } catch (e: Exception) {
            LOG.warn("Tool execution error: $toolName", e)
            error(id, -32603, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun error(id: JsonElement, code: Int, message: String) = gson.toJson(mapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "error" to mapOf("code" to code, "message" to message)
    ))
}
