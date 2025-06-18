package com.codereview.plugin.service;

import com.codereview.plugin.model.ReviewResult;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;

/**
 * AI服务接口 
 * 负责与AI API进行通信，获取代码审查结果
 */
@Service
public final class AIService {
    private static final Logger LOG = Logger.getInstance(AIService.class);
    
    private String apiKey;
    private String apiEndpoint = "https://api.example.com/code-review";
    
    /**
     * 获取AI服务的实例
     */
    public static AIService getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication().getService(AIService.class);
    }
    
    /**
     * 设置API密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    /**
     * 设置API端点
     */
    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }
    
    /**
     * 发送代码给AI服务进行审查
     * 
     * @param code 代码内容
     * @param language 代码语言
     * @param callback 回调函数，用于处理审查结果
     */
    public void analyzeCode(String code, String language, AIResponseCallback callback) {
        // 实际应用中，这里应该发送HTTP请求到AI服务
        // 现在我们只是返回一个模拟的结果
        LOG.info("模拟发送代码到AI服务进行分析...");
        
        // 模拟网络延迟
        new Thread(() -> {
            try {
                Thread.sleep(1500); // 模拟1.5秒的处理时间
                
                // 创建模拟的审查结果
                ReviewResult result = new ReviewResult();
                result.setFileName("Example.java");
                result.setLineCount(code.split("\n").length);
                result.setOverallScore(85);
                result.setCodeStyleScore(90);
                result.setMaintainabilityScore(80);
                result.setComplexityScore(85);
                
                // 添加一些模拟的建议
                result.getSuggestions().add("考虑添加更多的单元测试来提高代码覆盖率");
                result.getSuggestions().add("部分方法可以被重构成更小的函数以提高可读性");
                
                // 添加一个模拟的问题
                ReviewResult.ReviewIssue issue = new ReviewResult.ReviewIssue();
                issue.setType("潜在的空指针异常");
                issue.setDescription("在使用对象前没有进行null检查");
                issue.setLine(24);
                issue.setSeverity("警告");
                issue.setSuggestion("在使用对象前添加null检查");
                result.getIssues().add(issue);
                
                // 设置详细分析
                result.setDetailedAnalysis("代码整体结构良好，但在错误处理和边界条件检查方面可以做得更好。\n" +
                        "有几处地方可以考虑使用更现代的Java特性如Optional或Stream API来简化代码。");
                
                // 调用回调函数返回结果
                callback.onSuccess(result);
                
            } catch (InterruptedException e) {
                callback.onError("处理被中断：" + e.getMessage());
            } catch (Exception e) {
                callback.onError("发生错误：" + e.getMessage());
            }
        }).start();
    }
    
    /**
     * AI响应回调接口
     */
    public interface AIResponseCallback {
        void onSuccess(ReviewResult result);
        void onError(String errorMessage);
    }
} 