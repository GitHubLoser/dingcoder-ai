package com.codereview.plugin.ui;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.auth.LoginDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 主工具窗口面板，包含代码生成和代码审查两个标签页
 */
public class MainToolWindowPanel extends JBPanel<MainToolWindowPanel> {
    private final Project project;
    private final AuthService authService;
    
    // UI组件
    private JPanel headerPanel;
    private JBTabbedPane tabbedPane;
    private JComboBox<String> userDropdown;
    private JButton loginButton;
    private JPanel loginPromptPanel;
    
    // 标签页面板
    private ChatToolWindowPanel codeGenerationPanel;
    private CodeReviewPanel codeReviewPanel;
    
    // 颜色主题
    private static final Color BACKGROUND_COLOR = new JBColor(Color.WHITE, new Color(43, 43, 43));
    private static final Color HEADER_COLOR = new JBColor(new Color(248, 249, 250), new Color(50, 50, 50));
    
    public MainToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.authService = AuthService.getInstance();
        
        initializeUI();
        updateUIState();
    }
    
    private void initializeUI() {
        setBackground(BACKGROUND_COLOR);
        
        // 创建顶部头部面板
        createHeaderPanel();
        
        // 创建标签页面板
        createTabbedPane();
        
        // 创建未登录提示面板
        createLoginPromptPanel();
        
        // 添加组件到主面板
        add(headerPanel, BorderLayout.NORTH);
        
        // 根据登录状态显示不同内容
        if (authService.isLoggedIn()) {
            add(tabbedPane, BorderLayout.CENTER);
        } else {
            add(loginPromptPanel, BorderLayout.CENTER);
        }
    }
    
    private void createHeaderPanel() {
        headerPanel = new JBPanel<>(new BorderLayout());
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setBorder(JBUI.Borders.empty(12, 16));
        headerPanel.setPreferredSize(new Dimension(0, 60));
        
        // 左侧标题
        JBLabel titleLabel = new JBLabel("鼎码智辅");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        
        // 右侧用户区域
        JPanel rightPanel = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);
        
        // 用户下拉框（登录后显示）
        String[] menuItems = {"退出登录"};
        userDropdown = new JComboBox<>(menuItems);
        userDropdown.setPreferredSize(new Dimension(150, 32));
        userDropdown.setVisible(false);
        userDropdown.addActionListener(this::onUserDropdownAction);
        rightPanel.add(userDropdown);
        
        // 登录按钮（未登录时显示）
        loginButton = new JButton("登录");
        loginButton.setPreferredSize(new Dimension(80, 32));
        loginButton.addActionListener(this::onLoginButtonClick);
        rightPanel.add(loginButton);
        
        headerPanel.add(rightPanel, BorderLayout.EAST);
    }
    
    private void createTabbedPane() {
        tabbedPane = new JBTabbedPane();
        
        // 创建代码生成面板
        codeGenerationPanel = new ChatToolWindowPanel(project);
        tabbedPane.addTab("代码生成", codeGenerationPanel);
        
        // 创建代码审查面板
        codeReviewPanel = new CodeReviewPanel(project);
        tabbedPane.addTab("代码审查", codeReviewPanel);
    }
    
    /**
     * 切换到代码审查tab（供外部调用）
     */
    public void switchToCodeReviewTab() {
        if (tabbedPane != null) {
            tabbedPane.setSelectedIndex(1); // 代码审查是第二个tab
        }
    }
    
    private void createLoginPromptPanel() {
        loginPromptPanel = new JBPanel<>(new BorderLayout());
        loginPromptPanel.setBackground(BACKGROUND_COLOR);
        
        JPanel centerPanel = new JBPanel<>();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        centerPanel.setBorder(JBUI.Borders.empty(80));
        
        // 图标
        JLabel iconLabel = new JLabel();
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        iconLabel.setPreferredSize(new Dimension(80, 80));
        try {
            javax.swing.Icon icon = com.intellij.openapi.util.IconLoader.getIcon("/icons/digiwin-ai-circle.svg", getClass());
            iconLabel.setIcon(icon);
        } catch (Exception ex) {
            iconLabel.setText("D");
        }
        centerPanel.add(iconLabel);
        
        centerPanel.add(Box.createVerticalStrut(20));
        
        // 提示文字
        JBLabel messageLabel = new JBLabel("请先登录后使用鼎码智辅功能");
        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(messageLabel);
        
        centerPanel.add(Box.createVerticalStrut(20));
        
        // 登录按钮
        JButton loginBtn = new JButton("立即登录");
        loginBtn.setPreferredSize(new Dimension(120, 40));
        loginBtn.setMaximumSize(new Dimension(120, 40));
        loginBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginBtn.addActionListener(this::onLoginButtonClick);
        centerPanel.add(loginBtn);
        
        loginPromptPanel.add(centerPanel, BorderLayout.CENTER);
    }
    
    private void onLoginButtonClick(ActionEvent e) {
        // 显示登录对话框
        ApplicationManager.getApplication().invokeLater(() -> {
            LoginDialog loginDialog = new LoginDialog(project);
            if (loginDialog.showAndGet() && authService.isLoggedIn()) {
                updateUIState();
            }
        });
    }
    
    private void onUserDropdownAction(ActionEvent e) {
        JComboBox<?> source = (JComboBox<?>) e.getSource();
        String selectedItem = (String) source.getSelectedItem();
        
        if ("退出登录".equals(selectedItem)) {
            // 直接退出登录，不需要确认
            authService.logout();
            updateUIState();
            
            // 重置下拉选择
            source.setSelectedIndex(-1);
        }
    }
    
    public void updateUIState() {
        boolean isLoggedIn = authService.isLoggedIn();
        
        // 更新头部面板中的组件
        loginButton.setVisible(!isLoggedIn);
        userDropdown.setVisible(isLoggedIn);
        
        if (isLoggedIn) {
            String currentUser = authService.getCurrentUser();
            if (currentUser != null && !currentUser.isEmpty()) {
                userDropdown.insertItemAt(currentUser, 0);
                userDropdown.setSelectedIndex(0);
            }
        } else {
            userDropdown.removeAllItems();
            userDropdown.addItem("退出登录");
        }
        
        // 更新主内容区域
        remove(tabbedPane);
        remove(loginPromptPanel);
        
        if (isLoggedIn) {
            add(tabbedPane, BorderLayout.CENTER);
        } else {
            add(loginPromptPanel, BorderLayout.CENTER);
        }
        
        // 刷新面板
        revalidate();
        repaint();
        
        // 更新子面板状态
        if (isLoggedIn && codeGenerationPanel != null) {
            codeGenerationPanel.updateUIState();
        }
    }
    
    public void dispose() {
        if (codeGenerationPanel != null) {
            codeGenerationPanel.dispose();
        }
        if (codeReviewPanel != null) {
            codeReviewPanel.dispose();
        }
    }
} 