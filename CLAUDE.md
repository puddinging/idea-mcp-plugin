# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build              # Build the plugin
./gradlew buildPlugin        # Package for distribution (output: build/distributions/)
./gradlew runIde             # Launch sandbox IDE with the plugin installed
./gradlew runIde -PtestProject=/path/to/project  # Open a specific project in sandbox
```

Requires JDK 17+ (toolchain targets 21). No test suite exists yet.

## Architecture

This is an IntelliJ IDEA plugin that exposes IDE capabilities via the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/), allowing AI assistants (Claude Code, Cursor, etc.) to interact with the IDE programmatically.

### Transport Layer (dual-mode)

- **SSE mode**: `McpSseServer` runs an HTTP server on port 19999 with `/sse` (long-lived SSE connection), `/message` (JSON-RPC requests), and `/health` endpoints. Uses `com.sun.net.httpserver.HttpServer`.
- **Socket mode**: `McpSocketServer` listens on port 19998 for raw TCP connections. Each line is a JSON-RPC message, response sent back on the same connection.
- **Stdio bridge**: `McpStdioRunner` (pure Java, no IntelliJ dependencies) reads JSON-RPC from stdin, forwards to the socket server on port 19998, and writes responses to stdout. This is the entry point for stdio-mode MCP clients.

Request flow: AI client -> Transport (SSE/Socket/Stdio) -> `McpProtocolHandler` -> `ToolRegistry` -> `McpTool.execute()`

### Tool System

All tools implement `McpTool` interface (`tools/McpTool.kt`): `name`, `description`, `inputSchema` (JSON Schema as `JsonObject`), and `execute(project, params)`.

Tools are organized by category under `tools/`: `file/`, `search/`, `navigation/`, `refactor/`, `analysis/`, `build/`, `generate/`, `spring/`, `vcs/`, `project/`, `symbol/`.

All tools are registered in `ToolRegistry.registerAll()`. To add a new tool: implement `McpTool`, then add a `register()` call in `ToolRegistry`.

### Key Utilities

- `SchemaUtils` - DSL for building JSON schemas and extracting params (`string()`, `int()`, `bool()`, `stringProp()`, etc.)
- `PsiUtils` - Helpers for resolving file paths (relative/absolute), PSI file lookup, offset/line/column conversions

### Plugin Lifecycle

`McpPluginStartupActivity` (registered as `postStartupActivity` in `plugin.xml`) registers all tools once and starts servers if enabled. Settings are persisted via `McpPluginSettings` (application-level `PersistentStateComponent`). The settings UI is in `McpPluginConfigurable`.

### Dependencies

- IntelliJ Platform 2025.1+ (Community Edition)
- Bundled plugins: `com.intellij.java`, `Git4Idea`
- External: `com.google.code.gson:gson:2.10.1` (JSON handling)

## Conventions

- Kotlin for all plugin code; Java only for `McpStdioRunner` (intentionally dependency-free for subprocess use)
- Tools return `JsonObject` results using `SchemaUtils.result {}` / `SchemaUtils.error()`
- File paths in tools accept both absolute and project-relative paths (resolved by `PsiUtils.findVirtualFile`)
