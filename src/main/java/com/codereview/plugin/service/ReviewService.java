package com.codereview.plugin.service;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.model.ReviewResult;
import com.codereview.plugin.model.DWFile;
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
import org.springframework.web.client.RestTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

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
                // 成功时不调用callback，保持评审结果区域不变
            } catch (Exception e) {
                LOG.error("代码审查API调用失败", e);
                // 失败时调用callback通知UI层重置按钮状态
                if (callback != null) {
                    String errorMessage = getErrorMessage(e);
                    callback.accept("❌ 代码审查请求失败：" + errorMessage);
                }
            }
        }).start();
    }
    
    /**
     * 审查代码变更（不等待返回值）
     * @param diffContent git diff 的输出内容
     * @param callback 结果回调（可选）
     */
    public void reviewChanges(String diffContent, Consumer<String> callback) {
        if (!authService.isLoggedIn()) {
            if (callback != null) {
                callback.accept("❌ 错误：用户未登录\n\n请先登录后再进行代码审查。");
            }
            return;
        }
        
        if (diffContent == null || diffContent.trim().isEmpty()) {
            if (callback != null) {
                callback.accept("❌ 错误：没有可评审的变更\n\n请先添加文件到暂存区。");
            }
            return;
        }
        
        LOG.info("开始代码变更审查，变更内容长度: " + diffContent.length());
        
        // 异步调用API，不等待返回值
        new Thread(() -> {
            try {
                // 解析diff内容，提取变更的文件
                List<ReviewFileItem> changedFiles = parseChangedFilesFromDiff(diffContent);
                callReviewChangesAPI(diffContent, changedFiles);
                LOG.info("代码变更审查请求已发送，不等待返回值");
                // 成功时不调用callback，保持评审结果区域不变
            } catch (Exception e) {
                LOG.error("代码变更审查API调用失败", e);
                // 失败时调用callback通知UI层重置按钮状态
                if (callback != null) {
                    String errorMessage = getErrorMessage(e);
                    callback.accept("❌ 代码变更审查请求失败：" + errorMessage);
                }
            }
        }).start();
    }
    
    /**
     * 从diff内容中解析变更的文件
     */
    private List<ReviewFileItem> parseChangedFilesFromDiff(String diffContent) {
        List<ReviewFileItem> changedFiles = new ArrayList<>();
        String[] lines = diffContent.split("\n");
        
        String currentFile = null;
        
        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                // 开始新文件
                String[] parts = line.split(" ");
                if (parts.length >= 4) {
                    // 从 "a/src/main/java/Test.java" 格式中提取文件路径
                    String filePath = parts[2].substring(2); // 移除 "a/" 前缀
                    currentFile = filePath;
                    LOG.info("从diff中解析到文件: " + currentFile);
                }
            }
        }
        
        // 处理所有解析到的文件
        if (currentFile != null) {
            String fileName = extractFileName(currentFile);
            try {
                String content = getFileContentFromProject(currentFile);
                if (content != null) {
                    changedFiles.add(new ReviewFileItem(fileName, currentFile, content));
                    LOG.info("成功获取文件内容: " + currentFile + " (大小: " + content.length() + " 字符)");
                } else {
                    LOG.warn("无法获取文件内容: " + currentFile);
                }
            } catch (Exception e) {
                LOG.warn("获取文件内容时出错: " + currentFile + ", 错误: " + e.getMessage());
            }
        }
        
        LOG.info("从diff中解析出 " + changedFiles.size() + " 个变更文件");
        return changedFiles;
    }
    
    /**
     * 从文件路径中提取文件名
     */
    private String extractFileName(String filePath) {
        if (filePath == null) return "";
        int lastSlash = filePath.lastIndexOf('/');
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }
    
    /**
     * 从项目中获取文件内容
     */
    private String getFileContentFromProject(String relativePath) {
        try {
            String basePath = project.getBasePath();
            if (basePath == null) {
                LOG.warn("无法获取项目基础路径");
                return null;
            }
            
            LOG.info("尝试获取文件内容: " + relativePath);
            LOG.info("项目基础路径: " + basePath);
            
            // 方法1：通过相对路径查找
            VirtualFile file = project.getBaseDir().findFileByRelativePath(relativePath);
            if (file != null && file.exists()) {
                LOG.info("通过相对路径找到文件: " + file.getPath());
                return getFileContent(file);
            }
            
            // 方法2：通过完整路径查找
            String fullPath = basePath + "/" + relativePath;
            file = project.getBaseDir().getFileSystem().findFileByPath(fullPath);
            if (file != null && file.exists()) {
                LOG.info("通过完整路径找到文件: " + file.getPath());
                return getFileContent(file);
            }
            
            // 方法3：通过文件名查找
            String fileName = extractFileName(relativePath);
            LOG.info("尝试通过文件名查找: " + fileName);
            file = findFileByName(fileName);
            if (file != null && file.exists()) {
                LOG.info("通过文件名找到文件: " + file.getPath());
                return getFileContent(file);
            }
            
            LOG.warn("无法找到文件: " + relativePath);
            return null;
        } catch (Exception e) {
            LOG.error("获取文件内容失败: " + relativePath, e);
            return null;
        }
    }
    
    /**
     * 通过文件名查找文件
     */
    private VirtualFile findFileByName(String fileName) {
        try {
            // 首先尝试在当前打开的文件中查找
            VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();
            for (VirtualFile file : openFiles) {
                if (fileName.equals(file.getName())) {
                    return file;
                }
            }
            
            // 如果没找到，在整个项目中搜索
            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot != null) {
                return findFileRecursively(projectRoot, fileName);
            }
            
            return null;
        } catch (Exception e) {
            LOG.error("通过文件名查找文件失败: " + fileName, e);
            return null;
        }
    }
    
    /**
     * 递归查找文件
     */
    private VirtualFile findFileRecursively(VirtualFile directory, String fileName) {
        if (directory.isDirectory()) {
            for (VirtualFile child : directory.getChildren()) {
                if (child.isDirectory()) {
                    VirtualFile found = findFileRecursively(child, fileName);
                    if (found != null) {
                        return found;
                    }
                } else if (fileName.equals(child.getName())) {
                    return child;
                }
            }
        }
        return null;
    }
    
    // 新增：创建跳过SSL校验的RestTemplate
    private static RestTemplate createUnsafeRestTemplate() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] xcs, String string) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] xcs, String string) {}
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            }}, new java.security.SecureRandom());

            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            // 使用HttpComponentsClientHttpRequestFactory提供更好的连接池管理和超时控制
            try {
                // 创建SSL连接工厂
                org.apache.http.conn.ssl.SSLConnectionSocketFactory sslSocketFactory = 
                    new org.apache.http.conn.ssl.SSLConnectionSocketFactory(sslContext, 
                        new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"}, 
                        null, 
                        org.apache.http.conn.ssl.NoopHostnameVerifier.INSTANCE);

                // 创建连接池管理器
                org.apache.http.impl.conn.PoolingHttpClientConnectionManager connectionManager = 
                    new org.apache.http.impl.conn.PoolingHttpClientConnectionManager();
                connectionManager.setMaxTotal(20); // 最大连接数
                connectionManager.setDefaultMaxPerRoute(10); // 每个路由最大连接数

                // 创建HTTP客户端
                org.apache.http.impl.client.CloseableHttpClient httpClient = 
                    org.apache.http.impl.client.HttpClients.custom()
                        .setSSLSocketFactory(sslSocketFactory)
                        .setConnectionManager(connectionManager)
                        .setConnectionManagerShared(true)
                        .build();

                // 创建请求工厂
                org.springframework.http.client.HttpComponentsClientHttpRequestFactory requestFactory = 
                    new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(httpClient);
                
                // 设置超时时间
                requestFactory.setConnectTimeout(30000); // 连接超时30秒
                requestFactory.setReadTimeout(60000); // 读取超时60秒
                
                return new RestTemplate(requestFactory);
            } catch (Exception httpComponentsException) {
                LOG.warn("HttpComponents不可用，回退到SimpleClientHttpRequestFactory", httpComponentsException);
                // 回退到SimpleClientHttpRequestFactory
                org.springframework.http.client.SimpleClientHttpRequestFactory fallbackFactory = 
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
                fallbackFactory.setConnectTimeout(30000);
                fallbackFactory.setReadTimeout(60000);
                return new RestTemplate(fallbackFactory);
            }
        } catch (Exception e) {
            LOG.error("创建RestTemplate失败", e);
            throw new RuntimeException("无法创建HTTP客户端", e);
        }
    }

    /**
     * 调用代码审查API（不等待返回值）
     */
    private void callReviewAPI(List<ReviewFileItem> fileItems) {
        LOG.info("=== 开始调用代码审查API ===");
        LOG.info("API URL: " + REVIEW_API_URL);
        
        // 重试机制
        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < maxRetries) {
            try {
                if (retryCount > 0) {
                    LOG.info("=== 第 " + (retryCount + 1) + " 次重试调用代码审查API ===");
                    // 重试前等待一段时间
                    Thread.sleep(1000 * retryCount);
                }
                
                RestTemplate restTemplate = createUnsafeRestTemplate();
                
                // 设置请求头 - 改为JSON格式
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                LOG.info("设置Content-Type: " + MediaType.APPLICATION_JSON);
                
                // 添加token
                String token = authService.getToken();
                if (token != null) {
                    headers.add("token", token);
                    LOG.info("已添加token到请求头: " + token.substring(0, Math.min(20, token.length())) + "...");
                } else {
                    LOG.warn("未获取到有效token");
                }
                
                LOG.info("=== 开始构建JSON请求体 ===");
                
                // 构建DWFile数组
                List<DWFile> dwFiles = new ArrayList<>();
                LOG.info("=== 创建DWFile对象数组 ===");
                for (int i = 0; i < fileItems.size(); i++) {
                    ReviewFileItem item = fileItems.get(i);
                    LOG.info("处理文件 " + (i + 1) + "/" + fileItems.size() + ":");
                    LOG.info("  - 文件名: " + item.getFileName());
                    LOG.info("  - 文件路径: " + item.getFilePath());
                    LOG.info("  - 内容大小: " + item.getContent().length() + " 字符");
                    
                    // 将文件内容转换为字节数组
                    byte[] fileBytes = item.getContent().getBytes("UTF-8");
                    LOG.info("  - 内容字节大小: " + fileBytes.length + " bytes");
                    
                    // 创建DWFile对象
                    DWFile dwFile = new DWFile(item.getFileName(), fileBytes);
                    dwFiles.add(dwFile);
                    
                    LOG.info("  ✅ 已创建DWFile对象: " + item.getFileName());
                    LOG.info("  - DWFile.fileName: " + dwFile.getFileName());
                    LOG.info("  - DWFile.fileByteArray长度: " + dwFile.getFileByteArray().length + " bytes");
                    
                    // 显示文件内容预览
                    if (item.getContent() != null && !item.getContent().isEmpty()) {
                        String[] lines = item.getContent().split("\n");
                        int showLines = Math.min(3, lines.length);
                        StringBuilder preview = new StringBuilder();
                        for (int j = 0; j < showLines; j++) {
                            preview.append("    行").append(j + 1).append(": ").append(lines[j]).append("\n");
                        }
                        if (lines.length > 3) {
                            preview.append("    ... (共").append(lines.length).append("行)");
                        }
                        LOG.info("  - 文件内容预览:\n" + preview.toString());
                    } else {
                        LOG.warn("  ⚠️ 文件内容为空: " + item.getFileName());
                    }
                    LOG.info("  ----------------------------------------");
                }
                LOG.info("=== DWFile数组构建完成，共创建了 " + dwFiles.size() + " 个文件对象 ===");
                
                // 构建fileInfo参数
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
                LOG.info("=== fileInfo参数构建完成 ===");
                LOG.info("fileInfo JSON内容: " + fileInfoJson);
                LOG.info("fileInfo JSON长度: " + fileInfoJson.length() + " 字符");
                
                // 构建完整的请求体
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("files", dwFiles);
                requestBody.put("fileInfo", fileInfoJson);
                
                String requestBodyJson = gson.toJson(requestBody);
                
                LOG.info("=== JSON请求体构建完成 ===");
                LOG.info("请求体包含参数:");
                LOG.info("  - files: DWFile数组 (共" + dwFiles.size() + "个文件)");
                LOG.info("  - fileInfo: JSON字符串");
                
                // 显示每个文件的详细信息
                for (int i = 0; i < dwFiles.size(); i++) {
                    DWFile dwFile = dwFiles.get(i);
                    LOG.info("    文件[" + i + "]: " + dwFile.getFileName() + 
                            " (字节数: " + dwFile.getFileByteArray().length + ")");
                }
                
                LOG.info("请求体JSON预览: " + requestBodyJson.substring(0, Math.min(200, requestBodyJson.length())) + "...");
                
                LOG.info("=== 准备发送JSON请求 ===");
                LOG.info("Content-Type: application/json");
                LOG.info("传输方式: JSON格式，文件内容转为byte[]数组");
                
                // 创建请求实体
                HttpEntity<String> requestEntity = new HttpEntity<>(requestBodyJson, headers);
                LOG.info("JSON请求实体创建完成");
                
                // 发送请求
                LOG.info("开始发送HTTP请求...");
                long startTime = System.currentTimeMillis();
                ResponseEntity<String> response = restTemplate.postForEntity(REVIEW_API_URL, requestEntity, String.class);
                long endTime = System.currentTimeMillis();
                
                LOG.info("=== API调用完成 ===");
                LOG.info("请求耗时: " + (endTime - startTime) + "ms");
                LOG.info("响应状态码: " + response.getStatusCode());
                LOG.info("响应头: " + response.getHeaders());
                LOG.info("响应内容长度: " + (response.getBody() != null ? response.getBody().length() : 0));
                LOG.info("响应内容: " + response.getBody());
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    LOG.info("代码审查请求发送成功");
                    return; // 成功，退出重试循环
                } else {
                    LOG.warn("API返回非200状态码: " + response.getStatusCode());
                    throw new RuntimeException("API调用失败：状态码 " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                
                LOG.error("=== 第 " + retryCount + " 次调用代码审查API时发生异常 ===");
                LOG.error("异常类型: " + e.getClass().getSimpleName());
                LOG.error("异常消息: " + e.getMessage());
                
                // 判断是否是可重试的异常
                boolean isRetryable = isRetryableException(e);
                if (!isRetryable) {
                    LOG.error("遇到不可重试的异常，停止重试");
                    break;
                }
                
                if (retryCount >= maxRetries) {
                    LOG.error("已达到最大重试次数 " + maxRetries + "，停止重试");
                    break;
                }
                
                LOG.info("将在 " + retryCount + " 秒后进行第 " + (retryCount + 1) + " 次重试");
            }
        }
        
        // 所有重试都失败了
        LOG.error("=== 代码审查API调用最终失败 ===");
        LOG.error("重试次数: " + retryCount);
        LOG.error("最后异常: " + (lastException != null ? lastException.getMessage() : "未知异常"));
        
        if (lastException != null) {
            String errorMessage = getErrorMessage(lastException);
            throw new RuntimeException("代码审查请求失败：" + errorMessage, lastException);
        } else {
            throw new RuntimeException("代码审查请求失败：未知错误");
        }
    }
    
    /**
     * 判断异常是否可重试
     */
    private boolean isRetryableException(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        
        // 可重试的网络异常
        return message.contains("Connection reset") ||
               message.contains("Connection refused") ||
               message.contains("Connection timeout") ||
               message.contains("Read timeout") ||
               message.contains("Connect timeout") ||
               message.contains("Socket timeout") ||
               message.contains("No route to host") ||
               message.contains("Network is unreachable") ||
               e instanceof java.net.SocketException ||
               e instanceof java.net.ConnectException ||
               e instanceof org.springframework.web.client.ResourceAccessException;
    }
    
    /**
     * 获取用户友好的错误消息
     */
    private String getErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return "网络连接异常";
        }
        
        if (message.contains("Connection reset")) {
            return "网络连接被重置，请检查网络连接或稍后重试";
        } else if (message.contains("Connection timeout") || message.contains("Connect timeout")) {
            return "连接超时，请检查网络连接";
        } else if (message.contains("Read timeout")) {
            return "请求超时，服务器响应时间过长";
        } else if (message.contains("Connection refused")) {
            return "连接被拒绝，服务器可能暂时不可用";
        } else if (message.contains("No route to host")) {
            return "无法连接到服务器，请检查网络设置";
        } else {
            return "网络请求失败：" + message;
        }
    }
    
    /**
     * 调用代码变更审查API（不等待返回值）
     */
    private void callReviewChangesAPI(String diffContent, List<ReviewFileItem> changedFiles) {
        LOG.info("=== 开始调用代码变更审查API ===");
        LOG.info("API URL: " + REVIEW_API_URL);
        
        // 重试机制
        int maxRetries = 3;
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < maxRetries) {
            try {
                if (retryCount > 0) {
                    LOG.info("=== 第 " + (retryCount + 1) + " 次重试调用代码变更审查API ===");
                    // 重试前等待一段时间
                    Thread.sleep(1000 * retryCount);
                }
                
                RestTemplate restTemplate = createUnsafeRestTemplate();
                
                // 设置请求头 - 改为JSON格式
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                LOG.info("设置Content-Type: " + MediaType.APPLICATION_JSON);
                
                // 添加token
                String token = authService.getToken();
                if (token != null) {
                    headers.add("token", token);
                    LOG.info("已添加token到请求头: " + token.substring(0, Math.min(20, token.length())) + "...");
                } else {
                    LOG.warn("未获取到有效token");
                }
                
                LOG.info("=== 开始构建JSON请求体 ===");
                
                // 构建DWFile数组（变更文件）
                List<DWFile> dwFiles = new ArrayList<>();
                LOG.info("=== 创建变更文件DWFile对象数组 ===");
                for (int i = 0; i < changedFiles.size(); i++) {
                    ReviewFileItem item = changedFiles.get(i);
                    LOG.info("处理变更文件 " + (i + 1) + "/" + changedFiles.size() + ":");
                    LOG.info("  - 文件名: " + item.getFileName());
                    LOG.info("  - 文件路径: " + item.getFilePath());
                    LOG.info("  - 内容大小: " + item.getContent().length() + " 字符");
                    
                    // 将文件内容转换为字节数组
                    byte[] fileBytes = item.getContent().getBytes("UTF-8");
                    LOG.info("  - 内容字节大小: " + fileBytes.length + " bytes");
                    
                    // 创建DWFile对象
                    DWFile dwFile = new DWFile(item.getFileName(), fileBytes);
                    dwFiles.add(dwFile);
                    
                    LOG.info("  ✅ 已创建变更文件DWFile对象: " + item.getFileName());
                    LOG.info("  - DWFile.fileName: " + dwFile.getFileName());
                    LOG.info("  - DWFile.fileByteArray长度: " + dwFile.getFileByteArray().length + " bytes");
                    LOG.info("  ----------------------------------------");
                }
                LOG.info("=== 变更文件DWFile数组构建完成，共创建了 " + dwFiles.size() + " 个文件对象 ===");
                
                // 创建diffFile（diff文件的DWFile对象）
                LOG.info("=== 创建diff文件DWFile对象 ===");
                LOG.info("diff文件名: git_changes.txt");
                LOG.info("diff内容大小: " + diffContent.length() + " 字符");
                
                byte[] diffBytes = diffContent.getBytes("UTF-8");
                LOG.info("diff内容字节大小: " + diffBytes.length + " bytes");
                
                DWFile diffFile = new DWFile("git_changes.txt", diffBytes);
                LOG.info("✅ 已创建diff文件DWFile对象");
                LOG.info("  - DWFile.fileName: " + diffFile.getFileName());
                LOG.info("  - DWFile.fileByteArray长度: " + diffFile.getFileByteArray().length + " bytes");
                
                // 构建fileInfo参数
                List<Map<String, Object>> fileInfoList = new ArrayList<>();
                for (ReviewFileItem item : changedFiles) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("fileName", item.getFileName());
                    fileInfo.put("filePath", item.getFilePath());
                    fileInfoList.add(fileInfo);
                }
                
                String fileInfoJson = gson.toJson(fileInfoList); // fileInfo必须是字符串
                
                // 构建完整的请求体，严格按服务端签名
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("files", dwFiles); // DWFile[]
                requestBody.put("diffFile", diffFile); // 单个DWFile对象
                requestBody.put("fileInfo", fileInfoJson); // 字符串

                LOG.info("====== 评审变更接口请求体 START ======");
                LOG.info(PRETTY_GSON.toJson(requestBody));
                if (requestBody.containsKey("diffFile")) {
                    Object diffFileObj = requestBody.get("diffFile");
                    LOG.info("------ diffFile 对象内容 ------");
                    LOG.info(PRETTY_GSON.toJson(diffFileObj));
                }
                LOG.info("====== 评审变更接口请求体 END ======");

                // 直接用gson.toJson(requestBody)作为请求体字符串
                HttpEntity<String> requestEntity = new HttpEntity<>(gson.toJson(requestBody), headers);
                
                LOG.info("=== JSON请求体构建完成 ===");
                LOG.info("请求体包含参数:");
                LOG.info("  - files: DWFile数组 (共" + dwFiles.size() + "个变更文件)");
                LOG.info("  - diffFile: DWFile对象 (diff文件)");
                LOG.info("  - fileInfo: JSON字符串");
                
                // 显示每个文件的详细信息
                for (int i = 0; i < dwFiles.size(); i++) {
                    DWFile dwFile = dwFiles.get(i);
                    LOG.info("    变更文件[" + i + "]: " + dwFile.getFileName() + 
                            " (字节数: " + dwFile.getFileByteArray().length + ")");
                }
                LOG.info("    diff文件: " + diffFile.getFileName() + 
                        " (字节数: " + diffFile.getFileByteArray().length + ")");
                
                LOG.info("请求体JSON预览: " + gson.toJson(requestBody).substring(0, Math.min(200, gson.toJson(requestBody).length())) + "...");
                
                LOG.info("=== 准备发送JSON变更审查请求 ===");
                LOG.info("Content-Type: application/json");
                LOG.info("传输方式: JSON格式，文件内容转为byte[]数组");
                
                // 创建请求实体
                LOG.info("JSON请求实体创建完成");
                
                // 发送请求
                LOG.info("开始发送HTTP请求...");
                long startTime = System.currentTimeMillis();
                ResponseEntity<String> response = restTemplate.postForEntity(REVIEW_API_URL, requestEntity, String.class);
                long endTime = System.currentTimeMillis();
                
                LOG.info("=== 变更审查API调用完成 ===");
                LOG.info("请求耗时: " + (endTime - startTime) + "ms");
                LOG.info("响应状态码: " + response.getStatusCode());
                LOG.info("响应头: " + response.getHeaders());
                LOG.info("响应内容长度: " + (response.getBody() != null ? response.getBody().length() : 0));
                LOG.info("响应内容: " + response.getBody());
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    LOG.info("代码变更审查请求发送成功");
                    return; // 成功，退出重试循环
                } else {
                    LOG.warn("API返回非200状态码: " + response.getStatusCode());
                    throw new RuntimeException("API调用失败：状态码 " + response.getStatusCode());
                }
                
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                
                LOG.error("=== 第 " + retryCount + " 次调用代码变更审查API时发生异常 ===");
                LOG.error("异常类型: " + e.getClass().getSimpleName());
                LOG.error("异常消息: " + e.getMessage());
                
                // 判断是否是可重试的异常
                boolean isRetryable = isRetryableException(e);
                if (!isRetryable) {
                    LOG.error("遇到不可重试的异常，停止重试");
                    break;
                }
                
                if (retryCount >= maxRetries) {
                    LOG.error("已达到最大重试次数 " + maxRetries + "，停止重试");
                    break;
                }
                
                LOG.info("将在 " + retryCount + " 秒后进行第 " + (retryCount + 1) + " 次重试");
            }
        }
        
        // 所有重试都失败了
        LOG.error("=== 代码变更审查API调用最终失败 ===");
        LOG.error("重试次数: " + retryCount);
        LOG.error("最后异常: " + (lastException != null ? lastException.getMessage() : "未知异常"));
        
        if (lastException != null) {
            String errorMessage = getErrorMessage(lastException);
            throw new RuntimeException("代码变更审查请求失败：" + errorMessage, lastException);
        } else {
            throw new RuntimeException("代码变更审查请求失败：未知错误");
        }
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

    // 在类成员区添加
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
} 