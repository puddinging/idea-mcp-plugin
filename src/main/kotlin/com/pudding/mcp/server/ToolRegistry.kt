package com.pudding.mcp.server

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.tools.analysis.*
import com.pudding.mcp.tools.build.*
import com.pudding.mcp.tools.file.*
import com.pudding.mcp.tools.generate.*
import com.pudding.mcp.tools.navigation.*
import com.pudding.mcp.tools.project.*
import com.pudding.mcp.tools.refactor.*
import com.pudding.mcp.tools.search.*
import com.pudding.mcp.tools.spring.*
import com.pudding.mcp.tools.symbol.*
import com.pudding.mcp.tools.vcs.*

object ToolRegistry {
    private val tools = mutableMapOf<String, McpTool>()

    fun register(tool: McpTool) {
        tools[tool.name] = tool
    }

    fun get(name: String): McpTool? = tools[name]

    fun listAll(): List<McpTool> = tools.values.toList()

    fun registerAll() {
        // 📌 文件浏览与读取
        register(ListDirectoryTreeTool())
        register(GetFileTextTool())
        register(ReadFileTool())
        register(GetAllOpenFilesTool())
        register(OpenFileInEditorTool())
        // 📌 文件编辑
        register(CreateNewFileTool())
        register(ReplaceTextInFileTool())
        register(ReformatFileTool())
        // 📌 文件搜索
        register(FindFilesByNameTool())
        register(FindFilesByGlobTool())
        register(SearchFileTool())
        // 📌 代码内容搜索
        register(SearchTextTool())
        register(SearchRegexTool())
        register(SearchInFilesByTextTool())
        register(SearchInFilesByRegexTool())
        register(SearchSymbolTool())
        // 📌 符号分析
        register(GetSymbolInfoTool())
        register(GetFileProblemsTool())
        // 📌 重构
        register(RenameRefactoringTool())
        // 📌 项目信息
        register(GetProjectModulesTool())
        register(GetProjectDepsTool())
        register(GetRepositoriesTool())
        // 📌 构建运行
        register(BuildProjectTool())
        register(GetRunConfigsTool())
        register(ExecuteRunConfigTool())
        register(ExecuteTerminalTool())
        // 🆕 导航分析
        register(FindUsagesTool())
        register(CallHierarchyTool())
        register(TypeHierarchyTool())
        register(GetImplementationsTool())
        // 🆕 代码检查
        register(RunInspectionsTool())
        register(ApplyQuickFixTool())
        // 🆕 重构扩展
        register(ExtractMethodTool())
        register(MoveFileTool())
        register(ChangeSignatureTool())
        // 🆕 Spring 感知
        register(GetSpringBeansTool())
        register(GetRequestMappingsTool())
        // 🆕 VCS
        register(GitBlameTool())
        register(GetGitLogTool())
        // 🆕 代码生成
        register(GenerateCodeTool())
    }
}
