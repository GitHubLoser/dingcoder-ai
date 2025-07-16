package com.codereview.plugin.ui;

import com.codereview.plugin.service.ReviewService;
import com.codereview.plugin.service.ReviewFeedbackService;
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
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellEditor;
import javax.swing.AbstractCellEditor;
import java.awt.*;
import javax.swing.ToolTipManager;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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
import java.util.List;
import java.util.ArrayList;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.codereview.plugin.service.MQTTService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

/**
 * 代码审查面板，包含评审文件、评审变更、评审结果三个区域
 */
public class CodeReviewPanel extends JBPanel<CodeReviewPanel> {
    private static final Logger LOG = Logger.getInstance(CodeReviewPanel.class);
    
    private final Project project;
    private final ReviewService reviewService;
    private final ReviewFeedbackService feedbackService;
    
    // UI组件
    private JList<ReviewFileItem> fileList;
    private DefaultListModel<ReviewFileItem> fileListModel;
    private JTextArea changesArea;
    private JTable resultTable;
    private DefaultTableModel resultTableModel;
    private JButton addFileButton;
    private JButton reviewFileButton;
    private JButton getGitDiffButton;
    private JButton reviewChangesButton;
    private JButton resetButton;
    private boolean mqttReceived = false; // 新增，是否收到MQTT消息
    
    // MQTT消息字段存储 - 用于反馈接口
    private java.util.Map<Integer, MqttMessageData> mqttDataMap = new java.util.HashMap<>();
    
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
        this.feedbackService = ReviewFeedbackService.getInstance();
        instance = this; // 设置静态引用
        
        initializeUI();
        
