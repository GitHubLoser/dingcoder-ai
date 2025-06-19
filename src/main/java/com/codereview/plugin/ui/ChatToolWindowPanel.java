package com.codereview.plugin.ui;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.auth.LoginDialog;
import com.codereview.plugin.service.AIService;
import com.codereview.plugin.service.MQTTService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

// 添加编辑器相关的import
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ide.highlighter.JavaFileType;

/**
 * 聊天界面
 */
public class ChatToolWindowPanel extends JBPanel<ChatToolWindowPanel> {
    private static final Logger LOG = Logger.getInstance(ChatToolWindowPanel.class);
    
    private final Project project;
    private final AuthService authService;
    private final AIService aiService;
    private final MQTTService mqttService;
    
    // UI组件
    private JPanel headerPanel;
    private JComboBox<String> userDropdown;
    private JButton loginButton;
    private JPanel chatPanel;
    private JScrollPane chatScrollPane;
    private JTextField inputField;
    private JButton sendButton;
    private JPanel welcomePanel;
    
    // 消息列表
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    
    // 颜色主题
    private static final Color BACKGROUND_COLOR = new JBColor(Color.WHITE, new Color(43, 43, 43));
    private static final Color HEADER_COLOR = new JBColor(new Color(248, 249, 250), new Color(50, 50, 50));
    private static final Color INPUT_AREA_COLOR = new JBColor(new Color(248, 249, 250), new Color(50, 50, 50));
    private static final Color ASSISTANT_BUBBLE_COLOR = new JBColor(new Color(240, 242, 247), new Color(60, 60, 60));
    private static final Color USER_BUBBLE_COLOR = new JBColor(new Color(16, 142, 233), new Color(52, 139, 255));
    
    public ChatToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.authService = AuthService.getInstance();
        this.aiService = AIService.getInstance();
        this.mqttService = MQTTService.getInstance();
        
        LOG.info("创建ChatToolWindowPanel实例");
        
        initializeUI();
        updateUIState();
        
