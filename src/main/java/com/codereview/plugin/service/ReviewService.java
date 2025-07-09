package com.codereview.plugin.service;

import com.codereview.plugin.auth.AuthService;
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
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.client.RestTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/**
 * 代码审查服务
 */
@Service
public final class ReviewService {
    private static final Logger LOG = Logger.getInstance(ReviewService.class);
    
    // API配置
    private static final String REVIEW_API_URL = "https://aide-at-test.apps.digiwincloud.com.cn/restful/standard/aide/submitReview";
    
    private final Project project;
    private final AIService aiService;
    private final AuthService authService;
    private final Gson gson;

    public ReviewService(Project project) {
        this.project = project;
        this.aiService = AIService.getInstance();
        this.authService = AuthService.getInstance();
        this.gson = new Gson();
    }

    /**
     * 获取服务实例
     */
    public static ReviewService getInstance(@NotNull Project project) {
        return project.getService(ReviewService.class);
    }

    /**
     * 代码审查文件项
     */
    public static class ReviewFileItem {
        private String fileName;
        private String filePath;
        private String content;
        private Integer startLine;
        private Integer endLine;
        
        public ReviewFileItem(String fileName, String filePath, String content) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.content = content;
        }
        
        public ReviewFileItem(String fileName, String filePath, String content, int startLine, int endLine) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.content = content;
            this.startLine = startLine;
            this.endLine = endLine;
        }
        
        // getters and setters
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public String getContent() { return content; }
        public Integer getStartLine() { return startLine; }
        public Integer getEndLine() { return endLine; }
    }

    /**
     * 审查多个文件或代码片段（不等待返回值）
     * @param fileItems 要审查的文件项列表
     * @param callback 结果回调（可选）
     */
    public void reviewFiles(List<ReviewFileItem> fileItems, Consumer<String> callback) {
        if (!authService.isLoggedIn()) {
            if (callback != null) {
                callback.accept("❌ 错误：用户未登录\n\n请先登录后再进行代码审查。");
            }
            return;
        }
        
        if (fileItems == null || fileItems.isEmpty()) {
            if (callback != null) {
                callback.accept("❌ 错误：没有要审查的文件\n\n请先添加文件或代码片段。");
            }
            return;
        }
        
        LOG.info("开始代码审查，文件数量: " + fileItems.size());
        
        // 异步调用API，不等待返回值
        new Thread(() -> {
            try {
                callReviewAPI(fileItems);
                LOG.info("代码审查请求已发送，不等待返回值");
                // 可选：发送成功提示
                if (callback != null) {
                    callback.accept("✅ 代码审查请求已发送\n\n评审结果将通过MQTT消息返回。");
                }
            } catch (Exception e) {
                LOG.error("代码审查API调用失败", e);
                if (callback != null) {
                    callback.accept("❌ API调用失败：" + e.getMessage() + "\n\n请检查网络连接或联系管理员。");
                }
            }
        }).start();
    }
    
    /**
     * 调用代码审查API（不等待返回值）
     */
    private void callReviewAPI(List<ReviewFileItem> fileItems) {
        LOG.info("准备调用代码审查API，URL: " + REVIEW_API_URL);
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // 添加token
            String token = authService.getToken();
            if (token != null) {
                headers.add("token", token);
                LOG.info("已添加token到请求头");
            } else {
                LOG.warn("未获取到有效token");
            }
            
            // 构建multipart请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // 添加文件
            for (int i = 0; i < fileItems.size(); i++) {
                ReviewFileItem item = fileItems.get(i);
                LOG.info("添加文件 " + (i + 1) + ": " + item.getFileName() + ", 大小: " + item.getContent().length() + " 字符");
                
                // 创建文件资源
                ByteArrayResource fileResource = new ByteArrayResource(item.getContent().getBytes()) {
                    @Override
                    public String getFilename() {
                        return item.getFileName();
                    }
                };
                
                body.add("files", fileResource);
            }
            
            // 构建fileInfo JSON数组
            List<Map<String, Object>> fileInfoList = new ArrayList<>();
            for (ReviewFileItem item : fileItems) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("fileName", item.getFileName());
                fileInfo.put("filePath", item.getFilePath());
                
                // 如果是代码片段，添加行范围
                if (item.getStartLine() != null && item.getEndLine() != null) {
                    fileInfo.put("line", item.getStartLine() + "-" + item.getEndLine());
                    LOG.info("文件 " + item.getFileName() + " 包含行范围: " + item.getStartLine() + "-" + item.getEndLine());
                } else {
                    LOG.info("文件 " + item.getFileName() + " 是完整文件");
                }
                
                fileInfoList.add(fileInfo);
            }
            
            String fileInfoJson = gson.toJson(fileInfoList);
            body.add("fileInfo", fileInfoJson);
            
            LOG.info("fileInfo JSON: " + fileInfoJson);
            LOG.info("准备发送请求，包含 " + fileItems.size() + " 个文件");
            
            // 创建请求实体
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            // 发送请求
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(REVIEW_API_URL, requestEntity, String.class);
            long endTime = System.currentTimeMillis();
            
            LOG.info("API调用完成，耗时: " + (endTime - startTime) + "ms");
            LOG.info("响应状态码: " + response.getStatusCode());
            LOG.info("响应内容: " + response.getBody());
            
            if (response.getStatusCode() == HttpStatus.OK) {
                LOG.info("代码审查请求发送成功");
            } else {
                LOG.warn("API返回非200状态码: " + response.getStatusCode());
                throw new RuntimeException("API调用失败：状态码 " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            LOG.error("调用代码审查API时发生异常", e);
            throw new RuntimeException("API调用异常：" + e.getMessage(), e);
        }
    }
    
    /**
     * 解析API响应
     */
    private String parseApiResponse(String responseBody) {
        try {
            if (responseBody == null || responseBody.trim().isEmpty()) {
                return "❌ API返回空响应";
            }
            
            LOG.info("开始解析API响应");
            
            // 尝试解析JSON响应
            JsonObject jsonResponse = new JsonParser().parse(responseBody).getAsJsonObject();
            
            StringBuilder result = new StringBuilder();
            result.append("# 🔍 代码审查结果\n\n");
            
            // 检查是否有错误
            if (jsonResponse.has("error") || jsonResponse.has("errorCode")) {
                String errorMsg = jsonResponse.has("error") ? 
                    jsonResponse.get("error").getAsString() : 
                    jsonResponse.get("errorCode").getAsString();
                result.append("❌ **审查失败**\n\n");
                result.append("错误信息：").append(errorMsg).append("\n\n");
                return result.toString();
            }
            
            // 解析成功响应
            if (jsonResponse.has("data")) {
                JsonObject data = jsonResponse.getAsJsonObject("data");
                
                // 审查摘要
                if (data.has("summary")) {
                    result.append("## 📋 审查摘要\n");
                    result.append(data.get("summary").getAsString()).append("\n\n");
                }
                
                // 问题列表
                if (data.has("issues")) {
                    result.append("## ⚠️ 发现的问题\n");
                    // 处理问题列表
                    result.append(data.get("issues").getAsString()).append("\n\n");
                }
                
                // 建议
                if (data.has("suggestions")) {
                    result.append("## 💡 改进建议\n");
                    result.append(data.get("suggestions").getAsString()).append("\n\n");
                }
                
                // 评分
                if (data.has("score")) {
                    result.append("## 📊 质量评分\n");
                    result.append("**综合评分：").append(data.get("score").getAsString()).append("**\n\n");
                }
            } else {
                // 如果没有标准的data字段，直接显示整个响应
                result.append("## 📄 审查结果\n");
                result.append("```json\n");
                result.append(gson.toJson(jsonResponse));
                result.append("\n```\n");
            }
            
            result.append("---\n");
            result.append("*审查完成时间：").append(new java.util.Date().toString()).append("*");
            
            return result.toString();
            
        } catch (Exception e) {
            LOG.error("解析API响应时出错", e);
            return "✅ 审查完成，但响应格式解析失败\n\n**原始响应：**\n" + responseBody;
        }
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

        // 创建文件项并调用新的审查方法
        List<ReviewFileItem> fileItems = new ArrayList<>();
        String relativePath = getRelativeFilePath(currentFile);
        fileItems.add(new ReviewFileItem(currentFile.getName(), relativePath, fileContent));
        
        reviewFiles(fileItems, callback);
    }
    
    /**
     * 获取文件相对路径
     */
    private String getRelativeFilePath(VirtualFile file) {
        String basePath = project.getBasePath();
        String fullPath = file.getPath();
        
        if (basePath != null && fullPath.startsWith(basePath)) {
            return fullPath.substring(basePath.length() + 1);
        }
        
        return fullPath;
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