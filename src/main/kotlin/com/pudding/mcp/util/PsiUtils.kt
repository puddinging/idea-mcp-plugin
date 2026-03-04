package com.pudding.mcp.util

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

object PsiUtils {

    fun findVirtualFile(project: Project, relativePath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val fullPath = if (relativePath.startsWith("/")) relativePath
                       else "$basePath/$relativePath"
        return LocalFileSystem.getInstance().findFileByPath(fullPath)
    }

    fun findPsiFile(project: Project, relativePath: String): PsiFile? {
        val vf = findVirtualFile(project, relativePath) ?: return null
        return PsiManager.getInstance(project).findFile(vf)
    }

    fun lineColumnToOffset(document: Document, line: Int, column: Int): Int {
        val lineIndex = (line - 1).coerceAtLeast(0)
        if (lineIndex >= document.lineCount) return document.textLength
        return document.getLineStartOffset(lineIndex) + (column - 1).coerceAtLeast(0)
    }

    fun getDocument(project: Project, psiFile: PsiFile): Document? {
        return PsiDocumentManager.getInstance(project).getDocument(psiFile)
    }

    fun offsetToLine(document: Document, offset: Int): Int {
        return document.getLineNumber(offset) + 1
    }

    fun offsetToColumn(document: Document, offset: Int): Int {
        val line = document.getLineNumber(offset)
        return offset - document.getLineStartOffset(line) + 1
    }

    fun relativePath(project: Project, virtualFile: VirtualFile): String {
        val base = project.basePath ?: return virtualFile.path
        return virtualFile.path.removePrefix("$base/")
    }
}
