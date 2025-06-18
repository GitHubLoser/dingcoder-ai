package com.codereview.plugin.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码审查结果模型
 */
public class ReviewResult {
    // 文件信息
    private String fileName;
    private int lineCount;
    
    // 评分信息
    private int overallScore;
    private int codeStyleScore;
    private int maintainabilityScore;
    private int complexityScore;
    
    // 发现的问题列表
    private List<ReviewIssue> issues = new ArrayList<>();
    
    // 建议列表
    private List<String> suggestions = new ArrayList<>();
    
    // 详细分析
    private String detailedAnalysis;

    // Getter 和 Setter
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public int getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(int overallScore) {
        this.overallScore = overallScore;
    }

    public int getCodeStyleScore() {
        return codeStyleScore;
    }

    public void setCodeStyleScore(int codeStyleScore) {
        this.codeStyleScore = codeStyleScore;
    }

    public int getMaintainabilityScore() {
        return maintainabilityScore;
    }

    public void setMaintainabilityScore(int maintainabilityScore) {
        this.maintainabilityScore = maintainabilityScore;
    }

    public int getComplexityScore() {
        return complexityScore;
    }

    public void setComplexityScore(int complexityScore) {
        this.complexityScore = complexityScore;
    }

    public List<ReviewIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ReviewIssue> issues) {
        this.issues = issues;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public String getDetailedAnalysis() {
        return detailedAnalysis;
    }

    public void setDetailedAnalysis(String detailedAnalysis) {
        this.detailedAnalysis = detailedAnalysis;
    }

    /**
     * 审查问题项
     */
    public static class ReviewIssue {
        private String type; // 问题类型
        private String description; // 问题描述
        private int line; // 问题所在行
        private String severity; // 严重程度
        private String suggestion; // 修复建议

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }

        public String getSeverity() {
            return severity;
        }

        public void setSeverity(String severity) {
            this.severity = severity;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }
    }
} 