package com.pudding.mcp.stdio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Stdio transport for MCP. Reads JSON-RPC from stdin, forwards to the local
 * SSE server via HTTP, and writes responses to stdout.
 * <p>
 * Launched as a subprocess by MCP clients using the IDE's bundled JBR Java binary.
 * Env var MCP_SERVER_PORT controls the target port (default 19999).
 */
public class McpStdioRunner {

    public static void main(String[] args) {
        String portStr = System.getenv("MCP_SERVER_PORT");
        int port = (portStr != null && !portStr.isEmpty()) ? Integer.parseInt(portStr) : 19999;
        String endpoint = "http://127.0.0.1:" + port + "/message";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    String response = postJson(endpoint, line);
                    System.out.println(response);
                    System.out.flush();
                } catch (Exception e) {
                    String error = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"" +
                            escapeJson(e.getMessage()) + "\"},\"id\":null}";
                    System.out.println(error);
                    System.out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("McpStdioRunner fatal error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static String postJson(String endpoint, String json) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            java.io.InputStream is = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) {
                return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"No response body\"},\"id\":null}";
            }

            byte[] bytes = readAllBytes(is);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAllBytes(java.io.InputStream is) throws Exception {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int n;
        while ((n = is.read(tmp)) != -1) {
            buf.write(tmp, 0, n);
        }
        return buf.toByteArray();
    }

    private static String escapeJson(String s) {
        if (s == null) return "unknown error";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
