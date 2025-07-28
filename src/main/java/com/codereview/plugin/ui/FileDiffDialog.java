package com.codereview.plugin.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.codereview.plugin.service.FileDiffService;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件差异显示对话框
 * 类似于Git的冲突解决界面，三区域布局
 */
public class FileDiffDialog extends DialogWrapper {
    private static final Logger LOG = Logger.getInstance(FileDiffDialog.class);
    
    private final Project project;
    private final List<FileDiffService.FileDiffInfo> diffInfos;
    private final FileDiffService fileDiffService;
    
    private JTabbedPane tabbedPane;
    private JButton overwriteButton;
    private JButton skipButton;
    private JButton mergeButton;
    
    private int currentIndex = 0;
    private boolean[] resolvedFiles; // true表示已解决，false表示跳过
    private JTextArea[] resultTextAreas; // 保存每个标签页的中间区域文本组件
    private String[] finalContents; // 保存每个文件的最终内容

    public FileDiffDialog(Project project, List<FileDiffService.FileDiffInfo> diffInfos) {
        super(project);
        this.project = project;
        this.diffInfos = diffInfos;
        this.fileDiffService = FileDiffService.getInstance(project);
        this.resolvedFiles = new boolean[diffInfos.size()];
        this.resultTextAreas = new JTextArea[diffInfos.size()];
        this.finalContents = new String[diffInfos.size()];
        
        setTitle("文件差异检测 - " + diffInfos.size() + " 个文件有差异");
        setSize(1200, 700);
        setResizable(true);
        
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(1200, 700));
        
        // 创建标签页
        tabbedPane = new JBTabbedPane();
        
        // 为每个有差异的文件创建标签页
        for (int i = 0; i < diffInfos.size(); i++) {
            FileDiffService.FileDiffInfo diffInfo = diffInfos.get(i);
            JPanel tabPanel = createThreeWayDiffTab(diffInfo, i);
            tabbedPane.addTab(diffInfo.getFileName(), tabPanel);
        }
        
        // 添加标签页切换监听器
        tabbedPane.addChangeListener(e -> {
            currentIndex = tabbedPane.getSelectedIndex();
            updateButtonStates();
        });
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // 创建底部按钮面板
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 初始化按钮状态
        updateButtonStates();
        
