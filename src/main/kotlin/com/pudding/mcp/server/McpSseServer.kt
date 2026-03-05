package com.pudding.mcp.server

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class McpSseServer {
    private val LOG = Logger.getInstance(McpSseServer::class.java)
    private var server: HttpServer? = null
    private val sseSessions = ConcurrentHashMap<String, SseSession>()

    private class SseSession(
        val exchange: HttpExchange,
        val outputStream: OutputStream
    )

    companion object {
        @Volatile
        private var instance: McpSseServer? = null

        fun getInstance(): McpSseServer {
            return instance ?: synchronized(this) {
                instance ?: McpSseServer().also { instance = it }
            }
        }
    }

    fun isRunning(): Boolean = server != null

    fun start(port: Int) {
        start(project = null, port = port)
    }

    fun start(project: Project?, port: Int) {
        if (server != null) {
            LOG.info("MCP Server already running on port $port")
            return
        }
        try {
            server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)

            // SSE 长连接端点
            server!!.createContext("/sse") { exchange ->
                if (exchange.requestMethod == "OPTIONS") {
                    handleCors(exchange)
                    return@createContext
                }
                val sessionId = UUID.randomUUID().toString()
                try {
                    exchange.responseHeaders.apply {
                        add("Content-Type", "text/event-stream")
                        add("Cache-Control", "no-cache")
                        add("Connection", "keep-alive")
                        add("Access-Control-Allow-Origin", "*")
                    }
                    exchange.sendResponseHeaders(200, 0)
                    val out = exchange.responseBody

                    sseSessions[sessionId] = SseSession(exchange, out)

                    // 告知客户端消息端点地址（包含 sessionId）
                    out.write("event: endpoint\ndata: /message?sessionId=$sessionId\n\n".toByteArray())
                    out.flush()

                    LOG.info("SSE client connected: $sessionId")

                    // 保持连接，直到客户端断开
                    try {
                        while (true) {
                            Thread.sleep(15000)
                            out.write(": keepalive\n\n".toByteArray())
                            out.flush()
                        }
                    } catch (_: IOException) {
                        // 客户端断开连接
                    } catch (_: InterruptedException) {
                        // 服务器关闭
                    } finally {
                        sseSessions.remove(sessionId)
                        try { out.close() } catch (_: Exception) {}
                        LOG.info("SSE client disconnected: $sessionId")
                    }
                } catch (e: Exception) {
                    sseSessions.remove(sessionId)
                    LOG.warn("SSE connection error", e)
                }
            }

            // 消息接收端点
            server!!.createContext("/message") { exchange ->
                if (exchange.requestMethod == "OPTIONS") {
                    handleCors(exchange)
                    return@createContext
                }
                try {
                    val query = exchange.requestURI.query ?: ""
                    val sessionId = query.split("&")
                        .map { it.split("=", limit = 2) }
                        .firstOrNull { it[0] == "sessionId" }
                        ?.getOrNull(1)

                    if (sessionId == null) {
                        val err = """{"error":"Missing sessionId"}""".toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(400, err.size.toLong())
                        exchange.responseBody.use { it.write(err) }
                        return@createContext
                    }

                    val session = sseSessions[sessionId]
                    if (session == null) {
                        val err = """{"error":"Unknown session"}""".toByteArray()
                        exchange.responseHeaders.add("Content-Type", "application/json")
                        exchange.sendResponseHeaders(400, err.size.toLong())
                        exchange.responseBody.use { it.write(err) }
                        return@createContext
                    }

                    val body = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                    val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
                        ?: throw IllegalStateException("No open project")
                    val response = McpProtocolHandler.handle(currentProject, body)

                    // 通过 SSE 流发送响应
                    synchronized(session.outputStream) {
                        session.outputStream.write("event: message\ndata: $response\n\n".toByteArray())
                        session.outputStream.flush()
                    }

                    // POST 返回 202 Accepted
                    exchange.responseHeaders.apply {
                        add("Access-Control-Allow-Origin", "*")
                    }
                    exchange.sendResponseHeaders(202, -1)
                } catch (e: Exception) {
                    LOG.warn("Message handler error", e)
                    val err = """{"error":"${e.message}"}""".toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(500, err.size.toLong())
                    exchange.responseBody.use { it.write(err) }
                }
            }

            // 健康检查端点
            server!!.createContext("/health") { exchange ->
                val body = """{"status":"ok","tools":${ToolRegistry.listAll().size}}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }

            server!!.executor = Executors.newCachedThreadPool()
            server!!.start()
            LOG.info("MCP Server started on http://127.0.0.1:$port")
        } catch (e: Exception) {
            LOG.error("Failed to start MCP Server on port $port", e)
        }
    }

    fun stop() {
        sseSessions.values.forEach { try { it.outputStream.close() } catch (_: Exception) {} }
        sseSessions.clear()
        server?.stop(1)
        server = null
        LOG.info("MCP Server stopped")
    }

    private fun handleCors(exchange: HttpExchange) {
        exchange.responseHeaders.apply {
            add("Access-Control-Allow-Origin", "*")
            add("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            add("Access-Control-Allow-Headers", "Content-Type, Authorization")
        }
        exchange.sendResponseHeaders(204, -1)
    }
}
