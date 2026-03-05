package com.pudding.mcp.server

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class McpSocketServer {
    private val LOG = Logger.getInstance(McpSocketServer::class.java)
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()

    companion object {
        const val DEFAULT_PORT = 19998

        @Volatile
        private var instance: McpSocketServer? = null

        fun getInstance(): McpSocketServer {
            return instance ?: synchronized(this) {
                instance ?: McpSocketServer().also { instance = it }
            }
        }
    }

    fun isRunning(): Boolean = running.get()

    fun start(project: Project?, port: Int = DEFAULT_PORT) {
        if (running.get()) {
            LOG.info("MCP Socket Server already running on port $port")
            return
        }
        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", port))
            }
            running.set(true)

            executor.submit {
                while (running.get()) {
                    try {
                        val client = serverSocket!!.accept()
                        executor.submit { handleClient(project, client) }
                    } catch (_: Exception) {
                        if (!running.get()) break
                    }
                }
            }
            LOG.info("MCP Socket Server started on 127.0.0.1:$port")
        } catch (e: Exception) {
            LOG.error("Failed to start MCP Socket Server on port $port", e)
        }
    }

    private fun handleClient(project: Project?, client: Socket) {
        try {
            client.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8), false)

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.isEmpty()) continue

                    val currentProject = project ?: ProjectManager.getInstance().openProjects.firstOrNull()
                        ?: throw IllegalStateException("No open project")
                    val response = McpProtocolHandler.handle(currentProject, trimmed)
                    writer.println(response)
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            LOG.warn("Socket client error", e)
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        LOG.info("MCP Socket Server stopped")
    }
}
