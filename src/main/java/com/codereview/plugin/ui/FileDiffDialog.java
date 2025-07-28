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
import java.util.List;
import java.util.ArrayList;

/**
 * 文件差异显示对话框
 * 类似于Git的冲突解决界面
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

    public FileDiffDialog(Project project, List<FileDiffService.FileDiffInfo> diffInfos) {
        super(project);
        this.project = project;
        this.diffInfos = diffInfos;
        this.fileDiffService = FileDiffService.getInstance(project);
        this.resolvedFiles = new boolean[diffInfos.size()];
        
        setTitle("文件差异检测 - " + diffInfos.size() + " 个文件有差异");
        setSize(800, 600);
        setResizable(true);
        
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setPreferredSize(new Dimension(800, 600));
        
        // 创建标签页
        tabbedPane = new JBTabbedPane();
        
        // 为每个有差异的文件创建标签页
        for (int i = 0; i < diffInfos.size(); i++) {
            FileDiffService.FileDiffInfo diffInfo = diffInfos.get(i);
            JPanel tabPanel = createDiffTab(diffInfo, i);
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

    private JPanel createDiffTab(FileDiffService.FileDiffInfo diffInfo, int index) {
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
        
        // 差异内容面板
        JPanel diffPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        
        // 现有文件内容
        JPanel existingPanel = createContentPanel("现有文件内容", diffInfo.getExistingContent(), false);
        diffPanel.add(existingPanel);
        
        // 新文件内容
        JPanel newPanel = createContentPanel("新文件内容", diffInfo.getNewContent(), true);
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
        
        JBScrollPane scrollPane = new JBScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(350, 400));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(JBUI.Borders.empty(10, 0, 0, 0));
        
        // 跳过按钮
        skipButton = new JButton("跳过此文件");
        skipButton.addActionListener(this::skipCurrentFile);
        panel.add(skipButton);
        
        // 覆盖按钮
        overwriteButton = new JButton("覆盖文件");
        overwriteButton.addActionListener(this::overwriteCurrentFile);
        panel.add(overwriteButton);
        
        // 合并按钮（保留现有内容）
        mergeButton = new JButton("保留现有");
        mergeButton.addActionListener(this::mergeCurrentFile);
        panel.add(mergeButton);
        
        return panel;
    }

    private void updateButtonStates() {
        if (currentIndex >= 0 && currentIndex < diffInfos.size()) {
            boolean isResolved = resolvedFiles[currentIndex];
            
            skipButton.setEnabled(!isResolved);
            overwriteButton.setEnabled(!isResolved);
            mergeButton.setEnabled(!isResolved);
            
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
     * @return 处理结果列表，null表示跳过，true表示覆盖，false表示保留现有
     */
    public Boolean[] getResolutionResults() {
        Boolean[] results = new Boolean[diffInfos.size()];
        for (int i = 0; i < diffInfos.size(); i++) {
            if (resolvedFiles[i]) {
                // 这里简化处理，实际可以根据用户选择返回不同的结果
                results[i] = true; // 覆盖
            } else {
                results[i] = null; // 跳过
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
    public static Boolean[] showMultipleFileDiff(Project project, List<FileDiffService.FileDiffInfo> diffInfos) {
        if (diffInfos.isEmpty()) {
            return new Boolean[0];
        }
        
        FileDiffDialog dialog = new FileDiffDialog(project, diffInfos);
        if (dialog.showAndGet()) {
            return dialog.getResolutionResults();
        } else {
            // 用户取消，全部跳过
            return new Boolean[diffInfos.size()];
        }
    }
} 