package com.codereview.plugin.service;

import com.codereview.plugin.model.ReviewResult;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 代码审查服务
 */
@Service
public final class ReviewService {
    private static final Logger LOG = Logger.getInstance(ReviewService.class);
    private final Project project;
    private final AIService aiService;

    public ReviewService(Project project) {
        this.project = project;
        this.aiService = AIService.getInstance();
    }

    /**
     * 获取服务实例
     */
    public static ReviewService getInstance(@NotNull Project project) {
        return project.getService(ReviewService.class);
    }

    /**
     * 审查当前打开的文件
     */
    public void reviewCurrentFile(Consumer<String> callback) {
        // 获取当前打开的文件
        VirtualFile currentFile = getCurrentOpenedFile();
        if (currentFile == null) {
            callback.accept("未打开任何文件！请先打开一个文件再进行代码审查。");
            return;
        }

        // 获取文件内容
        String fileContent = getFileContent(currentFile);
        if (fileContent == null) {
            callback.accept("无法读取文件内容！");
            return;
        }

        // 在实际应用中，这里应该调用AIService进行代码审查
        String fileExtension = currentFile.getExtension();
        String language = fileExtension != null ? fileExtension : "text";
        
        // 是否使用模拟数据（开发阶段使用）
        boolean useMockData = true;
        
        if (useMockData) {
            // 使用模拟数据（当前阶段）
            String mockResult = getMockReviewResult(currentFile.getName(), fileContent);
            callback.accept(mockResult);
        } else {
            // 使用AI服务（未来实现）
            aiService.analyzeCode(fileContent, language, new AIService.AIResponseCallback() {
                @Override
                public void onSuccess(ReviewResult result) {
                    String formattedResult = formatReviewResult(result);
                    callback.accept(formattedResult);
                }

                @Override
                public void onError(String errorMessage) {
                    callback.accept("AI代码审查出错：" + errorMessage);
                }
            });
        }
    }

    /**
     * 格式化审查结果为易读的文本
     */
    private String formatReviewResult(ReviewResult result) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("# AI代码审查结果\n\n");
        
        // 文件信息
        sb.append("## 文件信息\n");
        sb.append("- 文件名: ").append(result.getFileName()).append("\n");
        sb.append("- 代码行数: ").append(result.getLineCount()).append("\n\n");
        
        // 评分信息
        sb.append("## 代码质量评分\n");
        sb.append("- 总体评分: ").append(result.getOverallScore()).append("/100\n");
        sb.append("- 代码规范: ").append(result.getCodeStyleScore()).append("/100\n");
        sb.append("- 可维护性: ").append(result.getMaintainabilityScore()).append("/100\n");
        sb.append("- 复杂度: ").append(result.getComplexityScore()).append("/100\n\n");
        
        // 发现的问题
        sb.append("## 发现的问题\n");
        if (result.getIssues().isEmpty()) {
            sb.append("未发现明显问题，代码质量良好。\n\n");
        } else {
            for (int i = 0; i < result.getIssues().size(); i++) {
                ReviewResult.ReviewIssue issue = result.getIssues().get(i);
                sb.append(i + 1).append(". **").append(issue.getType()).append("** (").append(issue.getSeverity()).append(")");
                sb.append(" [行 ").append(issue.getLine()).append("]\n");
                sb.append("   - 问题：").append(issue.getDescription()).append("\n");
                sb.append("   - 建议：").append(issue.getSuggestion()).append("\n\n");
            }
        }
        
        // 改进建议
        sb.append("## 改进建议\n");
        if (result.getSuggestions().isEmpty()) {
            sb.append("代码已经很好，没有特别的改进建议。\n\n");
        } else {
            for (int i = 0; i < result.getSuggestions().size(); i++) {
                sb.append(i + 1).append(". ").append(result.getSuggestions().get(i)).append("\n");
            }
            sb.append("\n");
        }
        
        // 详细分析
        sb.append("## 详细分析\n");
        sb.append(result.getDetailedAnalysis());
        
        return sb.toString();
    }

    /**
     * 获取当前打开的文件
     */
    private VirtualFile getCurrentOpenedFile() {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        VirtualFile[] openFiles = fileEditorManager.getSelectedFiles();
        return openFiles.length > 0 ? openFiles[0] : null;
    }

    /**
     * 获取文件内容
     */
    private String getFileContent(VirtualFile file) {
        try {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            return document != null ? document.getText() : null;
        } catch (Exception e) {
            LOG.error("Failed to read file content", e);
            return null;
        }
    }

    /**
     * 获取模拟的审查结果 (将来会被实际的AI接口替换)
     */
    private String getMockReviewResult(String fileName, String content) {
        // 简单的代码行数统计
        int lineCount = content.split("\n").length;
        
        // 构建模拟结果
        StringBuilder result = new StringBuilder();
        result.append("# AI代码审查结果\n\n");
        result.append("## 文件信息\n");
        result.append("- 文件名: ").append(fileName).append("\n");
        result.append("- 代码行数: ").append(lineCount).append("\n\n");
        
        result.append("## 代码质量评分\n");
        result.append("- 总体评分: 85/100\n");
        result.append("- 代码规范: 90/100\n");
        result.append("- 可维护性: 80/100\n");
        result.append("- 复杂度: 85/100\n\n");
        
        result.append("## 主要发现\n");
        result.append("1. **良好实践**: 代码结构清晰，命名规范。\n");
        result.append("2. **建议改进**: 可以考虑增加更多的注释来提高可读性。\n");
        result.append("3. **潜在问题**: 部分方法可能需要增加错误处理机制。\n\n");
        
        result.append("## 详细分析\n");
        result.append("这里是代码的详细分析内容，说明每个关键部分的优缺点以及改进建议...\n\n");
        
        // 添加一些更具体的模拟建议
        result.append("### 设计模式应用\n");
        result.append("- 考虑在适当的地方应用工厂模式或构建者模式\n");
        result.append("- 单例模式的使用是否合理？检查线程安全性\n\n");
        
        result.append("### 性能考虑\n");
        result.append("- 部分循环可以优化，避免不必要的对象创建\n");
        result.append("- 检查是否有内存泄漏的可能性\n\n");
        
        result.append("### 测试覆盖\n");
        result.append("- 建议增加单元测试覆盖关键业务逻辑\n");
        result.append("- 考虑添加集成测试验证组件交互\n\n");
        
        return result.toString();
    }
} 