        // 设置代码审查MQTT回调
        setCodeReviewMqttCallback();
    }
    
    public static CodeReviewPanel getInstance() {
        return instance;
    }
    
    private void initializeUI() {
        setBackground(BACKGROUND_COLOR);
        setBorder(JBUI.Borders.empty(10));
        
        // 创建垂直分割面板 - 三个区域一列显示
        JSplitPane topSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        topSplitPane.setResizeWeight(0.25);
        topSplitPane.setDividerSize(0); // 去掉分割线
        topSplitPane.setBorder(null); // 去掉边框
        
        JSplitPane bottomSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        bottomSplitPane.setResizeWeight(0.0); // 变更区域固定大小，不随整体缩放
        bottomSplitPane.setDividerSize(0); // 去掉分割线
        bottomSplitPane.setBorder(null); // 去掉边框
        
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
        
        // 添加组件监听器，在面板大小变化时重新计算分割位置
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> adjustSplitPaneLocations(topSplitPane, bottomSplitPane));
            }
            
            @Override
            public void componentShown(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> adjustSplitPaneLocations(topSplitPane, bottomSplitPane));
            }
        });
        
        // 延迟设置初始分割位置，确保组件已完成布局
        SwingUtilities.invokeLater(() -> adjustSplitPaneLocations(topSplitPane, bottomSplitPane));
    }
    
    /**
     * 根据当前面板大小调整分割位置
     */
    private void adjustSplitPaneLocations(JSplitPane topSplitPane, JSplitPane bottomSplitPane) {
        int totalHeight = getHeight();
        if (totalHeight > 0) {
            // 文件区域：最小200px，最大400px，占总高度的25%
            int fileAreaHeight = Math.max(200, Math.min(400, (int)(totalHeight * 0.25)));
            
            // 变更区域：固定60px，确保按钮和边距完全显示
            int changesAreaHeight = 60;
            
            // 设置分割位置
            topSplitPane.setDividerLocation(fileAreaHeight);
            bottomSplitPane.setDividerLocation(changesAreaHeight);
            
            // 强制重新验证和重绘
            topSplitPane.revalidate();
            bottomSplitPane.revalidate();
        }
    }
    
    private JPanel createFilePanel() {
        JPanel panel = new JBPanel<>(new BorderLayout());
        panel.setBackground(PANEL_BACKGROUND);
        
        // 创建带标题的边框，并添加底部间距
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "评审文件"
        );
        border.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.setBorder(BorderFactory.createCompoundBorder(
            JBUI.Borders.empty(0, 0, 5, 0), // 底部5px间距
            border
        ));
        
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
        
        resetButton = new JButton("重置");
        resetButton.addActionListener(this::onReset);
        // 复制开始评审按钮的样式
        resetButton.setPreferredSize(reviewFileButton.getPreferredSize());
        resetButton.setFont(reviewFileButton.getFont());
        resetButton.setBackground(reviewFileButton.getBackground());
        resetButton.setForeground(reviewFileButton.getForeground());
        resetButton.setBorder(reviewFileButton.getBorder());
        resetButton.setFocusPainted(reviewFileButton.isFocusPainted());
        resetButton.setContentAreaFilled(reviewFileButton.isContentAreaFilled());
        resetButton.setOpaque(reviewFileButton.isOpaque());
        buttonPanel.add(resetButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createChangesPanel() {
        // 使用GridBagLayout来精确定位按钮，更稳定
        JPanel panel = new JBPanel<>(new GridBagLayout());
        panel.setBackground(PANEL_BACKGROUND);
        
        // 创建带标题的边框
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "评审变更"
        );
        border.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.setBorder(border);
        
        // 创建一个隐藏的文本区域用于存储git diff结果（不显示给用户）
        changesArea = new JTextArea();
        changesArea.setVisible(false);
        
        reviewChangesButton = new JButton("评审变更");
        reviewChangesButton.setPreferredSize(new Dimension(100, 30)); // 按钮大小不变
        reviewChangesButton.setFont(reviewFileButton.getFont());
        reviewChangesButton.setBackground(reviewFileButton.getBackground());
        reviewChangesButton.setForeground(reviewFileButton.getForeground());
        reviewChangesButton.setBorder(reviewFileButton.getBorder());
        reviewChangesButton.setFocusPainted(reviewFileButton.isFocusPainted());
        reviewChangesButton.setContentAreaFilled(reviewFileButton.isContentAreaFilled());
        reviewChangesButton.setOpaque(reviewFileButton.isOpaque());
        reviewChangesButton.addActionListener(e -> {
            LOG.info("正在获取Git变更并评审...");
            SwingUtilities.invokeLater(() -> {
                try {
                    String gitDiff = executeGitDiff();
                    if (gitDiff != null && !gitDiff.trim().isEmpty()) {
                        changesArea.setText(gitDiff);
                        onReviewChangesInner(gitDiff);
                    } else {
                        // 使用HTML格式化消息，确保换行符正确显示
                        JOptionPane.showMessageDialog(
                            this,
                            "<html><body>没有检测到Git变更，可能原因：<br>- 当前没有未提交的更改<br>- 当前目录不是Git仓库<br>- 所有更改已经提交</body></html>",
                            "提示",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                        this,
                        "获取Git变更失败：" + ex.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE
                    );
                    LOG.error("Failed to get git diff", ex);
                }
            });
        });

        // 使用GridBagConstraints将按钮完美居中
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(reviewChangesButton, gbc);
        
        return panel;
    }
    
    private JPanel createResultPanel() {
        JPanel panel = new JBPanel<>(new BorderLayout());
        panel.setBackground(PANEL_BACKGROUND);
        
        // 创建带标题的边框，并添加顶部间距
        TitledBorder border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            "评审结果"
        );
        border.setTitleFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        panel.setBorder(BorderFactory.createCompoundBorder(
            JBUI.Borders.empty(5, 0, 0, 0), // 顶部5px间距
            border
        ));
        
        // 创建表格数据模型 - 三列：文件路径、评审意见、评审反馈
        String[] columnNames = {"文件路径", "评审意见", "评审反馈"};
        resultTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // 表格只读
            }
        };
        
        // 创建表格
        resultTable = new JTable(resultTableModel) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 2; // 只有反馈按钮列可编辑
            }
            
            // 启用表格选择功能
            @Override
            public boolean isRowSelected(int row) {
                return super.isRowSelected(row);
            }
            
            @Override
            public boolean isColumnSelected(int column) {
                return super.isColumnSelected(column);
            }
            
            @Override
            public boolean isCellSelected(int row, int column) {
                return super.isCellSelected(row, column);
            }
        };
        // 支持单元格选择和多选
        resultTable.setCellSelectionEnabled(true);
        resultTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        resultTable.setGridColor(BORDER_COLOR);
        resultTable.setShowVerticalLines(true);
        resultTable.setShowHorizontalLines(true);
        
        // 启用表格的选择和复制功能
        resultTable.setFocusable(true);
        resultTable.setRequestFocusEnabled(true);
        
        // 添加键盘快捷键支持复制
        resultTable.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "copy");
        resultTable.getActionMap().put("copy", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copySelectedTableContent();
            }
        });
        
        // 设置默认行高
        resultTable.setRowHeight(80);
        
        // 设置列宽 - 确保反馈列完全可见
        TableColumn filePathColumn = resultTable.getColumnModel().getColumn(0);
        filePathColumn.setPreferredWidth(200);
        filePathColumn.setMinWidth(150);
        filePathColumn.setMaxWidth(300);
        
        TableColumn reviewColumn = resultTable.getColumnModel().getColumn(1);
        reviewColumn.setPreferredWidth(400);
        reviewColumn.setMinWidth(250);
        
        TableColumn feedbackColumn = resultTable.getColumnModel().getColumn(2);
        feedbackColumn.setPreferredWidth(100);
        feedbackColumn.setMinWidth(100);
        feedbackColumn.setMaxWidth(100);
        
        // 设置多行渲染器（只读但可选择）
        resultTable.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer());
        resultTable.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer());
        
        // 设置反馈按钮渲染器和编辑器
        feedbackColumn.setCellRenderer(new FeedbackButtonRenderer());
        feedbackColumn.setCellEditor(new FeedbackButtonEditor());
        
        // 配置ToolTipManager以改善按钮tooltip显示
        ToolTipManager.sharedInstance().setInitialDelay(0);
        ToolTipManager.sharedInstance().setDismissDelay(5000);
        ToolTipManager.sharedInstance().setReshowDelay(0);
        
        // 智能处理表格的鼠标事件，只在反馈按钮列阻止事件
        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = resultTable.rowAtPoint(e.getPoint());
                int col = resultTable.columnAtPoint(e.getPoint());
                
                // 只在反馈按钮列（第3列，索引为2）阻止事件
                if (row >= 0 && col == 2) {
                    e.consume();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                int row = resultTable.rowAtPoint(e.getPoint());
                int col = resultTable.columnAtPoint(e.getPoint());
                
                // 只在反馈按钮列阻止事件
                if (row >= 0 && col == 2) {
                    e.consume();
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = resultTable.rowAtPoint(e.getPoint());
                int col = resultTable.columnAtPoint(e.getPoint());
                
                // 只在反馈按钮列阻止事件
                if (row >= 0 && col == 2) {
                    e.consume();
                }
            }
        });
        
        // 添加表格的鼠标移动监听器，用于显示tooltip
        resultTable.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                // 获取鼠标位置对应的单元格
                int row = resultTable.rowAtPoint(e.getPoint());
                int col = resultTable.columnAtPoint(e.getPoint());
                
                if (row >= 0 && col == 2) { // 只在反馈列显示tooltip
                    // 获取按钮并显示tooltip
                    Component component = resultTable.getCellRenderer(row, col)
                        .getTableCellRendererComponent(resultTable, resultTable.getValueAt(row, col), false, false, row, col);
                    
                    if (component instanceof JPanel) {
                        JPanel panel = (JPanel) component;
                        // 查找按钮并显示tooltip
                        for (Component comp : panel.getComponents()) {
                            if (comp instanceof JPanel) {
                                JPanel buttonPanel = (JPanel) comp;
                                for (Component btn : buttonPanel.getComponents()) {
                                    if (btn instanceof JButton) {
                                        JButton button = (JButton) btn;
                                        if (button.getToolTipText() != null) {
                                            // 显示tooltip
                                            ToolTipManager.sharedInstance().mouseMoved(e);
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(resultTable);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
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
            // 只在状态栏显示错误，不显示在评审结果区域
            LOG.warn("没有打开的文件");
            return;
        }
        
        if (fileListModel.size() > 0 && fileListModel.get(0).isPlaceholder()) {
            fileListModel.removeElementAt(0); // 移除提示项
        }
        
        String relativePath = getRelativeFilePath(currentFile);
        String description = "完整文件: " + relativePath;
        ReviewFileItem item = new ReviewFileItem(currentFile.getName(), relativePath, 0, 0, false);
        fileListModel.addElement(item);
        
        // 不显示任何消息，保持评审结果区域空白
        LOG.info("已添加文件: " + currentFile.getName());
    }
    
    private void onAddSelectedCode(ActionEvent e) {
        String selectedCode = getSelectedCodeFromEditor();
        if (selectedCode == null || selectedCode.trim().isEmpty()) {
            // 只在状态栏显示错误，不显示在评审结果区域
            LOG.warn("没有选中的代码");
            return;
        }
        
        VirtualFile currentFile = getCurrentFile();
        if (currentFile == null) {
            // 只在状态栏显示错误，不显示在评审结果区域
            LOG.warn("无法确定当前文件");
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
        
        String relativePath = getRelativeFilePath(currentFile);
        String description = "选中代码片段: " + selectedCode.substring(0, Math.min(50, selectedCode.length())) + "...";
        ReviewFileItem item = new ReviewFileItem(currentFile.getName(), relativePath, startLine, endLine, false);
        fileListModel.addElement(item);
        
        // 不显示任何消息，保持评审结果区域空白
        LOG.info("已添加选中代码: " + currentFile.getName() + " (行 " + startLine + "-" + endLine + ")");
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
                    String relativePath = getRelativeFilePath(file);
                    String description = "项目文件: " + relativePath;
                    ReviewFileItem item = new ReviewFileItem(file.getName(), relativePath, 0, 0, false);
                    fileListModel.addElement(item);
                }
                
                // 不显示任何消息，保持评审结果区域空白
                LOG.info("已添加 " + selectedFiles.size() + " 个项目文件");
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
        
        // 不显示任何消息，保持评审结果区域空白
        LOG.info("已删除 " + selectedIndices.length + " 个文件");
    }
    
    private void deleteFileItem(ReviewFileItem item) {
        fileListModel.removeElement(item);
        
        // 如果删除后列表为空，重新添加提示项
        if (fileListModel.isEmpty()) {
            fileListModel.addElement(new ReviewFileItem("提示", "点击 + 按钮或右键菜单添加要评审的文件", 0, 0, true));
        }
        
        // 不显示任何消息，保持评审结果区域空白
        LOG.info("已删除文件: " + item.getFileName());
    }

    private void onReviewFiles(ActionEvent e) {
        if (fileListModel.isEmpty() || (fileListModel.size() == 1 && fileListModel.get(0).isPlaceholder())) {
            // 只在状态栏显示错误，不显示在评审结果区域
            LOG.warn("没有要评审的文件");
            return;
        }
        // 禁用按钮并提示评审中
        reviewFileButton.setEnabled(false);
        reviewFileButton.setText("评审中...");
        
        // 不显示任何消息，保持评审结果区域空白
        LOG.info("正在进行代码评审，准备文件...");
        
        // 构建ReviewService.ReviewFileItem列表
        List<ReviewService.ReviewFileItem> reviewItems = new ArrayList<>();
        
        try {
            for (int i = 0; i < fileListModel.size(); i++) {
                ReviewFileItem uiItem = fileListModel.get(i);
                if (uiItem.isPlaceholder()) {
                    continue;
                }
                
                // 获取文件内容
                String content = getFileContentForReview(uiItem);
                if (content == null) {
                    LOG.warn("无法获取文件内容: " + uiItem.getFileName());
                    continue;
                }
                
                // 创建ReviewService的ReviewFileItem
                if (uiItem.getStartLine() > 0 && uiItem.getEndLine() > 0) {
                    // 代码片段
                    reviewItems.add(new ReviewService.ReviewFileItem(
                        uiItem.getFileName(),
                        uiItem.getDescription(), // 这里作为filePath使用
                        content,
                        uiItem.getStartLine(),
                        uiItem.getEndLine()
                    ));
                } else {
                    // 完整文件
                    reviewItems.add(new ReviewService.ReviewFileItem(
                        uiItem.getFileName(),
                        uiItem.getDescription(), // 这里作为filePath使用
                        content
                    ));
                }
            }
            
            if (reviewItems.isEmpty()) {
                // 只在状态栏显示错误，不显示在评审结果区域
                LOG.warn("没有有效的文件可以评审");
                return;
            }
            
            LOG.info("准备评审 " + reviewItems.size() + " 个文件/代码片段");
            // 不显示任何消息，保持评审结果区域空白
            
            // 调用新的评审服务
            reviewService.reviewFiles(reviewItems, null);
            
        } catch (Exception ex) {
            LOG.error("准备评审文件时出错", ex);
            // 只在日志中记录错误，不显示在评审结果区域
            LOG.error("准备评审文件时出错：" + ex.getMessage());
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
     * 获取用于评审的文件内容
     */
    private String getFileContentForReview(ReviewFileItem uiItem) {
        try {
            // 先尝试通过文件路径查找文件
            VirtualFile file = findFileByPath(uiItem.getDescription()); // description现在存储的是filePath
            
            // 如果路径查找失败，则通过文件名查找
            if (file == null) {
                file = findFileByName(uiItem.getFileName());
            }
            
            if (file == null) {
                LOG.warn("找不到文件: " + uiItem.getFileName() + ", 路径: " + uiItem.getDescription());
                return null;
            }
            
            String fullContent = new String(file.contentsToByteArray(), file.getCharset());
            
            // 如果是代码片段，提取对应行的内容
            if (uiItem.getStartLine() > 0 && uiItem.getEndLine() > 0) {
                String[] lines = fullContent.split("\n");
                StringBuilder selectedContent = new StringBuilder();
                
                int start = Math.max(0, uiItem.getStartLine() - 1); // 转换为0基索引
                int end = Math.min(lines.length, uiItem.getEndLine());
                
                for (int i = start; i < end; i++) {
                    selectedContent.append(lines[i]).append("\n");
                }
                
                return selectedContent.toString();
            } else {
                return fullContent;
            }
            
        } catch (Exception e) {
            LOG.error("读取文件内容失败: " + uiItem.getFileName(), e);
            return null;
        }
    }
    
    /**
     * 通过相对路径查找文件
     */
    private VirtualFile findFileByPath(String relativePath) {
        String basePath = project.getBasePath();
        if (basePath == null || relativePath == null) {
            return null;
        }
        
        String fullPath = basePath + "/" + relativePath;
        return LocalFileSystem.getInstance().findFileByPath(fullPath);
    }
    
    /**
     * 在项目中查找文件
     */
    private VirtualFile findFileByName(String fileName) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return null;
        }
        
        // 首先尝试在当前打开的文件中查找
        VirtualFile[] openFiles = FileEditorManager.getInstance(project).getOpenFiles();
        for (VirtualFile file : openFiles) {
            if (fileName.equals(file.getName())) {
                return file;
            }
        }
        
        // 如果没找到，在整个项目中搜索
        VirtualFile projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (projectRoot != null) {
            return findFileRecursively(projectRoot, fileName);
        }
        
        return null;
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
    
        private void onReviewChangesInner(String changes) {
        if (changes.isEmpty() || changes.startsWith("点击") || changes.startsWith("没有检测到") || changes.startsWith("获取Git变更时出错")) {
            // 只在日志中记录错误，不显示在评审结果区域
            LOG.warn("没有可评审的变更");
            return;
        }
        // 不显示任何消息，保持评审结果区域空白
        LOG.info("正在评审代码变更，准备变更内容...");
        try {
            // 调用评审变更服务
            reviewService.reviewChanges(changes, null);
        } catch (Exception ex) {
            LOG.error("评审变更时出错", ex);
            // 只在日志中记录错误，不显示在评审结果区域
            LOG.error("评审变更时出错：" + ex.getMessage());
        }
    }
    
    /**
     * 统计变更的文件数量
     */
    private int countChangedFiles(String gitDiff) {
        if (gitDiff == null || gitDiff.trim().isEmpty()) {
            return 0;
        }
        
        String[] lines = gitDiff.split("\n");
        int fileCount = 0;
        
        for (String line : lines) {
            if (line.startsWith("diff --git")) {
                fileCount++;
            }
        }
        
        return Math.max(1, fileCount); // 至少为1，即使没有标准的diff格式
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
    
    /**
     * 清空面板内容（退出登录时调用）
     */
    public void clearPanelContent() {
        try {
            LOG.info("开始清空代码审查面板内容");
            
            // 清空文件列表
            if (fileListModel != null) {
                fileListModel.clear();
                // 重新添加提示信息
                fileListModel.addElement(new ReviewFileItem("提示", "点击 + 按钮或右键菜单添加要评审的文件", 0, 0, true));
            }
            
            // 清空审查结果表格
            if (resultTableModel != null) {
                resultTableModel.setRowCount(0);
            }
            
            // 清空变更区域的隐藏内容
            if (changesArea != null) {
                changesArea.setText("");
            }
            
            // 清空MQTT消息数据
            if (mqttDataMap != null) {
                mqttDataMap.clear();
            }
            
            // 重置MQTT接收状态
            mqttReceived = false;
            
            // 刷新UI
            if (fileList != null) {
                fileList.revalidate();
                fileList.repaint();
            }
            if (resultTable != null) {
                resultTable.revalidate();
                resultTable.repaint();
            }
            
            LOG.info("代码审查面板内容清空完成");
        } catch (Exception e) {
            LOG.error("清空代码审查面板内容时出错", e);
        }
    }

    
    public void dispose() {
        try {
            // 清理资源
            instance = null;
            
            // 清理MQTT回调
            try {
                MQTTService mqttService = MQTTService.getInstance();
                if (mqttService != null) {
                    mqttService.setMessageCallback(MQTTService.FUNCTION_CODE_REVIEW, null);
                }
            } catch (Exception e) {
                LOG.error("清理MQTT回调时出错", e);
            }
            
            // 清理UI组件
            if (fileListModel != null) {
                fileListModel.clear();
            }
            if (resultTableModel != null) {
                resultTableModel.setRowCount(0);
            }
            
            LOG.info("CodeReviewPanel disposed");
        } catch (Exception e) {
            LOG.error("CodeReviewPanel dispose时出错", e);
        }
    }
    
    /**
     * 设置代码审查MQTT回调
     */
    private void setCodeReviewMqttCallback() {
        try {
            MQTTService mqttService = MQTTService.getInstance();
            if (mqttService != null) {
                LOG.info("开始设置代码审查MQTT回调函数");
                mqttService.setMessageCallback(MQTTService.FUNCTION_CODE_REVIEW, this::onCodeReviewMqttMessage);
                LOG.info("代码审查MQTT回调函数设置完成");
            } else {
                LOG.error("MQTT服务实例为空，无法设置代码审查回调");
            }
        } catch (Exception e) {
            LOG.error("设置代码审查MQTT回调时出错", e);
        }
    }
    
    /**
     * 处理代码审查MQTT消息
     */
    private void onCodeReviewMqttMessage(String message) {
        LOG.info("收到代码审查MQTT消息: " + message);
        mqttReceived = true; // 收到MQTT消息，允许反馈按钮可用
        SwingUtilities.invokeLater(() -> {
            try {
                // 将消息追加到评审结果区域
                parseAndAppendResult(message);
                LOG.info("代码审查消息已追加显示在结果区域");
                updateFeedbackButtonsState(); // 更新按钮状态
                // 检查是否审查结束
                if (message != null && message.contains("审查结束")) {
                    reviewFileButton.setEnabled(true);
                    reviewFileButton.setText("开始评审");
                    JOptionPane.showMessageDialog(this, "本次审查结束", "提示", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception e) {
                LOG.error("处理代码审查MQTT消息时出错", e);
            }
        });
    }
    
    /**
     * 更新反馈按钮状态
     */
    private void updateFeedbackButtonsState() {
        if (resultTable != null) {
            resultTable.repaint(); // 重新绘制表格以更新按钮状态
        }
    }
    
    /**
     * 添加文件到评审列表（供外部调用，如右键菜单）
     */
    public void addFileToReview(String fileName, String filePath, int startLine, int endLine) {
        if (fileListModel.size() > 0 && fileListModel.get(0).isPlaceholder()) {
            fileListModel.removeElementAt(0); // 移除提示项
        }
        ReviewFileItem item = new ReviewFileItem(fileName, filePath, startLine, endLine, false);
        fileListModel.addElement(item);
        // 不再自动在评审结果区域显示内容
        // showMessage("✅ 已添加: " + fileName + (startLine > 0 ? " (行 " + startLine + "-" + endLine + ")" : ""));
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
    
    /**
     * 显示消息到表格的第一行
     */
    private void showMessage(String message) {
        // 清空表格并显示消息
        resultTableModel.setRowCount(0);
        resultTableModel.addRow(new Object[]{
            "系统消息",
            message,
            ""
        });
    }
    
    /**
     * 清空评审结果区域，不显示任何内容
     */
    private void clearResultArea() {
        if (resultTable.isEditing()) {
            resultTable.getCellEditor().stopCellEditing();
        }
        resultTableModel.setRowCount(0);
        resultTable.clearSelection();
        resultTable.revalidate();
        resultTable.repaint();
    }
    
    /**
     * 解析API返回结果并显示到表格中（替换模式）
     */
    private void parseAndDisplayResult(String result) {
        try {
            // 清空现有数据
            resultTableModel.setRowCount(0);
            
            // 尝试解析JSON格式的结果
            if (result.trim().startsWith("{") || result.trim().startsWith("[")) {
                parseJsonResult(result, false);
            } else if (result.contains("code_file_desc") && result.contains("code_rvw_rs_desc")) {
                // 尝试解析包含指定字段的文本格式
                parseTextResult(result, false);
            } else {
                // 如果不是预期格式，显示原始结果
                resultTableModel.addRow(new Object[]{
                    "评审结果",
                    result,
                    new FeedbackButtons()
                });
            }
            
            // 如果没有解析到任何结果，显示原始内容
            if (resultTableModel.getRowCount() == 0) {
                resultTableModel.addRow(new Object[]{
                    "评审结果",
                    result,
                    new FeedbackButtons()
                });
            }
            
            adjustRowHeights();
            
        } catch (Exception e) {
            LOG.error("解析评审结果失败", e);
            resultTableModel.setRowCount(0);
            resultTableModel.addRow(new Object[]{
                "解析错误",
                "解析评审结果时出错: " + e.getMessage() + "\n\n原始结果:\n" + result,
                new FeedbackButtons()
            });
        }
    }
    
    /**
     * 解析API返回结果并追加到表格中（追加模式）
     */
    private void parseAndAppendResult(String result) {
        try {
            // 如果表格中有初始提示行，先清除
            if (resultTableModel.getRowCount() > 0) {
                Object firstRowValue = resultTableModel.getValueAt(0, 0);
                if ("暂无评审结果".equals(firstRowValue) || "系统消息".equals(firstRowValue)) {
                    resultTableModel.setRowCount(0);
                }
            }
            
            // 尝试解析JSON格式的结果
            if (result.trim().startsWith("{") || result.trim().startsWith("[")) {
                parseJsonResult(result, true);
            } else if (result.contains("code_file_desc") && result.contains("code_rvw_rs_desc")) {
                // 尝试解析包含指定字段的文本格式
                parseTextResult(result, true);
            } else {
                // 如果不是预期格式，追加原始结果
                resultTableModel.addRow(new Object[]{
                    "MQTT消息",
                    result,
                    new FeedbackButtons()
                });
            }
            
            adjustRowHeights();
            
        } catch (Exception e) {
            LOG.error("解析MQTT评审结果失败", e);
            resultTableModel.addRow(new Object[]{
                "解析错误",
                "解析MQTT评审结果时出错: " + e.getMessage() + "\n\n原始结果:\n" + result,
                new FeedbackButtons()
            });
        }
    }
    
    /**
     * 解析JSON格式的结果
     */
    private void parseJsonResult(String result, boolean isAppendMode) {
        try {
            JsonElement jsonElement = new JsonParser().parse(result);
            
            if (jsonElement.isJsonObject()) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                
                // 检查是否有data数组
                if (jsonObject.has("data") && jsonObject.get("data").isJsonArray()) {
                    JsonArray dataArray = jsonObject.getAsJsonArray("data");
                    parseJsonArray(dataArray, isAppendMode);
                } else {
                    // 单个对象
                    parseJsonObject(jsonObject, isAppendMode);
                }
                
            } else if (jsonElement.isJsonArray()) {
                JsonArray jsonArray = jsonElement.getAsJsonArray();
                parseJsonArray(jsonArray, isAppendMode);
            }
            
        } catch (Exception e) {
            LOG.warn("JSON解析失败，尝试文本解析", e);
            parseTextResult(result, isAppendMode);
        }
    }
    
    /**
     * 解析JSON数组
     */
    private void parseJsonArray(JsonArray jsonArray, boolean isAppendMode) {
        for (JsonElement element : jsonArray) {
            if (element.isJsonObject()) {
                parseJsonObject(element.getAsJsonObject(), isAppendMode);
            }
        }
    }
    
    /**
     * 解析JSON对象
     */
    private void parseJsonObject(JsonObject jsonObject, boolean isAppendMode) {
        String filePath = "";
        String reviewResult = "";
        
        // 创建MQTT消息数据对象
        MqttMessageData mqttData = new MqttMessageData();
        
        // 提取反馈接口需要的字段
        if (jsonObject.has("code_submt_recd_no")) {
            mqttData.codeSubmtRecdNo = jsonObject.get("code_submt_recd_no").getAsString();
        }
        if (jsonObject.has("code_file_no")) {
            mqttData.codeFileNo = jsonObject.get("code_file_no").getAsString();
        }
        if (jsonObject.has("code_slice_no")) {
            mqttData.codeSliceNo = jsonObject.get("code_slice_no").getAsString();
        }
        if (jsonObject.has("seq")) {
            mqttData.seq = jsonObject.get("seq").getAsInt();
        }
        
        // 提取code_file_desc字段
        if (jsonObject.has("code_file_desc")) {
            filePath = jsonObject.get("code_file_desc").getAsString();
        } else if (jsonObject.has("file_path") || jsonObject.has("filePath")) {
            filePath = jsonObject.has("file_path") ? 
                jsonObject.get("file_path").getAsString() : 
                jsonObject.get("filePath").getAsString();
        }
        
        // 提取code_rvw_rs_desc字段
        if (jsonObject.has("code_rvw_rs_desc")) {
            reviewResult = jsonObject.get("code_rvw_rs_desc").getAsString();
        } else if (jsonObject.has("review_result") || jsonObject.has("reviewResult")) {
            reviewResult = jsonObject.has("review_result") ? 
                jsonObject.get("review_result").getAsString() : 
                jsonObject.get("reviewResult").getAsString();
        } else if (jsonObject.has("message") || jsonObject.has("content")) {
            reviewResult = jsonObject.has("message") ? 
                jsonObject.get("message").getAsString() : 
                jsonObject.get("content").getAsString();
        }
        
        // 如果都有值，添加到表格
        if (!filePath.isEmpty() && !reviewResult.isEmpty()) {
            int rowIndex = resultTableModel.getRowCount();
            resultTableModel.addRow(new Object[]{
                filePath,
                reviewResult,
                new FeedbackButtons()
            });
            
            // 存储MQTT数据，与表格行索引关联
            if (mqttData.hasValidData()) {
                mqttDataMap.put(rowIndex, mqttData);
                LOG.info("存储MQTT数据到行 " + rowIndex + ": " + mqttData);
            }
        } else if (!reviewResult.isEmpty()) {
            // 只有评审结果，使用默认文件路径
            String defaultPath = isAppendMode ? "MQTT消息" : "评审结果";
            int rowIndex = resultTableModel.getRowCount();
            resultTableModel.addRow(new Object[]{
                defaultPath,
                reviewResult,
                new FeedbackButtons()
            });
            
            // 即使是默认路径，也存储MQTT数据
            if (mqttData.hasValidData()) {
                mqttDataMap.put(rowIndex, mqttData);
                LOG.info("存储MQTT数据到行 " + rowIndex + ": " + mqttData);
            }
        }
        
        adjustRowHeights();
    }
    
    /**
     * 解析文本格式的结果
     */
    private void parseTextResult(String result, boolean isAppendMode) {
        String[] lines = result.split("\n");
        String currentFilePath = "";
        String currentReview = "";
        
        for (String line : lines) {
            if (line.contains("code_file_desc")) {
                // 提取文件路径
                int start = line.indexOf("\"code_file_desc\":");
                if (start != -1) {
                    String temp = line.substring(start + 17);
                    int endQuote = temp.indexOf("\"", 1);
                    if (endQuote != -1) {
                        currentFilePath = temp.substring(1, endQuote + 1);
                    }
                }
            } else if (line.contains("code_rvw_rs_desc")) {
                // 提取评审意见
                int start = line.indexOf("\"code_rvw_rs_desc\":");
                if (start != -1) {
                    String temp = line.substring(start + 19);
                    int endQuote = temp.indexOf("\"", 1);
                    if (endQuote != -1) {
                        currentReview = temp.substring(1, endQuote + 1);
                    }
                }
                
                // 如果都有值，添加到表格
                if (!currentFilePath.isEmpty() && !currentReview.isEmpty()) {
                    resultTableModel.addRow(new Object[]{
                        currentFilePath,
                        currentReview,
                        new FeedbackButtons()
                    });
                    currentFilePath = "";
                    currentReview = "";
                }
            }
        }
        
        adjustRowHeights();
    }
    
    /**
     * MQTT消息数据类
     */
    private static class MqttMessageData {
        String codeSubmtRecdNo;
        String codeFileNo;
        String codeSliceNo;
        int seq;

        boolean hasValidData() {
            return codeSubmtRecdNo != null && !codeSubmtRecdNo.isEmpty() &&
                   codeFileNo != null && !codeFileNo.isEmpty() &&
                   codeSliceNo != null && !codeSliceNo.isEmpty();
        }

        @Override
        public String toString() {
            return String.format("MqttMessageData{codeSubmtRecdNo='%s', codeFileNo='%s', codeSliceNo='%s', seq=%d}",
                    codeSubmtRecdNo, codeFileNo, codeSliceNo, seq);
        }
    }

    /**
     * 反馈按钮容器类
     */
    private static class FeedbackButtons {
        @Override
        public String toString() {
            return "按钮";
        }
    }
    
    /**
     * 反馈按钮渲染器
     */
    private class FeedbackButtonRenderer implements TableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(true);
            panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            
            // 创建按钮容器面板，使用GridBagLayout实现完美居中
            JPanel buttonPanel = new JPanel(new GridBagLayout());
            buttonPanel.setOpaque(false);
            
            // 已确认按钮 - 绿色主题
            JButton confirmButton = new JButton("✓");
            confirmButton.setPreferredSize(new Dimension(28, 24));
            confirmButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            confirmButton.setFocusable(false);
            confirmButton.setMargin(new Insets(0,0,0,0));
            confirmButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            confirmButton.setContentAreaFilled(true);
            confirmButton.setBackground(new Color(76, 175, 80)); // Material Design Green
            confirmButton.setEnabled(mqttReceived);
            confirmButton.setForeground(Color.WHITE);
            confirmButton.setOpaque(true);
            confirmButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            confirmButton.setToolTipText("确认评审意见");
            
            // 悬停效果
            confirmButton.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    if (mqttReceived) {
                        confirmButton.setBackground(new Color(67, 160, 71));
                    }
                }
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    if (mqttReceived) {
                        confirmButton.setBackground(new Color(76, 175, 80));
                    }
                }
            });

            // 误报按钮 - 橙色主题
            JButton falsePositiveButton = new JButton("✗");
            falsePositiveButton.setPreferredSize(new Dimension(28, 24));
            falsePositiveButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            falsePositiveButton.setFocusable(false);
            falsePositiveButton.setMargin(new Insets(0,0,0,0));
            falsePositiveButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            falsePositiveButton.setContentAreaFilled(true);
            falsePositiveButton.setBackground(new Color(255, 152, 0)); // Material Design Orange
            falsePositiveButton.setEnabled(true); // 误报按钮始终启用
            falsePositiveButton.setForeground(Color.WHITE);
            falsePositiveButton.setOpaque(true);
            falsePositiveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            falsePositiveButton.setToolTipText("标记为误报");
            
            // 悬停效果
            falsePositiveButton.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    // 误报按钮始终响应悬停效果
                    falsePositiveButton.setBackground(new Color(245, 124, 0));
                }
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    // 误报按钮始终响应悬停效果
                    falsePositiveButton.setBackground(new Color(255, 152, 0));
                }
            });
            
            // 只有确认按钮在MQTT消息接收前禁用，误报按钮始终可用
            if (!mqttReceived) {
                confirmButton.setBackground(new Color(189, 189, 189));
                confirmButton.setForeground(new Color(117, 117, 117));
                // 误报按钮保持启用状态的样式
                falsePositiveButton.setBackground(new Color(255, 152, 0));
                falsePositiveButton.setForeground(Color.WHITE);
            }
            
            // 使用GridBagConstraints将按钮添加到容器中，实现完美居中
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = new Insets(0, 3, 0, 3); // 左右间距
            buttonPanel.add(confirmButton, gbc);
            
            gbc.gridx = 1;
            gbc.insets = new Insets(0, 3, 0, 3); // 左右间距
            buttonPanel.add(falsePositiveButton, gbc);
            
            panel.add(buttonPanel, BorderLayout.CENTER);
            return panel;
        }
    }
    
    /**
     * 反馈按钮编辑器
     */
    private class FeedbackButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private JPanel panel;
        private JButton confirmButton;
        private JButton falsePositiveButton;
        private int editingRow;
        
        public FeedbackButtonEditor() {
            panel = new JPanel(new BorderLayout());
            
            // 创建按钮容器面板，使用GridBagLayout实现完美居中
            JPanel buttonPanel = new JPanel(new GridBagLayout());
            buttonPanel.setOpaque(false);
            
            // 已确认按钮 - 绿色主题
            confirmButton = new JButton("✓");
            confirmButton.setPreferredSize(new Dimension(28, 24));
            confirmButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            confirmButton.setFocusable(false);
            confirmButton.setMargin(new Insets(0,0,0,0));
            confirmButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            confirmButton.setContentAreaFilled(true);
            confirmButton.setBackground(new Color(76, 175, 80)); // Material Design Green
            confirmButton.setEnabled(mqttReceived);
            confirmButton.setForeground(Color.WHITE);
            confirmButton.setOpaque(true);
            confirmButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            confirmButton.setToolTipText("确认评审意见");
            
            // 悬停效果和强制tooltip显示
            confirmButton.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    if (mqttReceived) {
                        confirmButton.setBackground(new Color(67, 160, 71));
                    }
                    // 强制显示tooltip
                    ToolTipManager.sharedInstance().setInitialDelay(0);
                    ToolTipManager.sharedInstance().setDismissDelay(5000);
                    ToolTipManager.sharedInstance().mouseMoved(e);
                }
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    if (mqttReceived) {
                        confirmButton.setBackground(new Color(76, 175, 80));
                    }
                }
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    // 确保tooltip保持显示
                    ToolTipManager.sharedInstance().mouseMoved(e);
                }
            });

            // 误报按钮 - 橙色主题
            falsePositiveButton = new JButton("✗");
            falsePositiveButton.setPreferredSize(new Dimension(28, 24));
            falsePositiveButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            falsePositiveButton.setFocusable(false);
            falsePositiveButton.setMargin(new Insets(0,0,0,0));
            falsePositiveButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            falsePositiveButton.setContentAreaFilled(true);
            falsePositiveButton.setBackground(new Color(255, 152, 0)); // Material Design Orange
            falsePositiveButton.setEnabled(true); // 误报按钮始终启用
            falsePositiveButton.setForeground(Color.WHITE);
            falsePositiveButton.setOpaque(true);
            falsePositiveButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            falsePositiveButton.setToolTipText("标记为误报");
            
            // 悬停效果和强制tooltip显示
            falsePositiveButton.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    // 误报按钮始终响应悬停效果
                    falsePositiveButton.setBackground(new Color(245, 124, 0));
                    // 强制显示tooltip
                    ToolTipManager.sharedInstance().setInitialDelay(0);
                    ToolTipManager.sharedInstance().setDismissDelay(5000);
                    ToolTipManager.sharedInstance().mouseMoved(e);
                }
                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    // 误报按钮始终响应悬停效果
                    falsePositiveButton.setBackground(new Color(255, 152, 0));
                }
                @Override
                public void mouseMoved(java.awt.event.MouseEvent e) {
                    // 确保tooltip保持显示
                    ToolTipManager.sharedInstance().mouseMoved(e);
                }
            });
            
            // 只有确认按钮在MQTT消息接收前禁用，误报按钮始终可用
            if (!mqttReceived) {
                confirmButton.setBackground(new Color(189, 189, 189));
                confirmButton.setForeground(new Color(117, 117, 117));
                // 误报按钮保持启用状态的样式
                falsePositiveButton.setBackground(new Color(255, 152, 0));
                falsePositiveButton.setForeground(Color.WHITE);
            }
            
            confirmButton.addActionListener(e -> {
                onFeedbackClick("confirmed", editingRow);
                fireEditingStopped();
            });
            falsePositiveButton.addActionListener(e -> {
                LOG.info("❎按钮被点击，行号: " + editingRow);
                showFalsePositiveDialog(editingRow);
                fireEditingStopped();
            });
            
            // 使用GridBagConstraints将按钮添加到容器中，实现完美居中
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.insets = new Insets(0, 3, 0, 3); // 左右间距
            buttonPanel.add(confirmButton, gbc);
            
            gbc.gridx = 1;
            gbc.insets = new Insets(0, 3, 0, 3); // 左右间距
            buttonPanel.add(falsePositiveButton, gbc);
            
            panel.add(buttonPanel, BorderLayout.CENTER);
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                                                     int row, int column) {
            editingRow = row;
            return panel;
        }
        
        @Override
        public Object getCellEditorValue() {
            return new FeedbackButtons();
        }
        
        private void onFeedbackClick(String feedback, int row) {
            // 获取当前行的文件路径和评审意见
            String filePath = (String) resultTableModel.getValueAt(row, 0);
            String review = (String) resultTableModel.getValueAt(row, 1);
            
            LOG.info("用户反馈: " + feedback + ", 文件: " + filePath + ", 行号: " + row);
            
            // 获取MQTT消息数据
            MqttMessageData mqttData = mqttDataMap.get(row);
            if (mqttData == null || !mqttData.hasValidData()) {
                LOG.warn("行 " + row + " 没有有效的MQTT数据，无法提交反馈");
                return;
            }
            
            if ("confirmed".equals(feedback)) {
                // 已确认 - 直接调用反馈接口
                submitFeedback(mqttData, "2", "", row);
                LOG.info("已确认评审意见: " + filePath);
            } else if ("false_positive".equals(feedback)) {
                // 误报 - 显示输入框让用户填写原因
                showFalsePositiveDialog(row);
            }
        }
    }
    
    // 多行文本单元格编辑器 - 支持文本选择和复制
    private static class MultiLineTableCellEditor extends AbstractCellEditor implements TableCellEditor {
        private JTextArea textArea;
        
        public MultiLineTableCellEditor() {
            textArea = new JTextArea();
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setBorder(JBUI.Borders.empty(5));
            textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            
            // 添加键盘快捷键支持复制
            textArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "copy");
            textArea.getActionMap().put("copy", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String selectedText = textArea.getSelectedText();
                    if (selectedText != null && !selectedText.isEmpty()) {
                        try {
                            StringSelection selection = new StringSelection(selectedText);
                            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clipboard.setContents(selection, selection);
                        } catch (Exception ex) {
                            // 忽略复制错误
                        }
                    }
                }
            });
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            textArea.setText(value != null ? value.toString() : "");
            textArea.selectAll(); // 选中全部文本
            return textArea;
        }
        
        @Override
        public Object getCellEditorValue() {
            return textArea.getText();
        }
    }
    
    /**
     * 自动调整行高
     */
    private void adjustRowHeights() {
        for (int row = 0; row < resultTable.getRowCount(); row++) {
            int maxHeight = 30;
            for (int col = 0; col < 2; col++) {
                TableCellRenderer renderer = resultTable.getCellRenderer(row, col);
                Component comp = renderer.getTableCellRendererComponent(resultTable, resultTable.getValueAt(row, col), false, false, row, col);
                int height = comp.getPreferredSize().height;
                maxHeight = Math.max(maxHeight, height);
            }
            resultTable.setRowHeight(row, maxHeight + 6);
        }
    }

    // 多行自动换行渲染器 - 支持文本选择和复制，但只读
    private static class MultiLineTableCellRenderer extends JTextArea implements TableCellRenderer {
        public MultiLineTableCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
            setBorder(JBUI.Borders.empty(5));
            setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            setEditable(false); // 设置为只读，但可以选择文本
            setFocusable(true); // 支持焦点
            setHighlighter(null); // 移除默认高亮器，避免选择时的高亮效果
            
            // 添加鼠标监听器支持文本选择
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                }
            });
            
            // 添加键盘监听器，只允许复制操作
            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    // 只允许Ctrl+C复制操作
                    if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
                        copy();
                    } else {
                        e.consume(); // 阻止其他键盘操作
                    }
                }
            });
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value != null ? value.toString() : "");
            
            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(table.getBackground());
                setForeground(table.getForeground());
            }
            
            // 根据内容自动调整行高
            setSize(table.getColumnModel().getColumn(column).getWidth(), 0);
            int preferredHeight = getPreferredSize().height;
            
            // 确保最小高度为80px，最大高度为200px
            int newHeight = Math.max(80, Math.min(200, preferredHeight + 10));
            
            if (table.getRowHeight(row) != newHeight) {
                table.setRowHeight(row, newHeight);
            }
            
            return this;
        }
    }

    /**
     * 重置操作 - 清除评审文件区域和评审结果区域的内容
     */
    private void onReset(ActionEvent e) {
        // 确认对话框
        int result = Messages.showDialog(
            project,
            "确定要重置吗？这将清除所有评审文件和结果。",
            "确认重置",
            new String[]{"是", "否"},
            0,
            Messages.getQuestionIcon()
        );
        
        if (result == 0) { // 选择"是"
            // 清除评审文件区域
            fileListModel.clear();
            fileListModel.addElement(new ReviewFileItem("提示", "点击 + 按钮或右键菜单添加要评审的文件", 0, 0, true));
            
            // 清除评审结果区域 - 完全清空，不显示任何默认内容
            clearResultArea();
            
            // 重置MQTT状态
            mqttReceived = false; // 重置时禁用反馈按钮
            mqttDataMap.clear(); // 清空MQTT数据映射
            updateFeedbackButtonsState(); // 更新按钮状态
            
            // 不显示任何消息，保持评审结果区域完全空白
            LOG.info("已重置评审面板，评审结果区域已清空");
        }
    }

    /**
     * 误报弹窗，支持多行输入
     */
    private void showFalsePositiveDialog(int row) {
        LOG.info("开始显示误报对话框，行号: " + row);
        // 创建文本框
        JTextArea textArea = new JTextArea(8, 40);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        textArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        
        // 创建滚动面板
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(400, 150));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(15, 0));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // 添加标题标签
        JLabel titleLabel = new JLabel("请填写误报原因：");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);
        
        // 添加文本框
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 创建按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
        
        // 创建确定按钮
        JButton confirmButton = new JButton("确定");
        confirmButton.setPreferredSize(new Dimension(80, 32));
        confirmButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        confirmButton.setBackground(new Color(33, 150, 243));
        confirmButton.setForeground(Color.WHITE);
        confirmButton.setFocusPainted(false);
        confirmButton.setBorderPainted(false);
        
        // 创建取消按钮
        JButton cancelButton = new JButton("取消");
        cancelButton.setPreferredSize(new Dimension(80, 32));
        cancelButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        cancelButton.setBackground(new Color(158, 158, 158));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        
        // 添加按钮到面板
        buttonPanel.add(confirmButton);
        buttonPanel.add(cancelButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 创建对话框
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(resultTable), "误报反馈", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setContentPane(mainPanel);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(SwingUtilities.getWindowAncestor(resultTable));
        
        // 设置按钮事件
        confirmButton.addActionListener(e -> {
            String reason = textArea.getText().trim();
            if (reason.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请填写误报原因！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            onFalsePositiveSubmit(row, reason);
            dialog.dispose();
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        // 设置回车键确认
        textArea.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "confirm");
        textArea.getActionMap().put("confirm", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmButton.doClick();
            }
        });
        
        // 设置ESC键取消
        dialog.getRootPane().registerKeyboardAction(
            e -> dialog.dispose(),
            "cancel",
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        
        // 显示对话框
        dialog.pack();
        dialog.setVisible(true);
    }

    // 误报提交处理
    private void onFalsePositiveSubmit(int row, String reason) {
        String filePath = (String) resultTableModel.getValueAt(row, 0);
        LOG.info("用户误报反馈: " + filePath + ", 原因: " + reason);
        
        // 获取MQTT消息数据
        MqttMessageData mqttData = mqttDataMap.get(row);
        if (mqttData == null || !mqttData.hasValidData()) {
            LOG.warn("行 " + row + " 没有有效的MQTT数据，无法提交误报反馈");
            return;
        }
        
        // 调用反馈接口，状态为"3"（误报），描述为用户输入的原因
        submitFeedback(mqttData, "3", reason, row);
    }
    
    /**
     * 复制选中的表格内容到剪贴板
     */
    private void copySelectedTableContent() {
        int[] selectedRows = resultTable.getSelectedRows();
        int[] selectedCols = resultTable.getSelectedColumns();
        if (selectedRows.length > 0 && selectedCols.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int row : selectedRows) {
                for (int col : selectedCols) {
                    Object value = resultTable.getValueAt(row, col);
                    sb.append(value == null ? "" : value.toString());
                    if (col != selectedCols[selectedCols.length - 1]) sb.append("\t");
                }
                sb.append("\n");
            }
            try {
                StringSelection selection = new StringSelection(sb.toString());
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(selection, selection);
                LOG.info("已复制表格内容到剪贴板");
            } catch (Exception e) {
                LOG.error("复制到剪贴板失败", e);
            }
        }
    }
    
    /**
     * 提交反馈到服务器
     */
    private void submitFeedback(MqttMessageData mqttData, String feedbackStatus, String description, int row) {
        LOG.info("提交反馈: 状态=" + feedbackStatus + ", 描述=" + description + ", MQTT数据=" + mqttData);
        
        feedbackService.submitFeedback(
            mqttData.codeSubmtRecdNo,
            mqttData.codeFileNo, 
            mqttData.codeSliceNo,
            mqttData.seq,
            feedbackStatus,
            description,
            success -> {
                SwingUtilities.invokeLater(() -> {
                    if (success) {
                        LOG.info("反馈提交成功，行号: " + row);
                        // 可以在这里更新UI状态，比如禁用按钮或显示已提交状态
                    } else {
                        LOG.error("反馈提交失败，行号: " + row);
                        // 可以在这里显示错误消息
                    }
                });
            }
        );
    }
} 