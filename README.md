# IntelliJ MCP Plugin

IntelliJ IDEA 插件，通过 [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) 将 IDE 能力暴露给 AI 编程助手（Claude Code、Cursor 等），支持 SSE 和 Stdio 两种传输方式。

## 功能概览

提供 40+ 个工具，覆盖 IDE 核心能力：

| 分类 | 工具 |
|------|------|
| 文件操作 | 读取/创建/替换文件内容、目录树、打开编辑器、格式化 |
| 搜索 | 文本搜索、正则搜索、符号搜索、文件名搜索、Glob 匹配 |
| 导航 | 查找引用、调用层级、类型层级、实现查找 |
| 重构 | 重命名、提取方法、移动文件、修改方法签名 |
| 分析 | 代码检查、快速修复、符号信息 |
| 构建 | 编译项目、运行配置、终端命令 |
| 代码生成 | 构造器、getter/setter、toString、equals/hashCode |
| Spring | Bean 列表、请求映射 |
| Git | blame、提交历史 |
| 项目 | 模块列表、依赖列表、VCS 仓库 |

## 兼容性

- IntelliJ IDEA 2023.3+（含 EAP 版本）
- 需要 Java 插件和 Git4Idea 插件

## 从零构建

### 1. 环境准备

- JDK 17+
- IntelliJ IDEA（任意版本，用于开发和测试）

### 2. 克隆项目

```bash
git clone https://github.com/<your-username>/intellij-mcp-plugin.git
cd intellij-mcp-plugin
```

### 3. 项目结构

```
src/main/
├── java/com/pudding/mcp/stdio/
│   └── McpStdioRunner.java          # Stdio 传输层（纯 Java，无外部依赖）
├── kotlin/com/pudding/mcp/
│   ├── McpPluginConfigurable.kt      # Settings 面板 UI
│   ├── McpPluginSettings.kt          # 持久化设置（开关状态）
│   ├── McpPluginStartupActivity.kt   # IDE 启动时注册工具
│   ├── server/
│   │   ├── McpSseServer.kt           # HTTP 服务器（SSE + JSON-RPC）
│   │   ├── McpProtocolHandler.kt     # MCP 协议处理
│   │   └── ToolRegistry.kt           # 工具注册中心
│   ├── tools/                        # 40+ 工具实现（按分类组织）
│   └── util/                         # PSI 和 Schema 工具类
└── resources/META-INF/
    └── plugin.xml                    # 插件声明
```

### 4. 构建

```bash
./gradlew build
```

产物位于 `build/distributions/idea-mcp-plugin-1.0.0.zip`。

### 5. 安装

IDEA → Settings → Plugins → 齿轮图标 → **Install Plugin from Disk...** → 选择 zip 文件 → 重启 IDE。

### 6. 启用

Settings → **MCP Server** → 勾选 **Enable MCP Server** → 服务即时启动。

## 连接 AI 助手

### SSE 模式

点击 **Copy SSE Config**，将配置粘贴到 AI 助手的 MCP 配置文件中：

```json
{
  "mcpServers": {
    "intellij-idea-mcp": {
      "url": "http://127.0.0.1:19999/sse"
    }
  }
}
```

### Stdio 模式

点击 **Copy Stdio Config**，插件会自动生成包含当前 JBR 路径和插件 JAR classpath 的配置：

```json
{
  "mcpServers": {
    "intellij-idea-mcp": {
      "type": "stdio",
      "env": { "MCP_SERVER_PORT": "19999" },
      "command": "<jbr-java-path>",
      "args": ["-classpath", "<plugin-jars>", "com.pudding.mcp.stdio.McpStdioRunner"]
    }
  }
}
```

> Stdio 模式下，AI 助手以子进程方式启动 `McpStdioRunner`，通过 stdin/stdout 传输 JSON-RPC 消息，由 Runner 转发至插件内置的 HTTP 服务。

## 架构

```
AI 助手 ──SSE──→ McpSseServer (:19999) ──→ McpProtocolHandler ──→ Tool.execute()
                                                                       ↓
AI 助手 ──Stdio──→ McpStdioRunner ──HTTP──→ McpSseServer (/message)   IDE PSI/API
```

## 开发调试

```bash
# 沙箱模式运行（启动带插件的 IDE 实例）
./gradlew runIde

# 仅构建不运行
./gradlew build

# 打包分发
./gradlew buildPlugin
```

## License

MIT