        // 设置MQTT消息回调
        LOG.info("设置MQTT消息回调");
        mqttService.setMessageCallback(this::onMQTTMessage);
    }
    
    private void initializeUI() {
        setBackground(BACKGROUND_COLOR);
        
        // 创建顶部头部面板
        createHeaderPanel();
        
        // 创建聊天区域
        createChatArea();
        
        // 创建输入区域
        createInputArea();
        
        // 创建欢迎面板
        createWelcomePanel();
        
        // 添加组件到主面板
        add(headerPanel, BorderLayout.NORTH);
        add(chatScrollPane, BorderLayout.CENTER);
        add(createInputPanel(), BorderLayout.SOUTH);
    }
    
    private void createHeaderPanel() {
        headerPanel = new JBPanel<>(new BorderLayout());
        headerPanel.setBackground(HEADER_COLOR);
        headerPanel.setBorder(JBUI.Borders.empty(12, 16));
        headerPanel.setPreferredSize(new Dimension(0, 60));
        
        // 左侧标题和状态
        JPanel leftPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftPanel.setOpaque(false);
        
        JBLabel titleLabel = new JBLabel("鼎码智辅");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        leftPanel.add(titleLabel);
        
        // 状态指示器
        JBLabel statusLabel = new JBLabel();
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        statusLabel.setBorder(JBUI.Borders.emptyLeft(12));
        leftPanel.add(statusLabel);
        
        // 保存状态标签引用
        headerPanel.putClientProperty("statusLabel", statusLabel);
        
        headerPanel.add(leftPanel, BorderLayout.WEST);
        
        // 右侧用户区域
        JPanel userPanel = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        userPanel.setOpaque(false);
        
        // 用户下拉框（登录后显示）
        userDropdown = new JComboBox<>();
        userDropdown.setPreferredSize(new Dimension(150, 32));
        userDropdown.setVisible(false);
        userDropdown.addActionListener(this::onUserDropdownAction);
        
        // 登录按钮（未登录时显示）
        loginButton = new JButton("登录");
        loginButton.setPreferredSize(new Dimension(80, 32));
        loginButton.addActionListener(this::onLoginButtonClick);
        
        userPanel.add(userDropdown);
        userPanel.add(loginButton);
        headerPanel.add(userPanel, BorderLayout.EAST);
    }
    
    private void createChatArea() {
        chatPanel = new JBPanel<>();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(BACKGROUND_COLOR);
        chatPanel.setBorder(JBUI.Borders.empty(16));
        
        chatScrollPane = new JBScrollPane(chatPanel);
        chatScrollPane.setBorder(null);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        chatScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
    }
    
    private void createWelcomePanel() {
        welcomePanel = new JBPanel<>(new BorderLayout());
        welcomePanel.setBackground(BACKGROUND_COLOR);
        welcomePanel.setBorder(JBUI.Borders.empty(40));
        
        JPanel centerPanel = new JBPanel<>();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        
        // 头像
        JBLabel avatarLabel = new JBLabel("🤖");
        avatarLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 48));
        avatarLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(avatarLabel);
        
        centerPanel.add(Box.createVerticalStrut(16));
        
        // 标题
        JBLabel titleLabel = new JBLabel("鼎码智辅");
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(titleLabel);
        
        centerPanel.add(Box.createVerticalStrut(16));
        
        // 登录提示或欢迎信息
        JBLabel descLabel = new JBLabel();
        descLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        descLabel.setForeground(JBColor.GRAY);
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(descLabel);
        
        // 登录按钮（大按钮）
        JButton bigLoginButton = new JButton("登录后可使用");
        bigLoginButton.setPreferredSize(new Dimension(200, 40));
        bigLoginButton.setMaximumSize(new Dimension(200, 40));
        bigLoginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        bigLoginButton.addActionListener(this::onLoginButtonClick);
        
        centerPanel.add(Box.createVerticalStrut(24));
        centerPanel.add(bigLoginButton);
        
        welcomePanel.add(centerPanel, BorderLayout.CENTER);
        
        // 保存组件引用以便后续更新
        welcomePanel.putClientProperty("descLabel", descLabel);
        welcomePanel.putClientProperty("bigLoginButton", bigLoginButton);
    }
    
    private void createInputArea() {
        inputField = new JTextField();
        inputField.setBorder(JBUI.Borders.empty(12, 16));
        inputField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        
        // 设置提示文字
        inputField.setText("输入API名称或者校验器名称");
        inputField.setForeground(JBColor.GRAY);
        
        // 添加焦点监听器处理提示文字
        inputField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (inputField.getText().equals("输入API名称或者校验器名称")) {
                    inputField.setText("");
                    inputField.setForeground(UIUtil.getLabelForeground());
                }
            }
            
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (inputField.getText().trim().isEmpty()) {
                    inputField.setText("输入API名称或者校验器名称");
                    inputField.setForeground(JBColor.GRAY);
                }
            }
        });
        
        // 添加回车键监听器
        inputField.addActionListener(this::onSendMessage);
        
        // 添加键盘监听器
        inputField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    onSendMessage(null);
                }
            }
        });
        
        sendButton = new JButton("发送");
        sendButton.setPreferredSize(new Dimension(60, 40));
        sendButton.addActionListener(this::onSendMessage);
    }
    
    private JPanel createInputPanel() {
        // 外层容器，增加更多垂直间距
        JPanel outerPanel = new JBPanel<>(new BorderLayout());
        outerPanel.setBackground(INPUT_AREA_COLOR);
        outerPanel.setBorder(JBUI.Borders.empty(16, 20, 20, 20)); // 上下左右间距
        
        // 内层输入区域容器
        JPanel inputPanel = new JBPanel<>(new BorderLayout());
        inputPanel.setBackground(JBColor.WHITE);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
            JBUI.Borders.customLine(new JBColor(Color.LIGHT_GRAY, new Color(70, 70, 70)), 1),
            JBUI.Borders.empty(8, 12)
        ));
        inputPanel.setPreferredSize(new Dimension(0, 44));
        
        // 输入框样式调整
        inputField.setBorder(JBUI.Borders.empty());
        inputField.setOpaque(false);
        
        // 发送按钮样式调整
        sendButton.setPreferredSize(new Dimension(60, 28));
        sendButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        
        // 输入框和按钮之间的间距
        JPanel inputContainer = new JBPanel<>(new BorderLayout());
        inputContainer.setOpaque(false);
        inputContainer.setBorder(new EmptyBorder(0, 0, 0, 8));
        inputContainer.add(inputField, BorderLayout.CENTER);
        
        inputPanel.add(inputContainer, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        outerPanel.add(inputPanel, BorderLayout.CENTER);
        
        return outerPanel;
    }
    
    /**
     * 更新UI状态
     */
    public void updateUIState() {
        boolean isLoggedIn = authService.isLoggedIn();
        
        // 更新头部状态
        JBLabel statusLabel = (JBLabel) headerPanel.getClientProperty("statusLabel");
        if (isLoggedIn) {
            statusLabel.setText("已连接");
            statusLabel.setForeground(new Color(40, 167, 69));
            loginButton.setVisible(false);
            userDropdown.setVisible(true);
            userDropdown.removeAllItems();
            userDropdown.addItem(authService.getCurrentUser());
            userDropdown.addItem("退出登录");
        } else {
            statusLabel.setText("未连接");
            statusLabel.setForeground(new Color(220, 53, 69));
            loginButton.setVisible(true);
            userDropdown.setVisible(false);
        }
        
        // 更新欢迎面板状态
        JBLabel descLabel = (JBLabel) welcomePanel.getClientProperty("descLabel");
        JButton bigLoginButton = (JButton) welcomePanel.getClientProperty("bigLoginButton");
        
        if (isLoggedIn) {
            descLabel.setText("<html><center>我是你的AI编码助手，可以帮你生成校验器的代码，<br/>只需要在输入框里输入api名称或者校验器的名称即可，<br/>多个校验器之间用分号隔开，<br/>现在就开始体验吧!</center></html>");
            bigLoginButton.setVisible(false);
            
            // 如果没有聊天消息，显示欢迎面板
            if (chatMessages.isEmpty()) {
                chatScrollPane.setViewportView(welcomePanel);
            }
        } else {
            descLabel.setText("登录后可使用");
            bigLoginButton.setVisible(true);
            bigLoginButton.setText("登录后可使用");
            chatScrollPane.setViewportView(welcomePanel);
        }
        
        // 更新输入区域状态
        inputField.setEnabled(isLoggedIn);
        sendButton.setEnabled(isLoggedIn);
        
        revalidate();
        repaint();
    }
    
    private void onLoginButtonClick(ActionEvent e) {
        LoginDialog loginDialog = new LoginDialog(project);
        if (loginDialog.showAndGet()) {
            updateUIState();
        }
    }
    
    private void onUserDropdownAction(ActionEvent e) {
        if (userDropdown.getSelectedItem() != null && "退出登录".equals(userDropdown.getSelectedItem().toString())) {
            // 退出登录
            authService.logout(); // 这里会自动断开MQTT连接
            
            // 清空消息
            chatMessages.clear();
            chatPanel.removeAll();
            
            // 更新UI状态
            updateUIState();
        }
    }
    
    private void onSendMessage(ActionEvent e) {
        String message = inputField.getText().trim();
        if (message.isEmpty() || message.equals("输入API名称或者校验器名称")) {
            return;
        }
        
        if (!authService.isLoggedIn()) {
            JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 清空输入框
        inputField.setText("");
        inputField.setForeground(JBColor.GRAY);
        inputField.setText("输入API名称或者校验器名称");
        
        // 添加用户消息
        addUserMessage(message);
        
        // 添加AI回复
        addAssistantMessage("正在为您生成代码，请稍候...");
    }
    
    private void addUserMessage(String message) {
        ChatMessage chatMessage = new ChatMessage(message, true);
        chatMessages.add(chatMessage);
        updateChatDisplay();
    }
    
    private void addAssistantMessage(String message) {
        LOG.info("开始添加助手消息");
        ChatMessage chatMessage = new ChatMessage(message, false);
        chatMessages.add(chatMessage);
        updateChatDisplay();
        LOG.info("助手消息添加完成");
    }
    
    private void updateChatDisplay() {
        LOG.info("开始更新聊天显示");
        // 切换到聊天面板
        if (chatScrollPane.getViewport().getView() != chatPanel) {
            LOG.info("切换到聊天面板");
            chatScrollPane.setViewportView(chatPanel);
        }
        
        // 清空聊天面板
        chatPanel.removeAll();
        
        // 添加所有消息
        LOG.info("添加 " + chatMessages.size() + " 条消息到面板");
        for (ChatMessage message : chatMessages) {
            JPanel messagePanel = createMessagePanel(message);
            chatPanel.add(messagePanel);
            chatPanel.add(Box.createVerticalStrut(8));
        }
        
        // 刷新面板
        chatPanel.revalidate();
        chatPanel.repaint();
        
        // 滚动到底部
        SwingUtilities.invokeLater(() -> {
            LOG.info("滚动到底部");
            JScrollBar verticalScrollBar = chatScrollPane.getVerticalScrollBar();
            verticalScrollBar.setValue(verticalScrollBar.getMaximum());
        });
        LOG.info("聊天显示更新完成");
    }
    
    private JPanel createMessagePanel(ChatMessage message) {
        JPanel containerPanel = new JBPanel<>(new BorderLayout());
        containerPanel.setOpaque(false);
        containerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        
        JPanel messagePanel = new JBPanel<>();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBorder(JBUI.Borders.empty(12, 16));
        messagePanel.setMaximumSize(new Dimension(600, Integer.MAX_VALUE));
        
        if (message.isUser()) {
            messagePanel.setBackground(USER_BUBBLE_COLOR);
            containerPanel.add(messagePanel, BorderLayout.EAST);
            
            // 用户消息使用普通文本区域
            JTextArea textArea = new JTextArea(message.getContent());
            textArea.setOpaque(false);
            textArea.setEditable(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            textArea.setForeground(Color.WHITE);
            messagePanel.add(textArea);
        } else {
            messagePanel.setBackground(ASSISTANT_BUBBLE_COLOR);
            containerPanel.add(messagePanel, BorderLayout.WEST);
            
            // AI回复可能包含代码，使用Editor组件
            String content = message.getContent();
            
            // 创建编辑器
            EditorFactory editorFactory = EditorFactory.getInstance();
            Document document = editorFactory.createDocument(content);
            Editor editor = editorFactory.createEditor(document, project, JavaFileType.INSTANCE, true);
            
            // 配置编辑器设置
            EditorEx editorEx = (EditorEx) editor;
            EditorSettings settings = editor.getSettings();
            settings.setFoldingOutlineShown(false);
            settings.setLineNumbersShown(false);
            settings.setLineMarkerAreaShown(false);
            settings.setIndentGuidesShown(false);
            settings.setGutterIconsShown(false);
            settings.setRightMarginShown(false);
            settings.setAdditionalColumnsCount(0);
            settings.setAdditionalLinesCount(0);
            settings.setUseSoftWraps(true);
            
            // 使用当前主题的配色
            EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
            editorEx.setColorsScheme(colorsScheme);
            
            // 设置背景色为透明
            editorEx.setBackgroundColor(ASSISTANT_BUBBLE_COLOR);
            
            // 添加编辑器组件到消息面板
            JComponent editorComponent = editor.getComponent();
            editorComponent.setPreferredSize(new Dimension(550, Math.min(400, editor.getDocument().getLineCount() * 20)));
            messagePanel.add(editorComponent);
            
            // 在ChatMessage类中保存editor引用，以便后续dispose
            message.setEditor(editor);
        }
        
        return containerPanel;
    }
    
    private void onMQTTMessage(String message) {
        LOG.info("收到MQTT消息回调: " + message);
        SwingUtilities.invokeLater(() -> {
            try {
                LOG.info("在EDT线程中处理消息...");
                // 如果是在欢迎面板，切换到聊天面板
                if (chatScrollPane.getViewport().getView() == welcomePanel) {
                    LOG.info("从欢迎面板切换到聊天面板");
                    chatScrollPane.setViewportView(chatPanel);
                }
                
                // 添加助手消息
                LOG.info("添加助手消息到面板");
                addAssistantMessage(message);
                LOG.info("消息添加完成");
            } catch (Exception e) {
                LOG.error("处理MQTT消息时出错", e);
            }
        });
    }
    
    // 内部类：聊天消息
    private static class ChatMessage {
        private final String content;
        private final boolean isUser;
        private Editor editor; // 添加editor字段
        
        public ChatMessage(String content, boolean isUser) {
            this.content = content;
            this.isUser = isUser;
        }
        
        public String getContent() {
            return content;
        }
        
        public boolean isUser() {
            return isUser;
        }
        
        public void setEditor(Editor editor) {
            this.editor = editor;
        }
        
        public Editor getEditor() {
            return editor;
        }
    }
    
    /**
     * 释放资源
     */
    public void dispose() {
        // 释放所有编辑器
        for (ChatMessage message : chatMessages) {
            if (!message.isUser() && message.getEditor() != null) {
                EditorFactory.getInstance().releaseEditor(message.getEditor());
            }
        }
        // 清空消息
        chatMessages.clear();
        chatPanel.removeAll();
    }
} 