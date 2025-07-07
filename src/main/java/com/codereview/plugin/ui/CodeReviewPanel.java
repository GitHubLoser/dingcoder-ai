package com.codereview.plugin.ui;

import com.codereview.plugin.service.ReviewService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.DocumentAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.util.ui.UIUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 代码审查面板，包含评审文件、评审变更、评审结果三个区域
 */
public class CodeReviewPanel extends JBPanel<CodeReviewPanel> {
    private static final Logger LOG = Logger.getInstance(CodeReviewPanel.class);
    
    private final Project project;
    private final ReviewService reviewService;
    
    // UI组件
    private JList<ReviewFileItem> fileList;
    private DefaultListModel<ReviewFileItem> fileListModel;
    private JTextArea changesArea;
    private JTextArea resultArea;
    private JButton addFileButton;
    private JButton reviewFileButton;
    private JButton getGitDiffButton;
    private JButton reviewChangesButton;
    
    // 静态引用，供外部访问
    private static CodeReviewPanel instance;
    
    // 颜色主题
    private static final Color BACKGROUND_COLOR = new JBColor(Color.WHITE, new Color(43, 43, 43));
    private static final Color PANEL_BACKGROUND = new JBColor(new Color(250, 250, 250), new Color(60, 60, 60));
    private static final Color BORDER_COLOR = new JBColor(new Color(200, 200, 200), new Color(80, 80, 80));
    
    public CodeReviewPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.reviewService = ReviewService.getInstance(project);
        instance = this; // 设置静态引用
        
