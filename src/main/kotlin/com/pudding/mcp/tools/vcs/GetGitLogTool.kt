package com.pudding.mcp.tools.vcs

import com.pudding.mcp.tools.McpTool
import com.pudding.mcp.util.PsiUtils
import com.pudding.mcp.util.SchemaUtils.error
import com.pudding.mcp.util.SchemaUtils.intOrDefault
import com.pudding.mcp.util.SchemaUtils.result
import com.pudding.mcp.util.SchemaUtils.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.history.GitHistoryUtils
import java.text.SimpleDateFormat
import java.util.Date

class GetGitLogTool : McpTool {
    override val name = "get_git_log"
    override val description = "Get the git commit history for a file or the whole repository"
    override val inputSchema = JsonObject().apply {
        addProperty("type", "object")
        add("properties", JsonObject().apply {
            add("filePath", JsonObject().apply {
                addProperty("type", "string")
                addProperty("description", "File path relative to project root. Omit for repo-wide log")
            })
            add("limit", JsonObject().apply {
                addProperty("type", "number")
                addProperty("description", "Max commits to return, default 20")
            })
        })
    }

    override fun execute(project: Project, params: JsonObject): JsonObject {
        val path = params.string("filePath")
        val limit = params.intOrDefault("limit", 20)

        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val commits = JsonArray()

            if (path != null) {
                // File-specific history via VcsHistoryProvider
                val vf = PsiUtils.findVirtualFile(project, path)
                    ?: return error("File not found: $path")
                val gitVcs = GitVcs.getInstance(project)
                val historyProvider = gitVcs.vcsHistoryProvider
                val filePath = VcsUtil.getFilePath(vf)
                val session = historyProvider.createSessionFor(filePath)

                val revisions = session.revisionList ?: emptyList()
                for (rev in revisions.take(limit)) {
                    commits.add(JsonObject().apply {
                        addProperty("hash", rev.revisionNumber.asString())
                        addProperty("author", rev.author ?: "")
                        addProperty("date", if (rev.revisionDate != null) dateFormat.format(rev.revisionDate) else "")
                        addProperty("message", rev.commitMessage?.trim() ?: "")
                    })
                }
            } else {
                // Repo-wide log via GitHistoryUtils
                val repositoryManager = GitUtil.getRepositoryManager(project)
                val repository = repositoryManager.repositories.firstOrNull()
                    ?: return error("No git repository found")
                val root: VirtualFile = repository.root

                val history = GitHistoryUtils.history(project, root, "-n", limit.toString())
                for (commit in history) {
                    commits.add(JsonObject().apply {
                        addProperty("hash", commit.id.asString())
                        addProperty("author", commit.author.name)
                        addProperty("date", dateFormat.format(Date(commit.authorTime)))
                        addProperty("message", commit.fullMessage.trim())
                    })
                }
            }

            result { add("commits", commits) }
        } catch (e: Exception) {
            error("Git log failed: ${e.message}")
        }
    }
}
