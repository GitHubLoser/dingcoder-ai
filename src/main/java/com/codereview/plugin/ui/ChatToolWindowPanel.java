package com.codereview.plugin.ui;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.auth.LoginDialog;
import com.codereview.plugin.constant.CommonConstant;
import com.codereview.plugin.model.ChatMessage;
import com.codereview.plugin.service.AIService;
import com.codereview.plugin.service.CodeGenerationService;
import com.codereview.plugin.service.MQTTService;
import com.codereview.plugin.service.ValidateSpecService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.*;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.awt.Cursor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
    private final ValidateSpecService validateSpecService;
    
    // UI组件
    private JPanel headerPanel;
    private JComboBox<String> userDropdown;
    private JButton loginButton;
    private JPanel chatPanel;
    private JScrollPane chatScrollPane;
    private JTextArea inputField;
    private JButton sendButton;
    private JPanel welcomePanel;
    
    // 消息列表
    private final List<ChatMessage> chatMessages = new ArrayList<>();
    
    // 发送按钮状态管理
    private boolean isWaitingForGeneration = false;
    
    // 颜色主题
    private static final Color BACKGROUND_COLOR = new JBColor(Color.WHITE, new Color(43, 43, 43));
    private static final Color HEADER_COLOR = new JBColor(new Color(248, 249, 250), new Color(50, 50, 50));
    private static final Color INPUT_AREA_COLOR = new JBColor(new Color(248, 249, 250), new Color(50, 50, 50));
    private static final Color ASSISTANT_BUBBLE_COLOR = new JBColor(new Color(240, 242, 247), new Color(60, 60, 60));
    private static final Color USER_BUBBLE_COLOR = new JBColor(new Color(16, 142, 233), new Color(52, 139, 255));
    
    // 新增通义灵码风格的输入框颜色
    private static final Color INPUT_BORDER_COLOR = new JBColor(new Color(225, 225, 225), new Color(70, 70, 70));
    private static final Color INPUT_FOCUS_BORDER_COLOR = new JBColor(new Color(24, 144, 255), new Color(64, 169, 255));
    private static final Color INPUT_BACKGROUND_COLOR = new JBColor(Color.WHITE, new Color(60, 60, 60));
    private static final Color SEND_BUTTON_COLOR = new JBColor(new Color(24, 144, 255), new Color(64, 169, 255));
    private static final Color SEND_BUTTON_HOVER_COLOR = new JBColor(new Color(40, 167, 69), new Color(52, 199, 89));
    private static final Color SEND_BUTTON_DISABLED_COLOR = new JBColor(new Color(200, 200, 200), new Color(100, 100, 100));
    
    public ChatToolWindowPanel(Project project) {
        super(new BorderLayout());
        this.project = project;
        this.authService = AuthService.getInstance();
        this.aiService = AIService.getInstance();
        this.mqttService = MQTTService.getInstance();
        this.validateSpecService = new ValidateSpecService();
        
        LOG.info("创建ChatToolWindowPanel实例");
        
        initializeUI();
        updateUIState();
        
        // 确保MQTT消息回调总是被设置
        LOG.info("设置MQTT消息回调");
        setMqttCallback();
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
        JPanel rightPanel = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.setOpaque(false);
        
        // 用户下拉框（登录后显示）
        userDropdown = new JComboBox<>();
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
    
    private void createChatArea() {
        // 创建聊天面板
        chatPanel = new JBPanel<>();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(BACKGROUND_COLOR);
        
        // 创建滚动面板
        chatScrollPane = new JBScrollPane(welcomePanel);
        chatScrollPane.setBorder(null);
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
        // 使用JTextArea替代JTextField
        inputField = new JTextArea();
        inputField.setLineWrap(true);  // 启用自动换行
        inputField.setWrapStyleWord(true);  // 按单词换行
        inputField.setRows(1);  // 初始显示1行
        
        // 通义灵码风格的字体设置
        Font inputFont = new Font("PingFang SC", Font.PLAIN, 14);
        if (!inputFont.getFamily().equals("PingFang SC")) {
            inputFont = new Font("Microsoft YaHei", Font.PLAIN, 14);
        }
        if (!inputFont.getFamily().equals("Microsoft YaHei")) {
            inputFont = UIUtil.getLabelFont().deriveFont(14f);
        }
        inputField.setFont(inputFont);
        
        // 设置背景色和前景色
        inputField.setBackground(INPUT_BACKGROUND_COLOR);
        inputField.setForeground(new JBColor(Color.BLACK, Color.WHITE));
        
        // 设置内边距
        inputField.setBorder(JBUI.Borders.empty(12, 16));
        
        // 设置圆角边框
        inputField.setBorder(new RoundedBorder(8, INPUT_BORDER_COLOR, 1));
        
        // 添加焦点监听器，实现焦点时的边框颜色变化
        inputField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                inputField.setBorder(new RoundedBorder(8, INPUT_FOCUS_BORDER_COLOR, 2));
            }
            
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                inputField.setBorder(new RoundedBorder(8, INPUT_BORDER_COLOR, 1));
            }
        });
        
        // 创建一个带滚动条的面板来包装输入框
        JScrollPane inputScrollPane = new JBScrollPane(
            inputField,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        inputScrollPane.setBorder(null);
        inputScrollPane.setOpaque(false);
        inputScrollPane.getViewport().setOpaque(false);
        
        // 添加文档监听器来处理高度自适应
        inputField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void adjustRows() {
                int lines = inputField.getLineCount();
                int minRows = 1, maxRows = 6;
                int rows = Math.max(minRows, Math.min(maxRows, lines));
                inputField.setRows(rows);
                inputField.revalidate();
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { adjustRows(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { adjustRows(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { adjustRows(); }
        });
        
        // 添加回车键监听
        inputField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "sendMessage");
        inputField.getActionMap().put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onSendMessage(e);
            }
        });
        
        // Shift+Enter用于换行
        inputField.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "newline");
        inputField.getActionMap().put("newline", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                inputField.append("\n");
            }
        });
        
        // 创建发送按钮
        sendButton = createModernSendButton();
        
        // 使用BorderLayout布局的面板来组织输入区域
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBorder(JBUI.Borders.empty(5));
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);
        
        // 将输入面板添加到主面板
        add(inputPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createInputPanel() {
        // 外层容器，增加更多垂直间距
        JPanel outerPanel = new JBPanel<>(new BorderLayout());
        outerPanel.setBackground(INPUT_AREA_COLOR);
        outerPanel.setBorder(JBUI.Borders.empty(16, 20, 20, 20)); // 上下左右间距
        
        // 内层输入区域容器 - 通义灵码风格
        JPanel inputPanel = new JBPanel<>(new BorderLayout());
        inputPanel.setBackground(INPUT_BACKGROUND_COLOR);
        inputPanel.setBorder(new RoundedBorder(12, INPUT_BORDER_COLOR, 1));
        // 不要设置固定高度
        // inputPanel.setPreferredSize(new Dimension(0, 48));
        
        // 输入框样式调整
        inputField.setBorder(JBUI.Borders.empty(12, 16));
        inputField.setOpaque(false);
        
        // 发送按钮样式调整 - 使用新的现代化按钮
        if (sendButton == null) {
            sendButton = createModernSendButton();
        }
        
        // 输入框和按钮之间的间距
        JPanel inputContainer = new JBPanel<>(new BorderLayout());
        inputContainer.setOpaque(false);
        inputContainer.setBorder(new EmptyBorder(0, 0, 0, 12));
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
            descLabel.setText("<html><center>我是你的AI编码助手，可以帮你生成校验器的代码，<br/>" +
                    "只需要在输入框里输入api名称或者校验器的名称即可，<br/>" +
                    "多个校验器之间用逗号隔开，例如：<br/>" +
                    "bm.pre_item.create:VD_pre_item_00012,VD_pre_item_00015<br/>" +
                    "现在就开始体验吧!" +
                    "</center></html>");
            bigLoginButton.setVisible(false);
            
            // 如果没有聊天消息，显示欢迎面板
            if (chatMessages.isEmpty()) {
                chatScrollPane.setViewportView(welcomePanel);
            }
        } else {
//            descLabel.setText("登录后可使用");
            bigLoginButton.setVisible(true);
            bigLoginButton.setText("登录后可使用");
            chatScrollPane.setViewportView(welcomePanel);
        }
        
        // 更新输入区域状态
        inputField.setEnabled(isLoggedIn);
        // 发送按钮状态：登录状态 && 不在等待生成状态
        boolean canSend = isLoggedIn && !isWaitingForGeneration;
        sendButton.setEnabled(canSend);
        
        // 更新发送按钮样式
        if (canSend) {
            sendButton.setText("发送");
            sendButton.setBackground(SEND_BUTTON_COLOR);
            sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
        } else if (isWaitingForGeneration) {
            sendButton.setText("生成中...");
            sendButton.setBackground(SEND_BUTTON_DISABLED_COLOR);
            sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_DISABLED_COLOR, 0));
        } else {
            sendButton.setText("发送");
            sendButton.setBackground(SEND_BUTTON_DISABLED_COLOR);
            sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_DISABLED_COLOR, 0));
        }
        
        revalidate();
        repaint();
    }
    
    private void onLoginButtonClick(ActionEvent e) {
        LOG.info("点击登录按钮");
        LoginDialog dialog = new LoginDialog(project);
        if (dialog.showAndGet()) {
            LOG.info("登录对话框返回成功");
            updateUIState();
            
            // 登录成功后重新设置MQTT回调
            setMqttCallback();
        }
    }
    
    private void onUserDropdownAction(ActionEvent e) {
        if (userDropdown.getSelectedItem() != null && "退出登录".equals(userDropdown.getSelectedItem().toString())) {
            // 退出登录前先保存当前回调
            Consumer<String> currentCallback = mqttService.getMessageCallback();
            
            // 退出登录
            authService.logout(); // 这里会自动断开MQTT连接
            
            // 清空消息
            chatMessages.clear();
            chatPanel.removeAll();
            
            // 重置等待状态
            isWaitingForGeneration = false;
            
            // 更新UI状态
            updateUIState();
            
            // 重新设置MQTT回调，确保回调不丢失
            if (currentCallback != null) {
                mqttService.setMessageCallback(currentCallback);
            } else {
                setMqttCallback();
            }
        }
    }
    
    private void onSendMessage(ActionEvent e) {
        String input = inputField.getText().trim();
        if (input.isEmpty() || isWaitingForGeneration) {
            return;
        }
        addUserMessage(input);
        inputField.setText("");
        // 彻底移除 setPreferredSize、revalidate、repaint 相关代码
        // 让 DocumentListener 自动调整高度
        isWaitingForGeneration = true;
        sendButton.setEnabled(false);
        sendButton.setText("生成中...");
        sendButton.setBackground(SEND_BUTTON_DISABLED_COLOR);
        sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_DISABLED_COLOR, 0));

        // 自动切换到消息面板
        if (chatScrollPane.getViewport().getView() == welcomePanel) {
            chatScrollPane.setViewportView(chatPanel);
        }
        // 立即插入"正在为您生成代码，请稍候..."
        addAssistantMessage("正在为您生成代码，请稍候...");

        // 获取当前选中的目录路径
        VirtualFile selectedDir = CodeGenerationService.getInstance(project).getCurrentSelectedDirectory();
        String filePath = selectedDir != null ? selectedDir.getPath() : "";
        // 调用验证规格API
        validateSpecService.callValidateSpecApi(input, filePath);
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
        // 清空聊天面板
        chatPanel.removeAll();
        
        // 重新添加所有消息
        for (ChatMessage message : chatMessages) {
            chatPanel.add(createMessagePanel(message));
        }
        
        // 刷新UI
        chatPanel.revalidate();
        chatPanel.repaint();
        
        // 滚动到底部
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
    
    private JPanel createMessagePanel(ChatMessage message) {
        JPanel outerPanel = new JPanel();
        outerPanel.setLayout(new BoxLayout(outerPanel, BoxLayout.X_AXIS));
        outerPanel.setOpaque(false);
        outerPanel.setBorder(JBUI.Borders.empty(2, 0, 2, 0));

        String content = message.getContent();
        boolean isSpecialMessage = "正在为您生成代码，请稍候...".equals(content.trim()) || 
                                 "所有代码均已生成".equals(content.trim());

        if (isSpecialMessage) {
            // 特殊消息：左对齐淡蓝色气泡
            JPanel bubble = new JPanel();
            bubble.setOpaque(true);
            bubble.setBackground(new JBColor(new Color(232, 244, 253), new Color(40, 50, 60)));
            bubble.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.X_AXIS));
            JLabel label = new JLabel(content);
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            label.setForeground(new JBColor(new Color(33, 150, 243), new Color(120, 170, 255)));
            bubble.add(label);
            outerPanel.add(bubble);
            outerPanel.add(Box.createHorizontalGlue());
            return outerPanel;
        }

        if (message.isUser()) {
            // 用户消息：靠右灰色气泡
            outerPanel.add(Box.createHorizontalGlue());
            JPanel bubble = new JPanel();
            bubble.setOpaque(true);
            // 使用更柔和的灰色背景
            bubble.setBackground(new JBColor(new Color(243, 244, 246), new Color(50, 54, 60)));
            bubble.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.X_AXIS));
            JLabel label = new JLabel("<html>" + content.replace("\n", "<br>") + "</html>");
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            // 使用更深的文字颜色
            label.setForeground(new JBColor(new Color(31, 35, 40), new Color(230, 237, 243)));
            bubble.add(label);
            outerPanel.add(bubble);
        } else if (content.trim().startsWith("package ") || content.trim().contains("class ") || content.trim().contains("interface ") || content.trim().contains("enum ")) {
            // 折叠代码块：默认只显示类名，点击展开/收起完整代码
            String classLine = extractJavaClassLine(content);
            JPanel foldPanel = new JPanel();
            foldPanel.setLayout(new BorderLayout());
            foldPanel.setOpaque(false);

            // 折叠按钮（类名）
            JToggleButton toggleButton = new JToggleButton("▶ " + classLine);
            toggleButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            toggleButton.setFocusPainted(false);
            toggleButton.setContentAreaFilled(false);
            toggleButton.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            toggleButton.setHorizontalAlignment(SwingConstants.LEFT);

            // 生成Java文件按钮
            JButton generateButton = new JButton("生成Java文件");
            generateButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            generateButton.setForeground(new JBColor(Color.WHITE, Color.WHITE));
            generateButton.setBackground(new JBColor(new Color(0x2B5AB8), new Color(0x2B5AB8)));
            generateButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
            generateButton.setFocusPainted(false);
            generateButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            generateButton.setOpaque(true);
            generateButton.addActionListener(e -> {
                CodeGenerationService codeGenService = CodeGenerationService.getInstance(project);
                if (codeGenService.generateJavaFile(content, true)) {
                    generateButton.setText("✓ 已生成");
                    generateButton.setEnabled(false);
                    generateButton.setBackground(new JBColor(new Color(0x28A745), new Color(0x28A745)));
                }
            });
            generateButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (generateButton.isEnabled()) {
                        generateButton.setBackground(new JBColor(new Color(0x234A94), new Color(0x234A94)));
                    }
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    if (generateButton.isEnabled()) {
                        generateButton.setBackground(new JBColor(new Color(0x2B5AB8), new Color(0x2B5AB8)));
                    }
                }
            });

            // 标题行（类名+按钮）
            JPanel titlePanel = new JPanel();
            titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.X_AXIS));
            titlePanel.setOpaque(false);
            titlePanel.add(toggleButton);
            titlePanel.add(Box.createHorizontalStrut(8));
            titlePanel.add(generateButton);
            titlePanel.add(Box.createHorizontalGlue());

            // 代码编辑器
            EditorFactory editorFactory = EditorFactory.getInstance();
            Document document = editorFactory.createDocument(content);
            Editor editor = editorFactory.createEditor(document, project, JavaFileType.INSTANCE, true);
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
            EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
            editorEx.setColorsScheme(colorsScheme);
            editorEx.setBackgroundColor(new JBColor(new Color(250, 250, 250), new Color(40, 44, 50)));
            editorEx.getColorsScheme().setEditorFontName("JetBrains Mono");
            message.setEditor(editor);
            JComponent editorComponent = editor.getComponent();
            editorComponent.setVisible(false); // 默认折叠

            // 展开/收起逻辑
            toggleButton.addActionListener(e -> {
                boolean expanded = toggleButton.isSelected();
                editorComponent.setVisible(expanded);
                toggleButton.setText((expanded ? "▼ " : "▶ ") + classLine);
                foldPanel.revalidate();
                foldPanel.repaint();
            });

            foldPanel.add(titlePanel, BorderLayout.NORTH);
            foldPanel.add(editorComponent, BorderLayout.CENTER);
            outerPanel.add(foldPanel);
        } else {
            // AI普通消息：靠左灰色气泡
            JPanel bubble = new JPanel();
            bubble.setOpaque(true);
            bubble.setBackground(new JBColor(new Color(245, 247, 250), new Color(60, 60, 60)));
            bubble.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.X_AXIS));
            JLabel label = new JLabel("<html>" + content.replace("\n", "<br>") + "</html>");
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            label.setForeground(new JBColor(new Color(33, 33, 33), new Color(220, 220, 220)));
            bubble.add(label);
            outerPanel.add(bubble);
            outerPanel.add(Box.createHorizontalGlue());
        }
        return outerPanel;
    }
    
    // 提取Java类/接口/枚举声明行，只显示类名.java
    private String extractJavaClassLine(String code) {
        String[] lines = code.split("\n");
        for (String line : lines) {
            String trim = line.trim();
            if (trim.startsWith("public class ") || trim.startsWith("class ")) {
                return extractName(trim, "class");
            } else if (trim.startsWith("public interface ") || trim.startsWith("interface ")) {
                return extractName(trim, "interface");
            } else if (trim.startsWith("public enum ") || trim.startsWith("enum ")) {
                return extractName(trim, "enum");
            }
        }
        return "Java代码.java";
    }
    // 提取名称并加.java后缀
    private String extractName(String line, String keyword) {
        String[] parts = line.split(keyword);
        if (parts.length > 1) {
            String name = parts[1].trim().split("[\\s\\{]")[0];
            return name + ".java";
        }
        return "Java代码.java";
    }
    
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setBorder(JBUI.Borders.empty(8, 16));
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.brighter());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });
        
        return button;
    }
    
    /**
     * 创建通义灵码风格的发送按钮
     */
    private JButton createModernSendButton() {
        JButton button = new JButton("发送");
        button.setPreferredSize(new Dimension(60, 36));
        button.setFont(new Font("PingFang SC", Font.PLAIN, 13));
        if (!button.getFont().getFamily().equals("PingFang SC")) {
            button.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        }
        if (!button.getFont().getFamily().equals("Microsoft YaHei")) {
            button.setFont(UIUtil.getLabelFont().deriveFont(13f));
        }
        
        // 设置按钮样式
        button.setBackground(SEND_BUTTON_COLOR);
        button.setForeground(Color.WHITE);
        button.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // 添加鼠标悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(SEND_BUTTON_HOVER_COLOR);
                    button.setBorder(new RoundedBorder(6, SEND_BUTTON_HOVER_COLOR, 0));
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(SEND_BUTTON_COLOR);
                    button.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
                }
            }
        });
        
        return button;
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
    
    private void addBatchGenerateButton() {
        // 先移除已存在的批量生成按钮（如果有的话）
        Component[] components = chatPanel.getComponents();
        for (Component component : components) {
            if (component instanceof JPanel && component.getName() != null && component.getName().equals("batchGeneratePanel")) {
                chatPanel.remove(component);
            }
        }
        
        // 创建批量生成按钮面板
        JPanel batchGeneratePanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 16, 16));
        batchGeneratePanel.setName("batchGeneratePanel");
        batchGeneratePanel.setOpaque(false);
        batchGeneratePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        batchGeneratePanel.setBorder(JBUI.Borders.empty(8, 16, 8, 16));
        
        // 创建批量生成按钮
        JButton batchGenerateButton = new JButton("批量生成全部文件");
        batchGenerateButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        batchGenerateButton.setForeground(new JBColor(Color.WHITE, Color.WHITE));
        batchGenerateButton.setBackground(new JBColor(new Color(0x2B5AB8), new Color(0x2B5AB8)));
        batchGenerateButton.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        batchGenerateButton.setFocusPainted(false);
        batchGenerateButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        batchGenerateButton.setOpaque(true);
        
        // 添加鼠标悬停效果
        batchGenerateButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                batchGenerateButton.setBackground(new JBColor(new Color(0x234A94), new Color(0x234A94)));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                batchGenerateButton.setBackground(new JBColor(new Color(0x2B5AB8), new Color(0x2B5AB8)));
            }
        });
        
        batchGenerateButton.addActionListener(e -> batchGenerateAllFiles());
        
        // 设置按钮大小
        Dimension buttonSize = new Dimension(150, 32);
        batchGenerateButton.setPreferredSize(buttonSize);
        
        // 添加到面板
        batchGeneratePanel.add(batchGenerateButton);
        
        // 添加到聊天面板底部
        chatPanel.add(batchGeneratePanel);
        chatPanel.revalidate();
        chatPanel.repaint();
        
        // 滚动到底部
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
    
    private void onMQTTMessage(String message) {
        LOG.info("收到MQTT消息回调: " + message);
        
        // 如果是超时消息，重置状态
        if ("操作超时，请重试".equals(message)) {
            SwingUtilities.invokeLater(() -> {
                isWaitingForGeneration = false;
                sendButton.setEnabled(true);
                sendButton.setText("发送");
                sendButton.setBackground(SEND_BUTTON_COLOR);
                sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
                addAssistantMessage(message);
            });
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                LOG.info("在EDT线程中处理消息...");
                // 如果是在欢迎面板，切换到聊天面板
                if (chatScrollPane.getViewport().getView() == welcomePanel) {
                    LOG.info("从欢迎面板切换到聊天面板");
                    chatScrollPane.setViewportView(chatPanel);
                }
                
                // 添加助手消息
                LOG.info("开始添加助手消息");
                addAssistantMessage(message);
                LOG.info("助手消息添加完成");
                
                // 检查是否是"所有代码均已生成"消息或错误消息
                if ("所有代码均已生成".equals(message.trim()) || 
                    (message.contains("ERROR") || message.contains("错误") || message.contains("失败"))) {
                    LOG.info("收到完成或错误消息，恢复发送按钮状态");
                    // 恢复发送按钮状态
                    isWaitingForGeneration = false;
                    sendButton.setEnabled(true);
                    sendButton.setText("发送");
                    sendButton.setBackground(SEND_BUTTON_COLOR);
                    sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
                    
                    // 如果是成功完成的消息，检查是否需要显示批量生成按钮
                    if ("所有代码均已生成".equals(message.trim())) {
                        boolean hasJavaCode = chatMessages.stream()
                            .filter(msg -> !msg.isUser())
                            .anyMatch(msg -> {
                                String content = msg.getContent();
                                return content != null && 
                                       (content.contains("class ") || 
                                        content.contains("interface ") || 
                                        content.contains("enum ")) &&
                                       content.trim().startsWith("package ");
                            });
                        
                        if (hasJavaCode) {
                            addBatchGenerateButton();
                        }
                    }
                }
                
                LOG.info("消息处理完成");
            } catch (Exception e) {
                LOG.error("处理MQTT消息时出错", e);
                // 发生错误时重置状态
                isWaitingForGeneration = false;
                sendButton.setEnabled(true);
                sendButton.setText("发送");
                sendButton.setBackground(SEND_BUTTON_COLOR);
                sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
                addAssistantMessage("消息处理出错，请重试");
            }
        });
    }
    
    private void batchGenerateAllFiles() {
        if (!authService.isLoggedIn()) {
            JOptionPane.showMessageDialog(this, "请先登录", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 确认是否要批量生成
        int result = JOptionPane.showConfirmDialog(
            this,
            "确定要批量生成所有未生成的Java文件吗？",
            "批量生成确认",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        
        // 获取当前选中的目录
        VirtualFile targetDir = CodeGenerationService.getInstance(project).getCurrentSelectedDirectory();
        if (targetDir == null) {
            JOptionPane.showMessageDialog(
                this,
                "请在项目视图中选择一个目标目录",
                "提示",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        
        // 统计成功和失败的数量
        int[] successCount = {0};
        int[] failCount = {0};
        
        // 遍历所有消息，找出包含Java代码的消息
        for (ChatMessage message : chatMessages) {
            if (!message.isUser() && !message.isGenerated()) {
                String content = message.getContent();
                if (content != null && (content.contains("class ") || content.contains("interface ") || content.contains("enum "))) {
                    if (CodeGenerationService.getInstance(project).generateJavaFile(content, false, targetDir)) {
                        successCount[0]++;
                        message.setGenerated(true);
                    } else {
                        failCount[0]++;
                    }
                }
            }
        }
        
        // 显示结果
        String resultMessage = String.format(
            "批量生成完成：\n成功：%d个文件\n失败：%d个文件",
            successCount[0],
            failCount[0]
        );
        
        JOptionPane.showMessageDialog(
            this,
            resultMessage,
            "批量生成结果",
            JOptionPane.INFORMATION_MESSAGE
        );
        
        // 刷新UI
        updateChatDisplay();
    }
    
    // 内部类：聊天消息
    private static class ChatMessage {
        private final String content;
        private final boolean isUser;
        private Editor editor;
        private boolean generated; // 添加生成状态标记
        
        public ChatMessage(String content, boolean isUser) {
            this.content = content;
            this.isUser = isUser;
            this.generated = false;
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
        
        public boolean isGenerated() {
            return generated;
        }
        
        public void setGenerated(boolean generated) {
            this.generated = generated;
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
    
    // 新增方法：设置MQTT回调
    private void setMqttCallback() {
        if (mqttService != null) {
            LOG.info("开始设置MQTT回调函数");
            mqttService.setMessageCallback(this::onMQTTMessage);
            LOG.info("MQTT回调函数设置完成");
        } else {
            LOG.error("MQTT服务实例为空，无法设置回调");
        }
    }
    
    /**
     * 圆角边框类，用于实现通义灵码风格的输入框
     */
    private static class RoundedBorder extends AbstractBorder {
        private final int radius;
        private final Color borderColor;
        private final int borderWidth;
        
        public RoundedBorder(int radius, Color borderColor, int borderWidth) {
            this.radius = radius;
            this.borderColor = borderColor;
            this.borderWidth = borderWidth;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(borderColor);
            g2d.setStroke(new BasicStroke(borderWidth));
            
            // 绘制圆角矩形边框
            g2d.drawRoundRect(x + borderWidth/2, y + borderWidth/2, 
                             width - borderWidth, height - borderWidth, radius, radius);
            
            g2d.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(borderWidth + 2, borderWidth + 2, borderWidth + 2, borderWidth + 2);
        }
    }
} 