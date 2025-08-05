package com.codereview.plugin.ui;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.auth.LoginDialog;
import com.codereview.plugin.service.MQTTService;
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
        // 延迟更新UI状态，避免启动时卡顿
        SwingUtilities.invokeLater(() -> {
            try {
                updateUIState();
            } catch (Exception e) {
                // 忽略更新时的异常，确保不影响IDEA启动
            }
        });
    }
    
    /**
     * 初始化用户界面组件
     * 设置背景色，创建头部面板、标签页面板和登录提示面板
     * 根据用户登录状态添加相应组件到主面板
     */
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
        
        // 添加窗口激活监听，确保登录状态同步（优化版本）
        addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean hasInitialized = false;
            
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                // 只在首次显示时更新UI状态，避免无限循环
                if (!hasInitialized) {
                    hasInitialized = true;
                    SwingUtilities.invokeLater(() -> updateUIState());
                }
            }
        });
    }
    
    /**
     * 创建头部面板，包含标题和用户操作区域
     * 头部面板分为左右两部分：左侧显示应用标题，右侧显示用户登录/登出相关控件
     */
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
    
    /**
     * 创建标签页面板，包含代码生成和代码审查两个标签页
     * 每个标签页对应一个功能面板：代码生成面板和代码审查面板
     */
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
    
    /**
     * 创建未登录时的提示面板
     * 包含图标、提示文字和登录按钮，引导用户进行登录操作
     */
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
            javax.swing.Icon icon = com.intellij.openapi.util.IconLoader.getIcon("/icons/digiwin-ai-circle-login.svg", getClass());
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
        
        centerPanel.add(Box.createVerticalStrut(15));
        
        loginPromptPanel.add(centerPanel, BorderLayout.CENTER);
    }
    
    /**
     * 登录按钮点击事件处理方法
     * 显示登录对话框，用户登录成功后更新UI状态
     * @param e 点击事件对象
     */
    private void onLoginButtonClick(ActionEvent e) {
        // 显示登录对话框
        ApplicationManager.getApplication().invokeLater(() -> {
            LoginDialog loginDialog = new LoginDialog(project);
            if (loginDialog.showAndGet() && authService.isLoggedIn()) {
                updateUIState();
            }
        });
    }
    
    /**
     * 用户下拉框操作事件处理方法
     * 处理用户选择"退出登录"选项的逻辑
     * @param e 下拉框选择事件对象
     */
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
    
    // 添加状态缓存，避免频繁更新
    private boolean lastLoginState = false;
    private String lastUserName = null;
    
    /**
     * 更新UI状态
     * 根据用户登录状态显示或隐藏相关组件，更新用户界面
     * 包括更新头部面板的登录按钮和用户下拉框，以及主内容区域的显示内容
     */
    public void updateUIState() {
        boolean isLoggedIn = authService.isLoggedIn();
        String currentUser = authService.getCurrentUser();
        
        // 检查状态是否真的发生了变化
        boolean stateChanged = (isLoggedIn != lastLoginState) || 
                             (isLoggedIn && !currentUser.equals(lastUserName));
        
        if (!stateChanged) {
            return; // 状态没有变化，不需要更新UI
        }
        
        // 更新缓存状态
        lastLoginState = isLoggedIn;
        lastUserName = currentUser;
        
        // 更新头部面板中的组件
        loginButton.setVisible(!isLoggedIn);
        userDropdown.setVisible(isLoggedIn);
        
        if (isLoggedIn) {
            if (currentUser != null && !currentUser.isEmpty()) {
                // 先清空所有项
                userDropdown.removeAllItems();
                // 添加用户名和退出登录选项
                userDropdown.addItem(currentUser);
                userDropdown.addItem("退出登录");
                userDropdown.setSelectedIndex(0);
            }
        } else {
            // 未登录时，清空下拉框但不添加任何选项
            userDropdown.removeAllItems();
        }
        
        // 更新主内容区域
        remove(tabbedPane);
        remove(loginPromptPanel);
        
        if (isLoggedIn) {
            add(tabbedPane, BorderLayout.CENTER);
        } else {
            add(loginPromptPanel, BorderLayout.CENTER);
        }
        
        // 延迟刷新面板，避免频繁重绘
        SwingUtilities.invokeLater(() -> {
            try {
                revalidate();
                repaint();
            } catch (Exception e) {
                // 忽略UI更新异常
            }
        });
        
        // 更新子面板状态
        if (isLoggedIn && codeGenerationPanel != null) {
            codeGenerationPanel.updateUIState();
        } else if (!isLoggedIn && codeGenerationPanel != null) {
            // 退出登录时，强制更新子面板状态
            codeGenerationPanel.updateUIState();
        }
    }
    
    /**
     * 清空所有面板内容（退出时调用）
     */
    public void clearAllPanelContent() {
        try {
            // 清空代码生成面板
            if (codeGenerationPanel != null) {
                codeGenerationPanel.clearPanelContent();
            }
            
            // 清空代码审查面板
            if (codeReviewPanel != null) {
                codeReviewPanel.clearPanelContent();
            }
            
            // 重新初始化UI状态
            revalidate();
            repaint();
        } catch (Exception e) {
            // 忽略清空时的异常，确保不影响退出流程
        }
    }

    /**
     * 释放资源
     * 清空面板内容，释放子面板资源，并断开MQTT连接
     */
    public void dispose() {
        try {
            // 先清空面板内容
            clearAllPanelContent();
            
            // 再释放资源
            if (codeGenerationPanel != null) {
                codeGenerationPanel.dispose();
                codeGenerationPanel = null;
            }
            if (codeReviewPanel != null) {
                codeReviewPanel.dispose();
                codeReviewPanel = null;
            }
            
            // 清理MQTT服务
            try {
                MQTTService mqttService = MQTTService.getInstance();
                if (mqttService != null && mqttService.isConnected()) {
                    mqttService.disconnect();
                }
            } catch (Exception e) {
                // 忽略清理时的异常
            }
        } catch (Exception e) {
            // 忽略dispose时的异常
        }
    }
} 