        return mainPanel;
    }

    private JPanel createThreeWayDiffTab(FileDiffService.FileDiffInfo diffInfo, int index) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        // 文件信息面板
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(JBUI.Borders.customLine(Color.GRAY));
        infoPanel.setBorder(JBUI.Borders.empty(5));
        
        JBLabel fileLabel = new JBLabel("文件: " + diffInfo.getFileName());
        fileLabel.setFont(fileLabel.getFont().deriveFont(Font.BOLD));
        infoPanel.add(fileLabel, BorderLayout.NORTH);
        
        JBLabel pathLabel = new JBLabel("路径: " + diffInfo.getFilePath());
        pathLabel.setForeground(UIUtil.getInactiveTextColor());
        infoPanel.add(pathLabel, BorderLayout.CENTER);
        
        panel.add(infoPanel, BorderLayout.NORTH);
        
        // 三区域差异内容面板
        JPanel diffPanel = new JPanel(new GridLayout(1, 3, 5, 0));
        
        // 左侧：现有文件内容
        JPanel existingPanel = createContentPanel("现有文件", diffInfo.getExistingContent(), false);
        diffPanel.add(existingPanel);
        
        // 中间：最终结果（可编辑）
        JPanel resultPanel = createResultPanel(diffInfo, index);
        diffPanel.add(resultPanel);
        
        // 右侧：新文件内容
        JPanel newPanel = createContentPanel("新文件", diffInfo.getNewContent(), true);
        diffPanel.add(newPanel);
        
        panel.add(diffPanel, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createContentPanel(String title, String content, boolean isNew) {
        JPanel panel = new JPanel(new BorderLayout());
        Border border = JBUI.Borders.customLine(isNew ? new Color(0x28A745) : new Color(0xDC3545));
        panel.setBorder(border);
        panel.setBorder(JBUI.Borders.empty(5));
        
        // 标题
        JBLabel titleLabel = new JBLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(isNew ? new Color(0x28A745) : new Color(0xDC3545));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // 内容区域
        JTextArea textArea = new JTextArea(content);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(UIUtil.getPanelBackground());
        
        // 如果是新文件区域，添加差异高亮和可点击箭头
        if (isNew) {
            addDiffHighlightingWithArrows(textArea, content);
        }
        
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(350, 500));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createResultPanel(FileDiffService.FileDiffInfo diffInfo, int index) {
        JPanel panel = new JPanel(new BorderLayout());
        Border border = JBUI.Borders.customLine(new Color(0x007ACC));
        panel.setBorder(border);
        panel.setBorder(JBUI.Borders.empty(5));
        
        // 标题
        JBLabel titleLabel = new JBLabel("最终结果");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0x007ACC));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // 可编辑的结果区域
        JTextArea resultArea = new JTextArea();
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setBackground(UIUtil.getEditorPaneBackground());
        
        // 初始化内容（默认使用新文件内容作为基础）
        resultArea.setText(diffInfo.getNewContent());
        
        // 保存文本组件引用
        resultTextAreas[index] = resultArea;
        
        JBScrollPane scrollPane = new JBScrollPane(resultArea);
        scrollPane.setPreferredSize(new Dimension(350, 500));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 移除中间区域的操作按钮，统一使用底部按钮
        
        return panel;
    }

    private void addSyntaxHighlighting(JTextArea textArea, String existingContent, String newContent) {
        // 简单的差异高亮实现
        String[] existingLines = existingContent.split("\n");
        String[] newLines = newContent.split("\n");
        
        // 这里可以实现更复杂的差异高亮算法
        // 目前先使用简单的行级比较
        StringBuilder highlightedContent = new StringBuilder();
        
        int maxLines = Math.max(existingLines.length, newLines.length);
        for (int i = 0; i < maxLines; i++) {
            String existingLine = i < existingLines.length ? existingLines[i] : "";
            String newLine = i < newLines.length ? newLines[i] : "";
            
            if (!existingLine.equals(newLine)) {
                // 有差异的行，添加标记
                if (!existingLine.isEmpty()) {
                    highlightedContent.append("// 删除: ").append(existingLine).append("\n");
                }
                if (!newLine.isEmpty()) {
                    highlightedContent.append("// 新增: ").append(newLine).append("\n");
                }
            } else {
                // 相同的行
                highlightedContent.append(newLine).append("\n");
            }
        }
        
        textArea.setText(highlightedContent.toString());
    }

    private void addDiffHighlightingWithArrows(JTextArea textArea, String newContent) {
        // 获取当前文件的差异信息
        if (currentIndex < 0 || currentIndex >= diffInfos.size()) {
            return;
        }
        
        FileDiffService.FileDiffInfo diffInfo = diffInfos.get(currentIndex);
        String existingContent = diffInfo.getExistingContent();
        
        // 分析差异并添加可点击箭头
        String[] existingLines = existingContent.split("\n");
        String[] newLines = newContent.split("\n");
        
        StringBuilder highlightedContent = new StringBuilder();
        highlightedContent.append("// 点击箭头 → 将内容合并到结果区域\n");
        highlightedContent.append("// 点击行号选择整行内容\n\n");
        
        int maxLines = Math.max(existingLines.length, newLines.length);
        for (int i = 0; i < maxLines; i++) {
            String existingLine = i < existingLines.length ? existingLines[i] : "";
            String newLine = i < newLines.length ? newLines[i] : "";
            
            if (!existingLine.equals(newLine)) {
                // 有差异的行，添加可点击箭头
                if (!newLine.isEmpty()) {
                    highlightedContent.append(String.format("%3d → %s\n", i + 1, newLine));
                }
            } else {
                // 相同的行，正常显示
                highlightedContent.append(String.format("%3d   %s\n", i + 1, newLine));
            }
        }
        
        textArea.setText(highlightedContent.toString());
        
        // 添加鼠标点击事件
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleArrowClick(e, textArea, existingContent, newContent);
            }
        });
        
        // 设置光标样式
        textArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void handleArrowClick(MouseEvent e, JTextArea textArea, String existingContent, String newContent) {
        try {
            // 获取点击位置对应的行
            int offset = textArea.viewToModel(e.getPoint());
            int line = textArea.getLineOfOffset(offset);
            
            // 获取该行的文本
            int lineStart = textArea.getLineStartOffset(line);
            int lineEnd = textArea.getLineEndOffset(line);
            String lineText = textArea.getText(lineStart, lineEnd - lineStart).trim();
            
            // 检查是否点击了箭头行（包含 → 符号）
            if (lineText.contains("→")) {
                // 提取实际内容（去掉行号和箭头）
                String content = lineText.substring(lineText.indexOf("→") + 1).trim();
                
                // 将内容添加到结果区域
                if (currentIndex >= 0 && currentIndex < resultTextAreas.length) {
                    JTextArea resultArea = resultTextAreas[currentIndex];
                    String currentResult = resultArea.getText();
                    
                    // 将内容追加到结果区域
                    if (currentResult.isEmpty()) {
                        resultArea.setText(content);
                    } else {
                        resultArea.setText(currentResult + "\n" + content);
                    }
                    
                    // 显示提示
                    textArea.setToolTipText("已合并到结果区域: " + content);
                    
                    // 高亮显示已点击的行
                    highlightClickedLine(textArea, line);
                }
            }
        } catch (Exception ex) {
            LOG.warn("处理箭头点击时出错", ex);
        }
    }

    private void highlightClickedLine(JTextArea textArea, int line) {
        try {
            // 高亮显示已点击的行（通过添加注释标记）
            int lineStart = textArea.getLineStartOffset(line);
            int lineEnd = textArea.getLineEndOffset(line);
            String lineText = textArea.getText(lineStart, lineEnd - lineStart).trim();
            
            // 如果还没有标记，添加标记
            if (!lineText.contains(" ✓")) {
                String newLineText = lineText + " ✓";
                textArea.replaceRange(newLineText, lineStart, lineEnd);
            }
        } catch (Exception ex) {
            LOG.warn("高亮点击行时出错", ex);
        }
    }

    private String mergeContent(String existingContent, String newContent) {
        // 智能合并策略：保留相同的行，对于不同的行选择保留新版本
        String[] existingLines = existingContent.split("\n");
        String[] newLines = newContent.split("\n");
        
        StringBuilder merged = new StringBuilder();
        merged.append("// ===== 智能合并结果 =====\n");
        
        int maxLines = Math.max(existingLines.length, newLines.length);
        for (int i = 0; i < maxLines; i++) {
            String existingLine = i < existingLines.length ? existingLines[i] : "";
            String newLine = i < newLines.length ? newLines[i] : "";
            
            if (existingLine.equals(newLine)) {
                // 相同的行，直接保留
                merged.append(newLine).append("\n");
            } else {
                // 不同的行，优先保留新版本，但添加注释说明
                if (!newLine.isEmpty()) {
                    merged.append(newLine).append(" // 合并：保留新版本\n");
                } else if (!existingLine.isEmpty()) {
                    merged.append("// 删除: ").append(existingLine).append("\n");
                }
            }
        }
        
        return merged.toString();
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(JBUI.Borders.empty(10, 0, 0, 0));
        
        // 保留原文件按钮
        JButton useOriginalButton = new JButton("保留原文件");
        useOriginalButton.addActionListener(e -> {
            if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
                FileDiffService.FileDiffInfo diffInfo = diffInfos.get(currentIndex);
                String originalContent = diffInfo.getExistingContent();
                resultTextAreas[currentIndex].setText(originalContent);
                finalContents[currentIndex] = originalContent; // 保存最终内容
                resolvedFiles[currentIndex] = true;
                moveToNextFile();
            }
        });
        panel.add(useOriginalButton);
        
        // 使用新文件按钮
        JButton useNewButton = new JButton("使用新文件");
        useNewButton.addActionListener(e -> {
            if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
                FileDiffService.FileDiffInfo diffInfo = diffInfos.get(currentIndex);
                String newContent = diffInfo.getNewContent();
                resultTextAreas[currentIndex].setText(newContent);
                finalContents[currentIndex] = newContent; // 保存最终内容
                resolvedFiles[currentIndex] = true;
                moveToNextFile();
            }
        });
        panel.add(useNewButton);
        
        // 使用合并结果按钮
        JButton useResultButton = new JButton("使用合并结果");
        useResultButton.addActionListener(e -> {
            if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
                // 保存当前结果区域的内容
                String mergedContent = resultTextAreas[currentIndex].getText();
                finalContents[currentIndex] = mergedContent; // 保存最终内容
                resolvedFiles[currentIndex] = true;
                moveToNextFile();
            }
        });
        panel.add(useResultButton);
        
        return panel;
    }

    private void updateButtonStates() {
        if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
            boolean isResolved = resolvedFiles[currentIndex];
            
            // 更新标签页标题
            FileDiffService.FileDiffInfo diffInfo = diffInfos.get(currentIndex);
            String tabTitle = diffInfo.getFileName();
            if (isResolved) {
                tabTitle += " ✓";
            }
            tabbedPane.setTitleAt(currentIndex, tabTitle);
        }
    }

    private void skipCurrentFile(ActionEvent e) {
        if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
            resolvedFiles[currentIndex] = true;
            moveToNextFile();
        }
    }

    private void overwriteCurrentFile(ActionEvent e) {
        if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
            resolvedFiles[currentIndex] = true;
            moveToNextFile();
        }
    }

    private void mergeCurrentFile(ActionEvent e) {
        if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
            resolvedFiles[currentIndex] = true;
            moveToNextFile();
        }
    }

    private void moveToNextFile() {
        // 查找下一个未解决的文件
        int nextIndex = currentIndex + 1;
        while (nextIndex < diffInfos.size() && resolvedFiles[nextIndex]) {
            nextIndex++;
        }
        
        if (nextIndex < diffInfos.size()) {
            tabbedPane.setSelectedIndex(nextIndex);
        } else {
            // 所有文件都已处理，关闭对话框
            close(OK_EXIT_CODE);
        }
    }

    /**
     * 获取处理结果
     * @return 处理结果列表，包含最终内容和操作类型
     */
    public static class ResolutionResult {
        private final String content;
        private final ResolutionType type;
        
        public ResolutionResult(String content, ResolutionType type) {
            this.content = content;
            this.type = type;
        }
        
        public String getContent() { return content; }
        public ResolutionType getType() { return type; }
    }
    
    public enum ResolutionType {
        SKIP,      // 跳过
        ORIGINAL,  // 使用原文件
        NEW,       // 使用新文件
        MERGED     // 使用合并结果
    }
    
    public ResolutionResult[] getResolutionResults() {
        ResolutionResult[] results = new ResolutionResult[diffInfos.size()];
        for (int i = 0; i < diffInfos.size(); i++) {
            if (resolvedFiles[i]) {
                // 根据最终内容判断操作类型
                String finalContent = finalContents[i];
                String originalContent = diffInfos.get(i).getExistingContent();
                String newContent = diffInfos.get(i).getNewContent();
                
                ResolutionType type;
                if (finalContent.equals(originalContent)) {
                    type = ResolutionType.ORIGINAL;
                } else if (finalContent.equals(newContent)) {
                    type = ResolutionType.NEW;
                } else {
                    type = ResolutionType.MERGED;
                }
                
                results[i] = new ResolutionResult(finalContent, type);
            } else {
                results[i] = new ResolutionResult(null, ResolutionType.SKIP);
            }
        }
        return results;
    }

    /**
     * 显示单个文件差异对话框
     */
    public static boolean showSingleFileDiff(Project project, FileDiffService.FileDiffInfo diffInfo) {
        List<FileDiffService.FileDiffInfo> list = new ArrayList<>();
        list.add(diffInfo);
        FileDiffDialog dialog = new FileDiffDialog(project, list);
        return dialog.showAndGet();
    }

    /**
     * 显示多个文件差异对话框
     */
    public static ResolutionResult[] showMultipleFileDiff(Project project, List<FileDiffService.FileDiffInfo> diffInfos) {
        if (diffInfos.isEmpty()) {
            return new ResolutionResult[0];
        }
        
        FileDiffDialog dialog = new FileDiffDialog(project, diffInfos);
        if (dialog.showAndGet()) {
            return dialog.getResolutionResults();
        } else {
            // 用户取消，全部跳过
            return new ResolutionResult[diffInfos.size()];
        }
    }
} 