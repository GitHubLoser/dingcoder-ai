package com.codereview.plugin.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件差异比较服务
 * 用于比较现有文件和生成文件的差异
 */
public class FileDiffService {
    private static final Logger LOG = Logger.getInstance(FileDiffService.class);
    private final Project project;

    public FileDiffService(Project project) {
        this.project = project;
    }

    /**
     * 获取服务实例
     */
    public static FileDiffService getInstance(Project project) {
        return new FileDiffService(project);
    }

    /**
     * 文件差异信息
     */
    public static class FileDiffInfo {
        private final String fileName;
        private final String filePath;
        private final String existingContent;
        private final String newContent;
        private final boolean hasDifferences;

        public FileDiffInfo(String fileName, String filePath, String existingContent, String newContent, boolean hasDifferences) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.existingContent = existingContent;
            this.newContent = newContent;
            this.hasDifferences = hasDifferences;
        }

        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public String getExistingContent() { return existingContent; }
        public String getNewContent() { return newContent; }
        public boolean hasDifferences() { return hasDifferences; }
    }

    /**
     * 检查文件是否存在差异
     */
    public FileDiffInfo checkFileDifferences(String className, String newContent, VirtualFile targetDir) {
        try {
            String fileName = className + ".java";
            VirtualFile existingFile = targetDir.findChild(fileName);
            
            if (existingFile == null) {
                // 文件不存在，没有差异
                return new FileDiffInfo(fileName, targetDir.getPath() + "/" + fileName, "", newContent, false);
            }

            // 读取现有文件内容
            String existingContent = new String(existingFile.contentsToByteArray(), existingFile.getCharset());
            
            // 比较内容是否有差异
            boolean hasDifferences = !normalizeContent(existingContent).equals(normalizeContent(newContent));
            
            return new FileDiffInfo(
                fileName,
                existingFile.getPath(),
                existingContent,
                newContent,
                hasDifferences
            );
            
        } catch (Exception e) {
            LOG.error("检查文件差异时出错: " + className, e);
            return new FileDiffInfo(className + ".java", "", "", newContent, false);
        }
    }

    /**
     * 标准化内容用于比较（去除空白字符差异）
     */
    private String normalizeContent(String content) {
        return content.replaceAll("\\s+", " ").trim();
    }

    /**
     * 批量检查文件差异
     */
    public List<FileDiffInfo> batchCheckFileDifferences(List<String> classNames, List<String> newContents, VirtualFile targetDir) {
        List<FileDiffInfo> diffInfos = new ArrayList<>();
        
        for (int i = 0; i < classNames.size(); i++) {
            String className = classNames.get(i);
            String newContent = newContents.get(i);
            
            FileDiffInfo diffInfo = checkFileDifferences(className, newContent, targetDir);
            if (diffInfo.hasDifferences()) {
                diffInfos.add(diffInfo);
            }
        }
        
        return diffInfos;
    }

    /**
     * 生成差异文本（简单的行级比较）
     */
    public String generateDiffText(FileDiffInfo diffInfo) {
        StringBuilder diffText = new StringBuilder();
        diffText.append("文件: ").append(diffInfo.getFileName()).append("\n");
        diffText.append("路径: ").append(diffInfo.getFilePath()).append("\n");
        diffText.append("==================================================\n\n");
        
        String[] existingLines = diffInfo.getExistingContent().split("\n");
        String[] newLines = diffInfo.getNewContent().split("\n");
        
        // 简单的行级比较
        int maxLines = Math.max(existingLines.length, newLines.length);
        
        for (int i = 0; i < maxLines; i++) {
            String existingLine = i < existingLines.length ? existingLines[i] : "";
            String newLine = i < newLines.length ? newLines[i] : "";
            
            if (!existingLine.equals(newLine)) {
                diffText.append("第").append(i + 1).append("行:\n");
                if (!existingLine.isEmpty()) {
                    diffText.append("- ").append(existingLine).append("\n");
                }
                if (!newLine.isEmpty()) {
                    diffText.append("+ ").append(newLine).append("\n");
                }
                diffText.append("\n");
            }
        }
        
        return diffText.toString();
    }
} 