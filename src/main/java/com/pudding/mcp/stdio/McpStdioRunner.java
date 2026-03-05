package com.pudding.mcp.stdio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Stdio transport for MCP. Reads JSON-RPC from stdin, forwards to the plugin's
 * raw socket server, and writes responses to stdout.
 * <p>
 * Env var MCP_SOCKET_PORT controls the target port (default 19998).
 */
public class McpStdioRunner {

    public static void main(String[] args) {
        String portStr = System.getenv("MCP_SOCKET_PORT");
        int port = (portStr != null && !portStr.isEmpty()) ? Integer.parseInt(portStr) : 19998;

        try (Socket socket = new Socket("127.0.0.1", port);
             BufferedReader socketIn = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter socketOut = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), false);
             BufferedReader stdinReader = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = stdinReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                socketOut.println(line);
                socketOut.flush();

                String response = socketIn.readLine();
                if (response == null) {
                    System.err.println("McpStdioRunner: socket closed by server");
                    break;
                }
                System.out.println(response);
                System.out.flush();
            }
        } catch (Exception e) {
            System.err.println("McpStdioRunner fatal error: " + e.getMessage());
            System.exit(1);
        }
    }
}
