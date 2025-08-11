package com.codereview.plugin.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.codereview.plugin.service.FileDiffService;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简化的文件差异显示对话框
 * 两区域布局：左侧可编辑的原文件，右侧只读的新文件
 * 支持实时差异高亮显示
 */
public class FileDiffDialog extends DialogWrapper {
    private static final Logger LOG = Logger.getInstance(FileDiffDialog.class);
    
    private final Project project;
    private final List<FileDiffService.FileDiffInfo> diffInfos;
    private final FileDiffService fileDiffService;
    
    private JTabbedPane tabbedPane;
    private JButton selectLeftButton;
    private JButton selectRightButton;
    
    private int currentIndex = 0;
    private boolean[] resolvedFiles; // true表示已解决，false表示跳过
    private JTextArea[] leftTextAreas; // 左侧可编辑区域
    private JTextArea[] rightTextAreas; // 右侧只读区域
    private String[] finalContents; // 保存每个文件的最终内容
    
    // 差异高亮相关
    private static final Color DIFF_HIGHLIGHT_COLOR = new Color(255, 255, 170, 45); // 更浅更透明的黄色
    private static final Color DIFF_BORDER_COLOR = new Color(255, 235, 180); // 更浅的黄色边框
    private boolean[] diffHighlighted; // 标记是否已高亮显示差异

    // 同步滚动相关
    private JBScrollPane[] leftScrollPanes;
    private JBScrollPane[] rightScrollPanes;
    private boolean isSyncingScroll = false;

    public FileDiffDialog(Project project, List<FileDiffService.FileDiffInfo> diffInfos) {
        super(project);
        this.project = project;
        this.diffInfos = diffInfos;
        this.fileDiffService = FileDiffService.getInstance(project);
        this.resolvedFiles = new boolean[diffInfos.size()];
        this.leftTextAreas = new JTextArea[diffInfos.size()];
        this.rightTextAreas = new JTextArea[diffInfos.size()];
        this.finalContents = new String[diffInfos.size()];
        this.diffHighlighted = new boolean[diffInfos.size()];
        this.leftScrollPanes = new JBScrollPane[diffInfos.size()];
        this.rightScrollPanes = new JBScrollPane[diffInfos.size()];
        
        setTitle("文件差异检测 - " + diffInfos.size() + " 个文件有差异");
        setSize(1000, 600);
        setResizable(true);
        
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(1000, 600));
        
        // 创建标签页
        tabbedPane = new JTabbedPane();
        
        // 为每个有差异的文件创建标签页
        for (int i = 0; i < diffInfos.size(); i++) {
            FileDiffService.FileDiffInfo diffInfo = diffInfos.get(i);
            JPanel tabPanel = createTwoWayDiffTab(diffInfo, i);
            tabbedPane.addTab(diffInfo.getFileName(), tabPanel);
        }
        
        // 添加标签页切换监听器
        tabbedPane.addChangeListener(e -> {
            currentIndex = tabbedPane.getSelectedIndex();
            updateButtonStates();
            // 切换到新标签页时高亮差异
            if (currentIndex >= 0 && !diffHighlighted[currentIndex]) {
                highlightDifferences(currentIndex);
            }
        });
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // 创建底部按钮面板
        JPanel buttonPanel = createButtonPanel();
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 初始化按钮状态
        updateButtonStates();
        
        // 初始化第一个文件的差异高亮
        if (diffInfos.size() > 0) {
            SwingUtilities.invokeLater(() -> highlightDifferences(0));
        }
        
