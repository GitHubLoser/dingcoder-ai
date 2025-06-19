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
    private JTextField inputField;
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
        // 发送按钮状态：登录状态 && 不在等待生成状态
        boolean canSend = isLoggedIn && !isWaitingForGeneration;
        sendButton.setEnabled(canSend);
        if (canSend) {
            sendButton.setText("发送");
        } else if (isWaitingForGeneration) {
            sendButton.setText("生成中...");
        }
        
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
            
            // 重置等待状态
            isWaitingForGeneration = false;
            
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
        
        // 如果正在等待生成，不允许发送新消息
        if (isWaitingForGeneration) {
            return;
        }
        
        // 获取当前选中的目录路径
        VirtualFile selectedDir = CodeGenerationService.getInstance(project).getCurrentSelectedDirectory();
        String filePath = selectedDir != null ? selectedDir.getPath() : "";
        
        // 清空输入框
        inputField.setText("");
        inputField.setForeground(JBColor.GRAY);
        inputField.setText("输入API名称或者校验器名称");
        
        // 添加用户消息
        addUserMessage(message);
        
        // 添加AI回复
        addAssistantMessage("正在为您生成代码，请稍候...");
        
        // 调用验证规格API
        validateSpecService.callValidateSpecApi(message, filePath);
        
        // 禁用发送按钮
        isWaitingForGeneration = true;
        sendButton.setEnabled(false);
        sendButton.setText("生成中...");
        
        LOG.info("已调用验证规格API，等待生成完成");
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
        
        // 如果有Java代码消息，添加批量生成按钮
        boolean hasJavaCode = chatMessages.stream()
            .filter(msg -> !msg.isUser())
            .anyMatch(msg -> {
                String content = msg.getContent();
                return content != null && (content.contains("class ") || content.contains("interface ") || content.contains("enum "));
            });
        
        if (hasJavaCode) {
            addBatchGenerateButton();
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
        outerPanel.setBorder(JBUI.Borders.empty(6, 0, 6, 0));

        String content = message.getContent();
        boolean isSpecialMessage = "正在为您生成代码，请稍候...".equals(content.trim()) || 
                                 "所有代码均已生成".equals(content.trim());

        if (isSpecialMessage) {
            // 特殊消息：左对齐淡蓝色气泡
            JPanel bubble = new JPanel();
            bubble.setOpaque(true);
            bubble.setBackground(new JBColor(new Color(232, 244, 253), new Color(40, 50, 60)));
            bubble.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16)); // 添加左右padding
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.X_AXIS));
            JLabel label = new JLabel(content);
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            label.setForeground(new JBColor(new Color(33, 150, 243), new Color(120, 170, 255)));
            bubble.add(label);
            outerPanel.add(bubble);
            outerPanel.add(Box.createHorizontalGlue()); // 添加弹性空间使其左对齐
            return outerPanel;
        }

        if (message.isUser()) {
            // 用户消息：靠右蓝色圆角气泡
            outerPanel.add(Box.createHorizontalGlue());
            JPanel bubble = new JPanel();
            bubble.setOpaque(true);
            bubble.setBackground(new JBColor(new Color(16, 142, 233), new Color(52, 139, 255)));
            bubble.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0)); // 只上下padding
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.X_AXIS));
            JLabel label = new JLabel("<html>" + content.replace("\n", "<br>") + "</html>");
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            label.setForeground(Color.WHITE);
            bubble.add(label);
            outerPanel.add(bubble);
        } else if (content.trim().startsWith("package ") || content.trim().contains("class ") || content.trim().contains("interface ") || content.trim().contains("enum ")) {
            // 代码消息：创建一个包含编辑器和按钮的容器
            JPanel codeContainer = new JPanel(new BorderLayout());
            codeContainer.setOpaque(false);

            // 创建代码编辑器
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
            
            // 创建一个面板来包含编辑器和生成按钮
            JPanel editorWithButtonPanel = new JPanel(new BorderLayout());
            editorWithButtonPanel.setOpaque(false);
            
            // 创建生成按钮
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
            
            // 添加鼠标悬停效果
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
            
            // 创建按钮容器并设置为左上角
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
            buttonPanel.setOpaque(false);
            buttonPanel.add(generateButton);
            
            // 将编辑器和按钮添加到面板
            editorWithButtonPanel.add(editor.getComponent(), BorderLayout.CENTER);
            editorWithButtonPanel.add(buttonPanel, BorderLayout.NORTH);
            
            message.setEditor(editor);
            outerPanel.add(editorWithButtonPanel);
        } else {
            // AI普通消息：靠左灰色圆角气泡
            JPanel bubble = new JPanel();
            bubble.setOpaque(true);
            bubble.setBackground(new JBColor(new Color(245, 247, 250), new Color(60, 60, 60)));
            bubble.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0)); // 只上下padding
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
    
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        button.setForeground(JBColor.WHITE);
        button.setBackground(bgColor);
        button.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        // 添加悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.darker());
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
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
        // 检查是否已经添加了批量生成按钮
        Component[] components = chatPanel.getComponents();
        for (Component component : components) {
            if (component instanceof JPanel && component.getName() != null && component.getName().equals("batchGeneratePanel")) {
                return; // 按钮已存在，不重复添加
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
        SwingUtilities.invokeLater(() -> {
            try {
                LOG.info("在EDT线程中处理消息...");
                // 如果是在欢迎面板，切换到聊天面板
                if (chatScrollPane.getViewport().getView() == welcomePanel) {
                    LOG.info("从欢迎面板切换到聊天面板");
                    chatScrollPane.setViewportView(chatPanel);
                }
                
                // 检查是否是"所有代码均已生成"消息
                if ("所有代码均已生成".equals(message.trim())) {
                    LOG.info("收到生成完成消息，恢复发送按钮");
                    // 恢复发送按钮状态
                    isWaitingForGeneration = false;
                    sendButton.setEnabled(true);
                    sendButton.setText("发送");
                }
                
                // 添加助手消息
                LOG.info("添加助手消息到面板");
                addAssistantMessage(message);
                
                // 添加批量生成按钮（如果有Java代码）
                if (message.contains("class ") || message.contains("interface ") || message.contains("enum ")) {
                    addBatchGenerateButton();
                }
                
                LOG.info("消息添加完成");
            } catch (Exception e) {
                LOG.error("处理MQTT消息时出错", e);
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
} 