        initializeUI();
    }
    
    public static CodeReviewPanel getInstance() {
        return instance;
    }
    
    private void initializeUI() {
        setBackground(BACKGROUND_COLOR);
        setBorder(JBUI.Borders.empty(10));
        
        // 创建垂直分割面板 - 三个区域一列显示
        JSplitPane topSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        topSplitPane.setDividerLocation(200);
        topSplitPane.setResizeWeight(0.3);
        
        JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        bottomSplitPane.setDividerLocation(200);
        bottomSplitPane.setResizeWeight(0.5);
        
        // 上：评审文件区域
        JPanel filePanel = createFilePanel();
        topSplitPane.setTopComponent(filePanel);
        
        // 中：评审变更区域
        JPanel changesPanel = createChangesPanel();
        bottomSplitPane.setTopComponent(changesPanel);
        
        // 下：评审结果区域
        JPanel resultPanel = createResultPanel();
        bottomSplitPane.setBottomComponent(resultPanel);
        
        topSplitPane.setBottomComponent(bottomSplitPane);
        
        add(topSplitPane, BorderLayout.CENTER);
    }
    
    private JPanel createFilePanel() {
        JPanel panel = new JBPanel<>(new BorderLayout());
        panel.setBackground(PANEL_BACKGROUND);
        
        // 创建带标题的边框
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "评审文件"
        );
        border.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.setBorder(border);
        
        // 文件列表区域
        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileList.setCellRenderer(new ReviewFileItemRenderer());
        fileList.setBorder(JBUI.Borders.empty(5));
        
        // 添加鼠标监听器处理右键菜单和删除按钮点击
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    int index = fileList.locationToIndex(e.getPoint());
                    if (index >= 0 && index < fileListModel.size()) {
                        ReviewFileItem item = fileListModel.get(index);
                        if (!item.isPlaceholder()) {
                            // 检查是否点击了删除按钮区域（右侧25px区域）
                            Rectangle cellBounds = fileList.getCellBounds(index, index);
                            if (cellBounds != null) {
                                int cellWidth = cellBounds.width;
                                int clickX = e.getX() - cellBounds.x;
                                
                                // 如果点击在右侧25px区域内，认为是点击了删除按钮
                                if (clickX > cellWidth - 35) {
                                    deleteFileItem(item);
                                    e.consume(); // 阻止事件继续传播
                                }
                            }
                        }
                    }
                }
            }
        });
        
        // 添加删除键支持
        fileList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        fileList.getActionMap().put("delete", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedFiles();
            }
        });
        
        // 初始提示
        fileListModel.addElement(new ReviewFileItem("提示", "点击 + 按钮或右键菜单添加要评审的文件", 0, 0, true));
        
        JBScrollPane scrollPane = new JBScrollPane(fileList);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 按钮区域
        JPanel buttonPanel = new JBPanel<>(new FlowLayout());
        buttonPanel.setOpaque(false);
        
        addFileButton = new JButton("+ 添加文件");
        addFileButton.addActionListener(this::onAddFile);
        buttonPanel.add(addFileButton);
        
        reviewFileButton = new JButton("开始评审");
        reviewFileButton.addActionListener(this::onReviewFiles);
        buttonPanel.add(reviewFileButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createChangesPanel() {
        JPanel panel = new JBPanel<>(new BorderLayout());
        panel.setBackground(PANEL_BACKGROUND);
        
        // 创建带标题的边框
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "评审变更"
        );
        border.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.setBorder(border);
        
        // 变更内容区域
        changesArea = new JTextArea();
        changesArea.setEditable(false);
        changesArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        changesArea.setText("点击\"获取Git变更\"按钮来获取本地代码变更：\n\n" +
                          "功能说明：\n" +
                          "• 执行 git diff 命令获取未提交的变更\n" +
                          "• 显示具体的代码变更对比\n" +
                          "• 新增、修改、删除的行数统计\n" +
                          "• 然后可以对变更内容进行AI评审");
        
        JBScrollPane scrollPane = new JBScrollPane(changesArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // 按钮区域
        JPanel buttonPanel = new JBPanel<>(new FlowLayout());
        buttonPanel.setOpaque(false);
        
        getGitDiffButton = new JButton("获取Git变更");
        getGitDiffButton.addActionListener(this::onGetGitDiff);
        buttonPanel.add(getGitDiffButton);
        
        reviewChangesButton = new JButton("评审变更");
        reviewChangesButton.addActionListener(this::onReviewChanges);
        buttonPanel.add(reviewChangesButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createResultPanel() {
        JPanel panel = new JBPanel<>(new BorderLayout());
        panel.setBackground(PANEL_BACKGROUND);
        
        // 创建带标题的边框
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "评审结果"
        );
        border.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.setBorder(border);
        
        // 结果显示区域
        resultArea = new JTextArea();
        resultArea.setEditable(false);
        resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        resultArea.setText("点击\"审查当前文件\"或\"审查选中文件\"开始代码审查...\n\n" +
                          "审查结果将包括：\n" +
                          "• 代码质量评分\n" +
                          "• 发现的问题和建议\n" +
                          "• 代码规范检查\n" +
                          "• 安全性分析\n" +
                          "• 性能优化建议");
        
        JBScrollPane scrollPane = new JBScrollPane(resultArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void onAddFile(ActionEvent e) {
        // 显示文件选择对话框
        JPopupMenu popup = new JPopupMenu();
        
        JMenuItem addCurrentFileItem = new JMenuItem("添加当前文件");
        addCurrentFileItem.addActionListener(this::onAddCurrentFile);
        popup.add(addCurrentFileItem);
        
        JMenuItem addSelectedCodeItem = new JMenuItem("添加选中代码");
        addSelectedCodeItem.addActionListener(this::onAddSelectedCode);
        popup.add(addSelectedCodeItem);
        
        popup.addSeparator();
        
        JMenuItem addProjectFileItem = new JMenuItem("选择项目文件...");
        addProjectFileItem.addActionListener(this::onAddProjectFiles);
        popup.add(addProjectFileItem);
        
        // 显示在按钮下方
        popup.show(addFileButton, 0, addFileButton.getHeight());
    }
    
    private void onAddCurrentFile(ActionEvent e) {
        VirtualFile currentFile = getCurrentFile();
        if (currentFile == null) {
            resultArea.setText("❌ 没有打开的文件\n\n请先在编辑器中打开一个文件。");
            return;
        }
        
        if (fileListModel.size() > 0 && fileListModel.get(0).isPlaceholder()) {
            fileListModel.removeElementAt(0); // 移除提示项
        }
        
        String description = "完整文件: " + currentFile.getPath();
        ReviewFileItem item = new ReviewFileItem(currentFile.getName(), description, 0, 0, false);
        fileListModel.addElement(item);
        
        resultArea.setText("✅ 已添加文件: " + currentFile.getName());
    }
    
    private void onAddSelectedCode(ActionEvent e) {
        String selectedCode = getSelectedCodeFromEditor();
        if (selectedCode == null || selectedCode.trim().isEmpty()) {
            resultArea.setText("❌ 没有选中的代码\n\n请在编辑器中选中要评审的代码片段。");
            return;
        }
        
        VirtualFile currentFile = getCurrentFile();
        if (currentFile == null) {
            resultArea.setText("❌ 无法确定当前文件\n\n请确保在编辑器中打开了文件并选中了代码。");
            return;
        }
        
        // 获取选中代码的行数信息
        Editor editor = getCurrentEditor();
        int startLine = 0, endLine = 0;
        if (editor != null) {
            SelectionModel selectionModel = editor.getSelectionModel();
            startLine = editor.getDocument().getLineNumber(selectionModel.getSelectionStart()) + 1;
            endLine = editor.getDocument().getLineNumber(selectionModel.getSelectionEnd()) + 1;
        }
        
        if (fileListModel.size() > 0 && fileListModel.get(0).isPlaceholder()) {
            fileListModel.removeElementAt(0); // 移除提示项
        }
        
        String description = "选中代码片段: " + selectedCode.substring(0, Math.min(50, selectedCode.length())) + "...";
        ReviewFileItem item = new ReviewFileItem(currentFile.getName(), description, startLine, endLine, false);
        fileListModel.addElement(item);
        
                resultArea.setText("✅ 已添加选中代码: " + currentFile.getName() + " (行 " + startLine + "-" + endLine + ")");
    }
    
    private void onAddProjectFiles(ActionEvent e) {
        FileSearchDialog dialog = new FileSearchDialog(project);
        if (dialog.showAndGet()) {
            Collection<VirtualFile> selectedFiles = dialog.getSelectedFiles();
            
            if (!selectedFiles.isEmpty()) {
                if (fileListModel.size() > 0 && fileListModel.get(0).isPlaceholder()) {
                    fileListModel.removeElementAt(0); // 移除提示项
                }
                
                for (VirtualFile file : selectedFiles) {
                    String description = "项目文件: " + file.getPath();
                    ReviewFileItem item = new ReviewFileItem(file.getName(), description, 0, 0, false);
                    fileListModel.addElement(item);
                }
                
                resultArea.setText("✅ 已添加 " + selectedFiles.size() + " 个项目文件");
            }
        }
    }
    
    private void showContextMenu(MouseEvent e) {
        int index = fileList.locationToIndex(e.getPoint());
        if (index >= 0 && !fileListModel.get(index).isPlaceholder()) {
            fileList.setSelectedIndex(index);
            
            JPopupMenu contextMenu = new JPopupMenu();
            JMenuItem deleteItem = new JMenuItem("删除");
            deleteItem.addActionListener(actionEvent -> deleteSelectedFiles());
            contextMenu.add(deleteItem);
            
            contextMenu.show(fileList, e.getX(), e.getY());
        }
    }
    
    private void deleteSelectedFiles() {
        int[] selectedIndices = fileList.getSelectedIndices();
        if (selectedIndices.length == 0) {
            return;
        }
        
        // 从后往前删除，避免索引问题
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            int index = selectedIndices[i];
            if (index >= 0 && index < fileListModel.size() && !fileListModel.get(index).isPlaceholder()) {
                fileListModel.removeElementAt(index);
            }
        }
        
        // 如果删除后列表为空，重新添加提示项
        if (fileListModel.isEmpty()) {
            fileListModel.addElement(new ReviewFileItem("提示", "点击 + 按钮或右键菜单添加要评审的文件", 0, 0, true));
        }
        
        resultArea.setText("✅ 已删除 " + selectedIndices.length + " 个文件");
    }
    
    private void deleteFileItem(ReviewFileItem item) {
        fileListModel.removeElement(item);
        
        // 如果删除后列表为空，重新添加提示项
        if (fileListModel.isEmpty()) {
            fileListModel.addElement(new ReviewFileItem("提示", "点击 + 按钮或右键菜单添加要评审的文件", 0, 0, true));
        }
        
        resultArea.setText("✅ 已删除文件: " + item.getFileName());
    }

    private void onReviewFiles(ActionEvent e) {
        if (fileListModel.isEmpty() || (fileListModel.size() == 1 && fileListModel.get(0).isPlaceholder())) {
            resultArea.setText("❌ 没有要评审的文件\n\n请先添加文件或代码片段。");
            return;
        }
        
        resultArea.setText("🔄 正在进行代码评审，请稍候...\n\n分析中...");
        
        // 调用评审服务
        reviewService.reviewCurrentFile(result -> {
            SwingUtilities.invokeLater(() -> {
                resultArea.setText(result);
                resultArea.setCaretPosition(0);
            });
        });
    }
    
    private void onGetGitDiff(ActionEvent e) {
        resultArea.setText("🔄 正在获取Git变更，请稍候...");
        
        // 在后台线程中执行git diff命令
        SwingUtilities.invokeLater(() -> {
            try {
                String gitDiff = executeGitDiff();
                if (gitDiff != null && !gitDiff.trim().isEmpty()) {
                    changesArea.setText(gitDiff);
                    resultArea.setText("✅ 已获取Git变更信息");
                } else {
                    changesArea.setText("没有检测到代码变更。\n\n可能的原因：\n" +
                                      "• 当前没有未提交的更改\n" +
                                      "• 当前目录不是Git仓库\n" +
                                      "• 所有更改已经提交");
                    resultArea.setText("ℹ️ 没有发现Git变更");
                }
            } catch (Exception ex) {
                changesArea.setText("获取Git变更时出错：\n" + ex.getMessage() + "\n\n" +
                                  "请确保：\n" +
                                  "• 当前项目是Git仓库\n" +
                                  "• 有读取权限\n" +
                                  "• Git命令可用");
                resultArea.setText("❌ 获取Git变更失败");
                LOG.error("Failed to get git diff", ex);
            }
        });
    }
    
    private void onReviewChanges(ActionEvent e) {
        String changes = changesArea.getText().trim();
        if (changes.isEmpty() || changes.startsWith("点击") || changes.startsWith("没有检测到") || changes.startsWith("获取Git变更时出错")) {
            resultArea.setText("❌ 错误：没有可评审的变更\n\n请先点击\"获取Git变更\"按钮获取代码变更。");
            return;
        }
        
        resultArea.setText("🔄 正在评审代码变更，请稍候...\n\n分析变更内容中...");
        
        // 调用评审服务评审变更内容
        // 这里可以创建一个专门的变更评审方法
        SwingUtilities.invokeLater(() -> {
            String mockResult = getMockChangeReviewResult(changes);
            resultArea.setText(mockResult);
            resultArea.setCaretPosition(0);
        });
    }
    
    private String getSelectedCodeFromEditor() {
        // 获取当前活动的编辑器
        Editor editor = getCurrentEditor();
        if (editor == null) {
            return null;
        }
        
        SelectionModel selectionModel = editor.getSelectionModel();
        return selectionModel.getSelectedText();
    }
    
    private String getCurrentFileContent() {
        VirtualFile currentFile = getCurrentFile();
        if (currentFile == null) {
            return null;
        }
        
        try {
            return new String(currentFile.contentsToByteArray(), currentFile.getCharset());
        } catch (Exception e) {
            LOG.error("Failed to read file content", e);
            return null;
        }
    }
    
    private VirtualFile getCurrentFile() {
        VirtualFile[] openFiles = FileEditorManager.getInstance(project).getSelectedFiles();
        return openFiles.length > 0 ? openFiles[0] : null;
    }
    
    private Editor getCurrentEditor() {
        com.intellij.openapi.fileEditor.FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor();
        if (fileEditor instanceof TextEditor) {
            return ((TextEditor) fileEditor).getEditor();
        }
        return null;
    }
    
    private String executeGitDiff() throws Exception {
        String projectPath = project.getBasePath();
        if (projectPath == null) {
            throw new Exception("无法获取项目路径");
        }
        
        ProcessBuilder processBuilder = new ProcessBuilder("git", "diff");
        processBuilder.directory(Paths.get(projectPath).toFile());
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0 && output.length() == 0) {
            throw new Exception("Git命令执行失败，退出码: " + exitCode);
        }
        
        return output.toString();
    }
    
    private String getMockChangeReviewResult(String changes) {
        int lineCount = changes.split("\n").length;
        
        StringBuilder result = new StringBuilder();
        result.append("# Git变更代码审查结果\n\n");
        result.append("## 变更概览\n");
        result.append("- 变更行数: ").append(lineCount).append("\n");
        result.append("- 审查评分: 88/100\n");
        result.append("- 风险等级: 中等\n\n");
        
        result.append("## 变更分析\n");
        result.append("1. **代码质量**: 变更代码整体质量良好\n");
        result.append("2. **安全性**: 未发现明显安全隐患\n");
        result.append("3. **性能影响**: 对性能影响较小\n");
        result.append("4. **兼容性**: 变更与现有代码兼容性良好\n\n");
        
        result.append("## 建议\n");
        result.append("1. 建议增加单元测试覆盖新增的代码逻辑\n");
        result.append("2. 考虑添加必要的代码注释\n");
        result.append("3. 在提交前进行代码格式化\n");
        result.append("4. 确保所有依赖项正确更新\n\n");
        
        result.append("## 风险评估\n");
        result.append("- **低风险**: 代码格式和注释修改\n");
        result.append("- **中风险**: 业务逻辑变更\n");
        result.append("- **高风险**: 暂未发现\n");
        
        return result.toString();
    }
    
    public void dispose() {
        // 清理资源
        instance = null;
        LOG.info("CodeReviewPanel disposed");
    }
    
    /**
     * 添加文件到评审列表（供外部调用，如右键菜单）
     */
    public void addFileToReview(String fileName, String description, int startLine, int endLine) {
        if (fileListModel.size() > 0 && fileListModel.get(0).isPlaceholder()) {
            fileListModel.removeElementAt(0); // 移除提示项
        }
        
        ReviewFileItem item = new ReviewFileItem(fileName, description, startLine, endLine, false);
        fileListModel.addElement(item);
        
        // 更新结果区域
        resultArea.setText("✅ 已添加: " + fileName + 
                          (startLine > 0 ? " (行 " + startLine + "-" + endLine + ")" : ""));
    }
    
    /**
     * 文件项数据类
     */
    private static class ReviewFileItem {
        private final String fileName;
        private final String description;
        private final int startLine;
        private final int endLine;
        private final boolean isPlaceholder;
        
        public ReviewFileItem(String fileName, String description, int startLine, int endLine, boolean isPlaceholder) {
            this.fileName = fileName;
            this.description = description;
            this.startLine = startLine;
            this.endLine = endLine;
            this.isPlaceholder = isPlaceholder;
        }
        
        public String getFileName() { return fileName; }
        public String getDescription() { return description; }
        public int getStartLine() { return startLine; }
        public int getEndLine() { return endLine; }
        public boolean isPlaceholder() { return isPlaceholder; }
        
        @Override
        public String toString() {
            if (isPlaceholder) {
                return description;
            }
            if (startLine > 0) {
                return fileName + " (行 " + startLine + "-" + endLine + ")";
            }
            return fileName;
        }
    }
    
    /**
     * 文件项渲染器
     */
    private class ReviewFileItemRenderer implements ListCellRenderer<ReviewFileItem> {
        @Override
        public Component getListCellRendererComponent(JList<? extends ReviewFileItem> list, ReviewFileItem value, int index,
                                                    boolean isSelected, boolean cellHasFocus) {
            
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(true);
            panel.setBorder(JBUI.Borders.empty(5, 10));
            
            // 设置背景色
            if (isSelected) {
                panel.setBackground(list.getSelectionBackground());
            } else {
                panel.setBackground(list.getBackground());
            }
            
            // 文件名标签
            JLabel label = new JLabel();
            label.setText(value.toString());
            
            if (value.isPlaceholder()) {
                label.setForeground(JBColor.GRAY);
                label.setFont(label.getFont().deriveFont(Font.ITALIC));
                panel.add(label, BorderLayout.CENTER);
            } else {
                label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                label.setFont(label.getFont().deriveFont(Font.PLAIN));
                panel.add(label, BorderLayout.CENTER);
                
                // 添加删除按钮
                JLabel deleteButton = new JLabel("❎");
                deleteButton.setPreferredSize(new Dimension(25, 25));
                deleteButton.setOpaque(false);
                deleteButton.setFont(new Font("Dialog", Font.PLAIN, 14));
                deleteButton.setToolTipText("点击删除此文件");
                deleteButton.setHorizontalAlignment(SwingConstants.CENTER);
                deleteButton.setVerticalAlignment(SwingConstants.CENTER);
                deleteButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                
                panel.add(deleteButton, BorderLayout.EAST);
            }
            
            return panel;
        }
    }
    
    /**
     * 文件搜索对话框
     */
    private static class FileSearchDialog extends DialogWrapper {
        private final Project project;
        private SearchTextField searchField;
        private JList<VirtualFile> fileList;
        private DefaultListModel<VirtualFile> fileListModel;
        private Collection<VirtualFile> selectedFiles = new ArrayList<>();
        
        public FileSearchDialog(@Nullable Project project) {
            super(project, true);
            this.project = project;
            setTitle("选择项目文件");
            setSize(600, 500);
            init();
        }
        
        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setPreferredSize(new Dimension(600, 500));
            
            // 搜索框
            searchField = new SearchTextField(true);
            searchField.addDocumentListener(new DocumentAdapter() {
                @Override
                protected void textChanged(@NotNull DocumentEvent e) {
                    searchFiles(searchField.getText());
                }
            });
            
            JPanel searchPanel = new JPanel(new BorderLayout());
            searchPanel.setBorder(JBUI.Borders.empty(10));
            searchPanel.add(new JLabel("Enter file name:"), BorderLayout.NORTH);
            searchPanel.add(searchField, BorderLayout.CENTER);
            
            panel.add(searchPanel, BorderLayout.NORTH);
            
            // 文件列表
            fileListModel = new DefaultListModel<>();
            fileList = new JList<>(fileListModel);
            fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            fileList.setCellRenderer(new FileListCellRenderer());
            
            JScrollPane scrollPane = new JScrollPane(fileList);
            scrollPane.setPreferredSize(new Dimension(580, 400));
            panel.add(scrollPane, BorderLayout.CENTER);
            
            // 初始化显示所有文件
            searchFiles("");
            
            return panel;
        }
        
        private void searchFiles(String searchText) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    Collection<VirtualFile> files = new ArrayList<>();
                    
                    if (searchText.trim().isEmpty()) {
                        // 显示所有项目文件
                        VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
                        for (VirtualFile root : contentRoots) {
                            collectFiles(root, files, 500); // 限制最多500个文件
                            if (files.size() >= 500) break;
                        }
                    } else {
                        // 搜索匹配的文件
                        Collection<VirtualFile> foundFiles = FilenameIndex.getVirtualFilesByName(
                            project,
                            searchText, 
                            GlobalSearchScope.projectScope(project)
                        );
                        
                        // 同时支持模糊搜索
                        String lowerSearchText = searchText.toLowerCase();
                        VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRoots();
                        for (VirtualFile root : contentRoots) {
                            collectFilesWithNameContaining(root, files, lowerSearchText, 200);
                            if (files.size() >= 200) break;
                        }
                        
                        files.addAll(foundFiles);
                    }
                    
                    // 在EDT线程中更新UI
                    SwingUtilities.invokeLater(() -> {
                        fileListModel.clear();
                        for (VirtualFile file : files) {
                            if (fileListModel.size() >= 500) break; // 限制显示数量
                            fileListModel.addElement(file);
                        }
                    });
                } catch (Exception e) {
                    // 处理异常
                    SwingUtilities.invokeLater(() -> {
                        fileListModel.clear();
                        // 可以添加错误提示
                    });
                }
            });
        }
        
        private void collectFiles(VirtualFile directory, Collection<VirtualFile> files, int maxFiles) {
            if (files.size() >= maxFiles) return;
            
            if (directory.isDirectory()) {
                for (VirtualFile child : directory.getChildren()) {
                    if (files.size() >= maxFiles) break;
                    
                    if (child.isDirectory()) {
                        collectFiles(child, files, maxFiles);
                    } else {
                        // 只收集常见的代码文件
                        String extension = child.getExtension();
                        if (isCodeFile(extension)) {
                            files.add(child);
                        }
                    }
                }
            }
        }
        
        private void collectFilesWithNameContaining(VirtualFile directory, Collection<VirtualFile> files, 
                                                  String searchText, int maxFiles) {
            if (files.size() >= maxFiles) return;
            
            if (directory.isDirectory()) {
                for (VirtualFile child : directory.getChildren()) {
                    if (files.size() >= maxFiles) break;
                    
                    if (child.isDirectory()) {
                        collectFilesWithNameContaining(child, files, searchText, maxFiles);
                    } else {
                        // 检查文件名是否包含搜索文本
                        if (child.getName().toLowerCase().contains(searchText) && isCodeFile(child.getExtension())) {
                            files.add(child);
                        }
                    }
                }
            }
        }
        
        private boolean isCodeFile(String extension) {
            if (extension == null) return false;
            
            // 常见代码文件扩展名
            String[] codeExtensions = {
                "java", "kt", "scala", "groovy",
                "js", "ts", "jsx", "tsx", "vue",
                "py", "rb", "php", "go", "rs",
                "cpp", "c", "h", "hpp", "cc",
                "cs", "vb", "fs",
                "xml", "html", "css", "scss", "less",
                "json", "yml", "yaml", "properties",
                "sql", "md", "txt", "sh", "bat"
            };
            
            String lowerExt = extension.toLowerCase();
            return Arrays.asList(codeExtensions).contains(lowerExt);
        }
        
        @Override
        protected void doOKAction() {
            selectedFiles = new ArrayList<>();
            for (VirtualFile file : fileList.getSelectedValuesList()) {
                selectedFiles.add(file);
            }
            super.doOKAction();
        }
        
        public Collection<VirtualFile> getSelectedFiles() {
            return selectedFiles;
        }
        
        /**
         * 文件列表渲染器
         */
        private static class FileListCellRenderer extends ColoredListCellRenderer<VirtualFile> {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends VirtualFile> list, 
                                               VirtualFile value, int index, boolean selected, boolean hasFocus) {
                if (value != null) {
                    // 显示文件名
                    append(value.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                    
                    // 显示文件路径
                    String path = value.getPath();
                    if (path.length() > 60) {
                        path = "..." + path.substring(path.length() - 57);
                    }
                    append("  (" + path + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
                    
                    // 设置文件图标
                    FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(value);
                    setIcon(fileType.getIcon());
                }
            }
        }
    }
} 