        return mainPanel;
    }

    private JPanel createTwoWayDiffTab(FileDiffService.FileDiffInfo diffInfo, int index) {
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
        
        // 两区域差异内容面板
        JPanel diffPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        
        // 左侧：原文件内容（可编辑）
        JPanel leftPanel = createLeftPanel(diffInfo, index);
        diffPanel.add(leftPanel);
        
        // 右侧：新文件内容（只读，支持复制）
        JPanel rightPanel = createRightPanel(diffInfo, index);
        diffPanel.add(rightPanel);

        // 绑定同步滚动
        setupSynchronizedScrolling(index);
        
        panel.add(diffPanel, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createLeftPanel(FileDiffService.FileDiffInfo diffInfo, int index) {
        JPanel panel = new JPanel(new BorderLayout());
        Border border = JBUI.Borders.customLine(new Color(0x007ACC));
        panel.setBorder(border);
        panel.setBorder(JBUI.Borders.empty(5));
        
        // 标题
        JBLabel titleLabel = new JBLabel("原文件内容（可编辑）");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0x007ACC));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // 可编辑的内容区域
        JTextArea textArea = new JTextArea(diffInfo.getExistingContent());
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(UIUtil.getEditorPaneBackground());
        
        // 保存文本组件引用
        leftTextAreas[index] = textArea;
        
        // 添加文本变化监听器，实时高亮差异
        textArea.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                SwingUtilities.invokeLater(() -> highlightDifferences(index));
            }
            
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                SwingUtilities.invokeLater(() -> highlightDifferences(index));
            }
            
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                SwingUtilities.invokeLater(() -> highlightDifferences(index));
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(450, 400));
        panel.add(scrollPane, BorderLayout.CENTER);

        // 保存滚动面板引用，用于同步滚动
        leftScrollPanes[index] = scrollPane;
        
        return panel;
    }

    private JPanel createRightPanel(FileDiffService.FileDiffInfo diffInfo, int index) {
        JPanel panel = new JPanel(new BorderLayout());
        Border border = JBUI.Borders.customLine(new Color(0x28A745));
        panel.setBorder(border);
        panel.setBorder(JBUI.Borders.empty(5));
        
        // 标题
        JBLabel titleLabel = new JBLabel("新文件内容（只读，支持复制）");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setForeground(new Color(0x28A745));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // 只读的内容区域
        JTextArea textArea = new JTextArea(diffInfo.getNewContent());
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBackground(UIUtil.getPanelBackground());
        
        // 保存文本组件引用
        rightTextAreas[index] = textArea;
        
        // 添加复制功能
        textArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // 双击复制全部内容
                    copyToClipboard(textArea.getText());
                    showCopyMessage();
                }
            }
        });
        
        // 添加右键菜单
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyAllItem = new JMenuItem("复制全部内容");
        copyAllItem.addActionListener(e -> {
            copyToClipboard(textArea.getText());
            showCopyMessage();
        });
        popupMenu.add(copyAllItem);
        
        JMenuItem copySelectedItem = new JMenuItem("复制选中内容");
        copySelectedItem.addActionListener(e -> {
            String selectedText = textArea.getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                copyToClipboard(selectedText);
                showCopyMessage();
            }
        });
        popupMenu.add(copySelectedItem);
        
        textArea.setComponentPopupMenu(popupMenu);
        
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(450, 400));
        panel.add(scrollPane, BorderLayout.CENTER);

        // 保存滚动面板引用，用于同步滚动
        rightScrollPanes[index] = scrollPane;
        
        return panel;
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        } catch (Exception e) {
            LOG.error("复制到剪贴板失败", e);
        }
    }

    private void showCopyMessage() {
        JOptionPane.showMessageDialog(getContentPane(), "内容已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(JBUI.Borders.empty(10, 0, 0, 0));
        
        // 选择左边按钮
        selectLeftButton = new JButton("选择左边");
        selectLeftButton.setPreferredSize(new Dimension(100, 32));
        selectLeftButton.addActionListener(e -> {
            if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
                // 使用左侧编辑后的内容
                String leftContent = leftTextAreas[currentIndex].getText();
                finalContents[currentIndex] = leftContent;
                resolvedFiles[currentIndex] = true;
                moveToNextFile();
            }
        });
        panel.add(selectLeftButton);
        
        // 选择右边按钮
        selectRightButton = new JButton("选择右边");
        selectRightButton.setPreferredSize(new Dimension(100, 32));
        selectRightButton.addActionListener(e -> {
            if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
                // 使用右侧新文件内容
                FileDiffService.FileDiffInfo diffInfo = diffInfos.get(currentIndex);
                finalContents[currentIndex] = diffInfo.getNewContent();
                resolvedFiles[currentIndex] = true;
                moveToNextFile();
            }
        });
        panel.add(selectRightButton);
        
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
            
            // 更新按钮状态
            selectLeftButton.setEnabled(!isResolved);
            selectRightButton.setEnabled(!isResolved);
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
        ORIGINAL,  // 使用原文件（左侧编辑后的内容）
        NEW        // 使用新文件（右侧内容）
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
                    // 如果内容被编辑过，也归类为ORIGINAL
                    type = ResolutionType.ORIGINAL;
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

    /**
     * 高亮显示两个文本区域的差异
     */
    private void highlightDifferences(int index) {
        if (index < 0 || index >= leftTextAreas.length || 
            leftTextAreas[index] == null || rightTextAreas[index] == null) {
            return;
        }
        
        try {
            String leftText = leftTextAreas[index].getText();
            String rightText = rightTextAreas[index].getText();
            
            // 清除之前的高亮
            clearHighlights(leftTextAreas[index]);
            clearHighlights(rightTextAreas[index]);
            
            // 计算差异并高亮
            List<DiffSegment> diffs = computeDifferences(leftText, rightText);
            
            // 应用高亮到左侧文本区域
            applyHighlights(leftTextAreas[index], diffs, true);
            
            // 应用高亮到右侧文本区域
            applyHighlights(rightTextAreas[index], diffs, false);
            
            diffHighlighted[index] = true;
            
        } catch (Exception e) {
            LOG.error("高亮差异时出错: " + index, e);
        }
    }
    
    /**
     * 差异片段
     */
    private static class DiffSegment {
        final int start;
        final int end;
        final boolean isDifferent;
        
        DiffSegment(int start, int end, boolean isDifferent) {
            this.start = start;
            this.end = end;
            this.isDifferent = isDifferent;
        }
    }
    
    /**
     * 计算两个文本的差异
     */
    private List<DiffSegment> computeDifferences(String text1, String text2) {
        List<DiffSegment> diffs = new ArrayList<>();
        
        // 智能差异比较：先比较行，再比较行内差异
        String[] lines1 = text1.split("\n", -1);
        String[] lines2 = text2.split("\n", -1);
        
        int maxLines = Math.max(lines1.length, lines2.length);
        
        for (int i = 0; i < maxLines; i++) {
            String line1 = i < lines1.length ? lines1[i] : "";
            String line2 = i < lines2.length ? lines2[i] : "";
            
            if (!line1.equals(line2)) {
                // 计算这一行在文本中的位置
                int start1 = getLineStartPosition(text1, i);
                int end1 = start1 + line1.length();
                int start2 = getLineStartPosition(text2, i);
                int end2 = start2 + line2.length();
                
                // 添加整行差异
                diffs.add(new DiffSegment(start1, end1, true));
                diffs.add(new DiffSegment(start2, end2, true));
                
                // 如果行长度差异很大，可能是新增或删除的行
                if (Math.abs(line1.length() - line2.length()) > 10) {
                    // 标记为显著差异
                    continue;
                }
                
                // 尝试找到行内的具体差异位置
                List<DiffSegment> inlineDiffs = findInlineDifferences(line1, line2, start1, start2);
                diffs.addAll(inlineDiffs);
            }
        }
        
        return diffs;
    }
    
    /**
     * 查找行内差异
     */
    private List<DiffSegment> findInlineDifferences(String line1, String line2, int start1, int start2) {
        List<DiffSegment> inlineDiffs = new ArrayList<>();
        
        // 简单的字符级差异检测
        int minLength = Math.min(line1.length(), line2.length());
        int diffStart = -1;
        
        for (int i = 0; i < minLength; i++) {
            if (line1.charAt(i) != line2.charAt(i)) {
                if (diffStart == -1) {
                    diffStart = i;
                }
            } else if (diffStart != -1) {
                // 差异结束，添加差异片段
                inlineDiffs.add(new DiffSegment(start1 + diffStart, start1 + i, true));
                inlineDiffs.add(new DiffSegment(start2 + diffStart, start2 + i, true));
                diffStart = -1;
            }
        }
        
        // 处理行尾差异
        if (diffStart != -1) {
            inlineDiffs.add(new DiffSegment(start1 + diffStart, start1 + line1.length(), true));
            inlineDiffs.add(new DiffSegment(start2 + diffStart, start2 + line2.length(), true));
        }
        
        return inlineDiffs;
    }
    
    /**
     * 获取指定行在文本中的起始位置
     */
    private int getLineStartPosition(String text, int lineIndex) {
        String[] lines = text.split("\n", -1);
        int position = 0;
        
        for (int i = 0; i < lineIndex && i < lines.length; i++) {
            position += lines[i].length() + 1; // +1 for newline
        }
        
        return position;
    }
    
    /**
     * 清除文本区域的高亮
     */
    private void clearHighlights(JTextArea textArea) {
        if (textArea.getHighlighter() != null) {
            textArea.getHighlighter().removeAllHighlights();
        }
    }
    
    /**
     * 应用高亮到文本区域
     */
    private void applyHighlights(JTextArea textArea, List<DiffSegment> diffs, boolean isLeft) {
        try {
            Highlighter highlighter = textArea.getHighlighter();
            
            for (DiffSegment diff : diffs) {
                if (diff.isDifferent) {
                    // 创建高亮样式
                    Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(DIFF_HIGHLIGHT_COLOR);
                    
                    // 应用高亮
                    highlighter.addHighlight(diff.start, diff.end, painter);
                }
            }
        } catch (Exception e) {
            LOG.error("应用高亮时出错", e);
        }
    }

    // 同步两个区域的垂直滚动（按比例）
    private void setupSynchronizedScrolling(int index) {
        JBScrollPane left = leftScrollPanes[index];
        JBScrollPane right = rightScrollPanes[index];
        if (left == null || right == null) {
            return;
        }
        JScrollBar leftBar = left.getVerticalScrollBar();
        JScrollBar rightBar = right.getVerticalScrollBar();

        leftBar.addAdjustmentListener(e -> syncScroll(leftBar, rightBar));
        rightBar.addAdjustmentListener(e -> syncScroll(rightBar, leftBar));
    }

    private void syncScroll(JScrollBar source, JScrollBar target) {
        if (isSyncingScroll) {
            return;
        }
        isSyncingScroll = true;
        try {
            int srcMax = Math.max(1, source.getMaximum() - source.getVisibleAmount());
            int tgtMax = Math.max(1, target.getMaximum() - target.getVisibleAmount());
            int srcVal = source.getValue();
            int mapped = (int) Math.round((srcVal / (double) srcMax) * tgtMax);
            target.setValue(mapped);
        } finally {
            isSyncingScroll = false;
        }
    }
} 