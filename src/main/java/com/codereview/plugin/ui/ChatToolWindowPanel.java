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
import java.awt.event.ActionListener;

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

    private static ChatToolWindowPanel instance;

    public static ChatToolWindowPanel getInstance() {
        return instance;
    }

    private final Project project;
    private final AuthService authService;
    private final AIService aiService;
    private final MQTTService mqttService;
    private final ValidateSpecService validateSpecService;

    // UI组件
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
        instance = this;

        LOG.info("创建ChatToolWindowPanel实例");

        initializeUI();
        updateUIState();

        // 确保MQTT消息回调总是被设置
        LOG.info("设置MQTT消息回调");
        setMqttCallback();
    }

    private void initializeUI() {
        setBackground(BACKGROUND_COLOR);

        // 创建聊天区域
        createChatArea();

        // 创建输入区域
        createInputArea();

        // 创建欢迎面板
        createWelcomePanel();

        // 添加组件到主面板
        add(chatScrollPane, BorderLayout.CENTER);
        add(createInputPanel(), BorderLayout.SOUTH);
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

        // Digiwin风格AI能量圈SVG，直接加载资源文件
        JLabel svgLabel = new JLabel();
        svgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        svgLabel.setPreferredSize(new Dimension(80, 80));
        try {
            // IntelliJ平台推荐用IconLoader加载SVG
            javax.swing.Icon icon = com.intellij.openapi.util.IconLoader.getIcon("/icons/digiwin-ai-circle.svg", getClass());
            svgLabel.setIcon(icon);
        } catch (Exception ex) {
            svgLabel.setText("D");
        }
        centerPanel.add(Box.createVerticalStrut(8));
        centerPanel.add(svgLabel);
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
        bigLoginButton.setEnabled(false);

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
        inputField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

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
            bigLoginButton.setVisible(true);
            bigLoginButton.setText("登录后可使用");
            chatScrollPane.setViewportView(welcomePanel);
        }

        // 更新输入区域状态
        inputField.setEnabled(isLoggedIn);
        // 输入框可编辑状态：登录状态 && 不在等待生成状态
        inputField.setEditable(isLoggedIn && !isWaitingForGeneration);
        
        // 登录后延迟解锁发送按钮，避免race condition
        if (isLoggedIn && isWaitingForGeneration == false) {
            sendButton.setEnabled(false);
            new javax.swing.Timer(300, e -> {
                sendButton.setEnabled(true);
                sendButton.setText("发送");
                sendButton.setBackground(SEND_BUTTON_COLOR);
                sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
                ((javax.swing.Timer) e.getSource()).stop();
            }).start();
        } else {
            // 发送按钮状态：登录状态 && 不在等待生成状态
            boolean canSend = isLoggedIn && !isWaitingForGeneration;
            sendButton.setEnabled(canSend);
            // 更新发送按钮样式
            if (canSend) {
                sendButton.setText("发送");
                sendButton.setBackground(SEND_BUTTON_COLOR);
                sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
            } else if (isWaitingForGeneration) {
                sendButton.setText("停止生成");
                sendButton.setBackground(SEND_BUTTON_DISABLED_COLOR);
                sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_DISABLED_COLOR, 0));
            } else {
                sendButton.setText("发送");
                sendButton.setBackground(SEND_BUTTON_DISABLED_COLOR);
                sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_DISABLED_COLOR, 0));
            }
        }

        revalidate();
        repaint();
    }



    private void onSendMessage(ActionEvent e) {
        String input = inputField.getText().trim();
        if (input.isEmpty() || isWaitingForGeneration) {
            return;
        }
        if (!authService.isLoggedIn()) {
            JOptionPane.showMessageDialog(this, "登录状态未同步，请退出再试", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 检查当前选择的路径是否正确
        if (!checkSelectedPath()) {
            return; // 路径检查失败，不继续执行
        }
        
        addUserMessage(input);
        inputField.setText("");
        isWaitingForGeneration = true;
        
        // 设置输入框不可编辑
        inputField.setEditable(false);
        
        updateSendButtonForCancel();
        addAssistantMessage("正在为您生成，请稍候");
        
        // 立即切换到聊天面板
        if (chatScrollPane.getViewport().getView() == welcomePanel) {
            chatScrollPane.setViewportView(chatPanel);
        }
        
        VirtualFile selectedDir = CodeGenerationService.getInstance(project).getCurrentSelectedDirectory();
        String filePath = selectedDir != null ? selectedDir.getPath() : "";
        validateSpecService.callValidateSpecApi(input, filePath);
    }

    private void updateSendButtonForCancel() {
        if (!isWaitingForGeneration) {
            updateSendButtonForSend();
            return;
        }
        sendButton.setEnabled(true);
        sendButton.setText("停止生成");
        sendButton.setBackground(SEND_BUTTON_DISABLED_COLOR);
        sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_DISABLED_COLOR, 0));
        for (ActionListener l : sendButton.getActionListeners()) {
            sendButton.removeActionListener(l);
        }
        sendButton.addActionListener(e -> onCancelGeneration());
    }

    private void onCancelGeneration() {
        isWaitingForGeneration = false;
        
        // 恢复输入框可编辑状态
        inputField.setEditable(true);
        
        updateSendButtonForSend();
    }

    private void updateSendButtonForSend() {
        sendButton.setEnabled(true);
        sendButton.setText("发送");
        sendButton.setBackground(SEND_BUTTON_COLOR);
        sendButton.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
        for (ActionListener l : sendButton.getActionListeners()) {
            sendButton.removeActionListener(l);
        }
        sendButton.addActionListener(this::onSendMessage);
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
        boolean isSpecialMessage = "正在为您生成，请稍候".equals(content.trim()) ||
                                 "所有代码均已生成".equals(content.trim());

        if (isSpecialMessage) {
            JPanel bubble = new JPanel();
            bubble.setOpaque(true);
            bubble.setBackground(new JBColor(new Color(232, 244, 253), new Color(40, 50, 60)));
            bubble.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.X_AXIS));
            JLabel label = new JLabel(content);
            label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            label.setForeground(new JBColor(new Color(33, 150, 243), new Color(120, 170, 255)));
            bubble.add(label);
            outerPanel.add(bubble);
            outerPanel.add(Box.createHorizontalGlue());
            return outerPanel;
        }

        if (message.isUser()) {
            outerPanel.add(Box.createHorizontalGlue());
            JPanel bubble = new JPanel();
            bubble.setOpaque(true);
            bubble.setBackground(new JBColor(new Color(243, 244, 246), new Color(50, 54, 60)));
            bubble.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.X_AXIS));
            JLabel label = new JLabel("<html>" + content.replace("\n", "<br>") + "</html>");
            label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            label.setForeground(new JBColor(new Color(31, 35, 40), new Color(230, 237, 243)));
            bubble.add(label);
            outerPanel.add(bubble);
            return outerPanel;
        }

        // 判断是否为代码（包含package声明或类定义）
        boolean isCode = isJavaCode(content);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        if (isCode) {
            // 代码消息 - 默认折叠显示
            JPanel codePanel = new JPanel(new BorderLayout());
            codePanel.setOpaque(false);
            
            // 代码内容区域
            JTextArea codeArea = new JTextArea();
            codeArea.setEditable(false);
            codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            codeArea.setLineWrap(true);
            codeArea.setWrapStyleWord(true);
            codeArea.setText(content);
            codeArea.setBackground(new JBColor(new Color(251, 252, 253), new Color(40, 40, 40)));
            codeArea.setBorder(JBUI.Borders.empty(12, 16, 12, 16));
            JScrollPane codeScroll = new JScrollPane(codeArea);
            codeScroll.setBorder(BorderFactory.createLineBorder(new JBColor(new Color(230, 230, 230), new Color(70, 70, 70)), 1));
            codeScroll.setVisible(false); // 默认折叠
            
            // 设置滚动条属性，确保代码很长时可以滚动
            codeScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            codeScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            codeScroll.setPreferredSize(new Dimension(0, 300)); // 设置最大高度为300px
            codeScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

            // 折叠标题栏 - 包含类名和所有按钮
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setOpaque(true);
            headerPanel.setBackground(new JBColor(new Color(247, 248, 250), new Color(50, 50, 50)));
            headerPanel.setBorder(JBUI.Borders.empty(8, 16, 8, 16));

            // 左侧：折叠按钮和类名
            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            leftPanel.setOpaque(false);

            JButton toggleBtn = new JButton("▶");  // 默认折叠，用右箭头
            toggleBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            toggleBtn.setFocusPainted(false);
            toggleBtn.setBorderPainted(false);
            toggleBtn.setContentAreaFilled(false);
            toggleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            toggleBtn.addActionListener(e -> {
                boolean expanded = codeScroll.isVisible();
                codeScroll.setVisible(!expanded);
                toggleBtn.setText(expanded ? "▶" : "▼");
                panel.revalidate();
                panel.repaint();
            });

            String className = extractClassName(content);
            JLabel summaryLabel = new JLabel("  " + className);  // 添加一点间距
            summaryLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            summaryLabel.setForeground(JBColor.foreground());

            leftPanel.add(toggleBtn);
            leftPanel.add(summaryLabel);

            // 右侧：操作按钮
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));  // 完全去除按钮间距
            rightPanel.setOpaque(false);
            
            // 生成文件按钮
            JButton generateButton = new JButton("生成文件");
            generateButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));  // 字体稍小
            generateButton.setForeground(Color.WHITE);
            generateButton.setBackground(new JBColor(new Color(0x2B5AB8), new Color(0x2B5AB8)));
            generateButton.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));  // 减少内边距
            generateButton.setFocusPainted(false);
            generateButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            generateButton.setOpaque(true);
            generateButton.addActionListener(e -> {
                CodeGenerationService codeGenService = CodeGenerationService.getInstance(project);
                if (codeGenService.generateJavaFile(content, true)) {
                    generateButton.setText("✓ 已生成");
                    generateButton.setEnabled(false);
                    generateButton.setBackground(new JBColor(new Color(0x28A745), new Color(0x28A745)));
                    
                    // 记录代码生成统计
                    recordCodeGenerationEvent(content, className, true);

                    // 写入msgMapping到多语言文件
                    try {
                        java.util.Map<String, String> mappingMap = com.codereview.plugin.service.MQTTService.getInstance().getCodeGenerationMsgMappingMap();
                        LOG.info("[多语言] 当前msgMapping Map内容: " + mappingMap);
                        if (mappingMap != null && !mappingMap.isEmpty()) {
                            for (java.util.Map.Entry<String, String> entry : mappingMap.entrySet()) {
                                String key = entry.getKey();
                                String value = entry.getValue();
                                LOG.info("[多语言] 写入 key=" + key + ", value=" + value);
                                com.codereview.plugin.GenerateMessageMappingService.writeUnicodeProperties(key, value);
                            }
                        } else {
                            LOG.info("[多语言] 未检测到msgMapping内容，无需写入");
                        }
                    } catch (Exception ex) {
                        LOG.error("[多语言] 写入多语言文件失败", ex);
                    }
                    // 发送统计接口 type=0
                    LOG.info("[统计] 即将上报 codeId=" + (message instanceof ChatMessage ? ((ChatMessage)message).getUuid() : "null") + ", userName=" + com.codereview.plugin.auth.AuthService.getInstance().getCurrentUser() + ", className=" + className + ", type=0");
                    com.codereview.plugin.service.CodeGenerationStatisticsService.getInstance().sendStatistics(
                        message instanceof ChatMessage ? ((ChatMessage)message).getUuid() : java.util.UUID.randomUUID().toString(),
                        com.codereview.plugin.auth.AuthService.getInstance().getCurrentUser(),
                        className,
                        "0",
                        null,
                        null
                    );
                    LOG.info("[统计] sendStatistics已调用完成（type=0）");
                }
            });
            
            // 点赞按钮
            JButton likeButton = new JButton("👍");
            likeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));  // 字体稍小
            likeButton.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));  // 进一步减少内边距
            likeButton.setFocusPainted(false);
            likeButton.setContentAreaFilled(false);
            likeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            likeButton.addActionListener(e -> {
                String feedback = showFeedbackDialog("点赞反馈", "请告诉我们您喜欢这个回答的原因：");
                if (feedback != null) {
                    LOG.info("用户点赞反馈: " + feedback);
                    likeButton.setText("👍");
                    likeButton.setEnabled(false);
                    likeButton.setForeground(new JBColor(new Color(0x28A745), new Color(0x28A745)));
                    
                    // 记录用户反馈统计
                    recordUserFeedbackEvent(content, "LIKE", feedback);
                    // 发送统计接口 type=1
                    LOG.info("[统计] 即将上报 codeId=" + (message instanceof ChatMessage ? ((ChatMessage)message).getUuid() : "null") + ", userName=" + com.codereview.plugin.auth.AuthService.getInstance().getCurrentUser() + ", className=" + className + ", type=1, feedbackType=LIKE, feedbackContent=" + feedback);
                    com.codereview.plugin.service.CodeGenerationStatisticsService.getInstance().sendStatistics(
                        message instanceof ChatMessage ? ((ChatMessage)message).getUuid() : java.util.UUID.randomUUID().toString(),
                        com.codereview.plugin.auth.AuthService.getInstance().getCurrentUser(),
                        className,
                        "1",
                        "LIKE",
                        feedback
                    );
                    LOG.info("[统计] sendStatistics已调用完成（type=1, LIKE）");
                }
            });
            
            // 点踩按钮
            JButton dislikeButton = new JButton("👎");
            dislikeButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));  // 字体稍小
            dislikeButton.setBorder(BorderFactory.createEmptyBorder(3, 2, 3, 2));  // 进一步减少内边距
            dislikeButton.setFocusPainted(false);
            dislikeButton.setContentAreaFilled(false);
            dislikeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dislikeButton.addActionListener(e -> {
                String feedback = showFeedbackDialog("改进建议", "请告诉我们您认为需要改进的地方：");
                if (feedback != null) {
                    LOG.info("用户点踩反馈: " + feedback);
                    dislikeButton.setText("👎");
                    dislikeButton.setEnabled(false);
                    dislikeButton.setForeground(new JBColor(new Color(0xDC3545), new Color(0xDC3545)));
                    
                    // 记录用户反馈统计
                    recordUserFeedbackEvent(content, "DISLIKE", feedback);
                    // 发送统计接口 type=1
                    LOG.info("[统计] 即将上报 codeId=" + (message instanceof ChatMessage ? ((ChatMessage)message).getUuid() : "null") + ", userName=" + com.codereview.plugin.auth.AuthService.getInstance().getCurrentUser() + ", className=" + className + ", type=1, feedbackType=DISLIKE, feedbackContent=" + feedback);
                    com.codereview.plugin.service.CodeGenerationStatisticsService.getInstance().sendStatistics(
                        message instanceof ChatMessage ? ((ChatMessage)message).getUuid() : java.util.UUID.randomUUID().toString(),
                        com.codereview.plugin.auth.AuthService.getInstance().getCurrentUser(),
                        className,
                        "1",
                        "DISLIKE",
                        feedback
                    );
                    LOG.info("[统计] sendStatistics已调用完成（type=1, DISLIKE）");
                }
            });

            rightPanel.add(generateButton);
            rightPanel.add(likeButton);
            rightPanel.add(dislikeButton);

            headerPanel.add(leftPanel, BorderLayout.WEST);
            headerPanel.add(rightPanel, BorderLayout.EAST);

            panel.add(headerPanel);
            panel.add(codeScroll);
        } else {
            // 非代码消息 - 直接显示文本
            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(true);
            textPanel.setBackground(new JBColor(new Color(240, 242, 247), new Color(60, 60, 60)));
            textPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
            
            JLabel textLabel = new JLabel("<html>" + content.replace("\n", "<br>") + "</html>");
            textLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            textLabel.setForeground(new JBColor(new Color(31, 35, 40), new Color(230, 237, 243)));
            textLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            textPanel.add(textLabel);
            panel.add(textPanel);
        }

        outerPanel.add(panel);
        return outerPanel;
    }

    /**
     * 判断内容是否为Java代码
     */
    private boolean isJavaCode(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // 检查是否包含Java关键特征
        return content.contains("package ") || 
               content.contains("public class ") || 
               content.contains("class ") ||
               content.contains("public interface ") || 
               content.contains("interface ") ||
               content.contains("public enum ") || 
               content.contains("enum ") ||
               (content.contains("import ") && content.contains("public "));
    }

    /**
     * 提取Java类名
     */
    private String extractClassName(String content) {
        if (content == null) return "Java代码.java";
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            String trim = line.trim();
            // 查找类声明
            if (trim.startsWith("public class ") || trim.startsWith("class ")) {
                return extractNameFromLine(trim, "class");
            } 
            // 查找接口声明
            else if (trim.startsWith("public interface ") || trim.startsWith("interface ")) {
                return extractNameFromLine(trim, "interface");
            } 
            // 查找枚举声明
            else if (trim.startsWith("public enum ") || trim.startsWith("enum ")) {
                return extractNameFromLine(trim, "enum");
            }
        }
        return "Java代码.java";
    }

    /**
     * 从声明行中提取名称
     */
    private String extractNameFromLine(String line, String keyword) {
        try {
            String[] parts = line.split("\\b" + keyword + "\\b");
            if (parts.length > 1) {
                String namepart = parts[1].trim();
                // 获取第一个单词作为类名
                String[] words = namepart.split("[\\s\\{<]");
                if (words.length > 0 && !words[0].trim().isEmpty()) {
                    return words[0].trim() + ".java";
                }
            }
        } catch (Exception e) {
            // 如果解析失败，返回默认值
        }
        return "Java代码.java";
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(JBColor.foreground());
        button.setBorder(JBUI.Borders.empty(8, 16));
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
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
        button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        // 设置按钮样式
        button.setBackground(SEND_BUTTON_COLOR);
        button.setForeground(JBColor.foreground());
        button.setBorder(new RoundedBorder(6, SEND_BUTTON_COLOR, 0));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // 添加点击事件处理
        button.addActionListener(this::onSendMessage);

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

    /**
     * 显示编辑器的右键菜单
     */
    private void showEditorContextMenu(MouseEvent e, Editor editor, String content) {
        JPopupMenu popup = new JPopupMenu();

        // 复制选中内容菜单项
        JMenuItem copySelectedItem = new JMenuItem("复制选中内容");
        copySelectedItem.addActionListener(evt -> {
            String selectedText = editor.getSelectionModel().getSelectedText();
            if (selectedText != null && !selectedText.isEmpty()) {
                copyToClipboard(selectedText);
            }
        });
        popup.add(copySelectedItem);

        // 复制全部内容菜单项
        JMenuItem copyAllItem = new JMenuItem("复制全部代码");
        copyAllItem.addActionListener(evt -> {
            copyToClipboard(content);
        });
        popup.add(copyAllItem);

        // 显示菜单
        popup.show(editor.getComponent(), e.getX(), e.getY());
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
        JButton batchGenerateButton = new JButton("生成全部文件");
        batchGenerateButton.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        batchGenerateButton.setForeground(JBColor.foreground());
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

        // 检查是否有msgMapping（表示这是新格式的代码消息）
        // 只保留Map逻辑，msgMapping字符串已废弃
        java.util.Map<String, String> mappingMap = mqttService.getCodeGenerationMsgMappingMap();
        if (mappingMap != null && !mappingMap.isEmpty()) {
            LOG.info("检测到msgMapping，这是新格式代码消息: " + mappingMap);
            // 清除已使用的msgMapping
            mqttService.clearCodeGenerationMsgMapping();
            // 后续可以根据msgMapping做特殊处理
        }

        // 如果是超时消息，重置状态
        if ("操作超时，请重试".equals(message)) {
            SwingUtilities.invokeLater(() -> {
                isWaitingForGeneration = false;
                updateSendButtonForSend();
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

                // 检查是否是"所有代码均已生成"消息
                if ("所有代码均已生成".equals(message.trim())) {
                    LOG.info("收到代码生成完成消息，恢复发送按钮状态");
                    // 恢复发送按钮状态和输入框可编辑状态
                    isWaitingForGeneration = false;
                    inputField.setEditable(true);
                    updateSendButtonForSend();

                    // 检查是否有Java代码消息，如果有则显示批量生成按钮
                    boolean hasJavaCode = chatMessages.stream()
                        .filter(msg -> !msg.isUser())
                        .anyMatch(msg -> isJavaCode(msg.getContent()));

                    if (hasJavaCode) {
                        addBatchGenerateButton();
                    }
                }

                LOG.info("消息处理完成");
            } catch (Exception e) {
                LOG.error("处理MQTT消息时出错", e);
                // 发生错误时重置状态
                isWaitingForGeneration = false;
                updateSendButtonForSend();
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
                if (isJavaCode(content)) {
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
        private final String uuid = java.util.UUID.randomUUID().toString();

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

        public String getUuid() { return uuid; }
    }

    /**
     * 释放资源
     */
    public void dispose() {
        try {
            // 释放所有编辑器
            for (ChatMessage message : chatMessages) {
                if (!message.isUser() && message.getEditor() != null) {
                    try {
                        EditorFactory.getInstance().releaseEditor(message.getEditor());
                    } catch (Exception e) {
                        LOG.error("释放编辑器时出错", e);
                    }
                }
            }

            // 清空消息
            chatMessages.clear();

            // 清理UI组件
            if (chatPanel != null) {
                chatPanel.removeAll();
            }

            // 不再清理MQTT回调
            // try {
            //     MQTTService mqttService = MQTTService.getInstance();
            //     if (mqttService != null) {
            //         mqttService.setMessageCallback(MQTTService.FUNCTION_CODE_GENERATION, null);
            //     }
            // } catch (Exception e) {
            //     LOG.error("清理MQTT回调时出错", e);
            // }

            LOG.info("ChatToolWindowPanel disposed");
        } catch (Exception e) {
            LOG.error("ChatToolWindowPanel dispose时出错", e);
        }
    }

    // 新增方法：设置MQTT回调
    private void setMqttCallback() {
        if (mqttService != null) {
            LOG.info("开始设置代码生成MQTT回调函数");
            mqttService.setMessageCallback(MQTTService.FUNCTION_CODE_GENERATION, this::onMQTTMessage);
            LOG.info("强制设置code_generation MQTT回调函数为当前实例");
        } else {
            LOG.error("MQTT服务实例为空，无法设置回调");
        }
    }

    // 新增方法：确保code_generation回调已注册
    public void ensureCodeGenerationMqttCallback() {
        if (mqttService != null && mqttService.isConnected()) {
            Consumer<String> currentCallback = mqttService.getMessageCallback(MQTTService.FUNCTION_CODE_GENERATION);
            if (currentCallback == null) {
                LOG.info("检测到code_generation回调未设置，重新设置");
                mqttService.setMessageCallback(MQTTService.FUNCTION_CODE_GENERATION, this::onMQTTMessage);
                LOG.info("code_generation MQTT回调函数重新设置完成");
            } else {
                LOG.info("code_generation MQTT回调函数已设置");
            }
        } else {
            LOG.warn("MQTT服务未连接，无法设置code_generation回调");
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

    /**
     * 清空面板内容（退出登录时调用）
     */
    public void clearPanelContent() {
        try {
            LOG.info("开始清空代码生成面板内容");

            // 释放所有编辑器
            for (ChatMessage message : chatMessages) {
                if (!message.isUser() && message.getEditor() != null) {
                    try {
                        EditorFactory.getInstance().releaseEditor(message.getEditor());
                    } catch (Exception e) {
                        LOG.error("释放编辑器时出错", e);
                    }
                }
            }

            // 清空消息列表
            chatMessages.clear();

            // 清空UI组件
            if (chatPanel != null) {
                chatPanel.removeAll();
                chatPanel.revalidate();
                chatPanel.repaint();
            }

            // 重置发送按钮状态
            isWaitingForGeneration = false;
            updateSendButtonForSend();

            // 清空输入框
            if (inputField != null) {
                inputField.setText("");
            }

            // 重新显示欢迎面板
            if (welcomePanel != null) {
                chatScrollPane.setViewportView(welcomePanel);
            }

            LOG.info("代码生成面板内容清空完成");
        } catch (Exception e) {
            LOG.error("清空代码生成面板内容时出错", e);
        }
    }

    /**
     * 显示反馈对话框
     */
    private String showFeedbackDialog(String title, String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));
        
        // 提示信息
        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        panel.add(messageLabel, BorderLayout.NORTH);
        
        // 输入区域
        JTextArea feedbackArea = new JTextArea(4, 30);
        feedbackArea.setLineWrap(true);
        feedbackArea.setWrapStyleWord(true);
        feedbackArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        feedbackArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JScrollPane scrollPane = new JScrollPane(feedbackArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new JBColor(new Color(200, 200, 200), new Color(100, 100, 100)), 1));
        scrollPane.setPreferredSize(new Dimension(350, 100));
        
        // 添加间距
        panel.add(Box.createVerticalStrut(10), BorderLayout.CENTER);
        panel.add(scrollPane, BorderLayout.SOUTH);
        
        int result = JOptionPane.showConfirmDialog(
            this,
            panel,
            title,
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        
        if (result == JOptionPane.OK_OPTION) {
            String feedback = feedbackArea.getText().trim();
            if (!feedback.isEmpty()) {
                JOptionPane.showMessageDialog(
                    this, 
                    "感谢您的反馈！我们会认真考虑您的建议。", 
                    "反馈已提交", 
                    JOptionPane.INFORMATION_MESSAGE
                );
                return feedback;
            }
        }
        
        return null;
    }

    /**
     * 检查当前选择的路径是否正确
     */
    private boolean checkSelectedPath() {
        VirtualFile selectedDir = CodeGenerationService.getInstance(project).getCurrentSelectedDirectory();
        
        if (selectedDir == null) {
            showPathSelectionDialog("未选择目录", 
                "请在项目视图中选择一个Java源码目录，例如：\n" +
                "• src/main/java/com/yourpackage\n" +
                "• src/java/com/yourpackage\n\n" +
                "选择正确的路径后，生成的代码会包含正确的package声明。");
            return false;
        }
        
        String path = selectedDir.getPath();
        LOG.info("当前选择路径: " + path);
        
        // 检查是否在Java源码目录下
        boolean isJavaSourceDir = path.contains("/src/main/java") || path.contains("/src/java") || 
                                  path.contains("\\src\\main\\java") || path.contains("\\src\\java");
        
        if (!isJavaSourceDir) {
            showPathSelectionDialog("路径选择错误", 
                "当前选择的路径不是Java源码目录！\n\n" +
                "当前路径：" + path + "\n\n" +
                "请选择Java源码目录，例如：\n" +
                "• src/main/java/com/yourpackage\n" +
                "• src/java/com/yourpackage\n\n" +
                "这样生成的代码才能包含正确的package声明。");
            return false;
        }
        
        // 检查是否在com包下（推荐但不强制）
        boolean isUnderComPackage = path.contains("/com/") || path.contains("\\com\\");
        
        if (!isUnderComPackage) {
            int result = JOptionPane.showConfirmDialog(
                this,
                "当前路径不在com包下，生成的代码可能没有package声明。\n\n" +
                "当前路径：" + path + "\n\n" +
                "建议选择com包下的目录，例如：\n" +
                "• src/main/java/com/yourpackage\n\n" +
                "是否继续生成？",
                "路径提醒",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            return result == JOptionPane.YES_OPTION;
        }
        
        return true;
    }
    
    /**
     * 显示路径选择对话框
     */
    private void showPathSelectionDialog(String title, String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(15));
        
        // 错误图标和消息
        JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        messagePanel.setOpaque(false);
        
        JLabel iconLabel = new JLabel("⚠️");
        iconLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        iconLabel.setBorder(JBUI.Borders.emptyRight(10));
        
        JLabel messageLabel = new JLabel("<html>" + message.replace("\n", "<br>") + "</html>");
        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        messagePanel.add(iconLabel);
        messagePanel.add(messageLabel);
        panel.add(messagePanel, BorderLayout.CENTER);
        
        // 操作说明
        JPanel instructionPanel = new JPanel(new BorderLayout());
        instructionPanel.setOpaque(false);
        instructionPanel.setBorder(JBUI.Borders.emptyTop(15));
        
        JLabel instructionLabel = new JLabel("<html><b>操作步骤：</b><br>" +
            "1. 在IDEA左侧项目视图中展开项目<br>" +
            "2. 找到 src → main → java → com → 你的包名<br>" +
            "3. 右键点击包名文件夹，选择该目录<br>" +
            "4. 再次点击发送按钮</html>");
        instructionLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        instructionLabel.setForeground(new JBColor(new Color(100, 100, 100), new Color(150, 150, 150)));
        
        instructionPanel.add(instructionLabel, BorderLayout.CENTER);
        panel.add(instructionPanel, BorderLayout.SOUTH);
        
        JOptionPane.showMessageDialog(
            this,
            panel,
            title,
            JOptionPane.WARNING_MESSAGE
        );
    }

    /**
     * 记录代码生成事件
     */
    private void recordCodeGenerationEvent(String codeContent, String className, boolean actuallyGenerated) {
        try {
            String userId = authService.getCurrentUser();
            if (userId == null) {
                LOG.warn("用户未登录，跳过统计事件发送");
                return;
            }

            String codeHash = calculateHash(codeContent);
            
            // 构建统计事件数据
            String eventData = String.format(
                "{\"eventType\":\"CODE_GENERATION\",\"userId\":\"%s\",\"timestamp\":\"%s\",\"data\":{\"codeHash\":\"%s\",\"className\":\"%s\",\"actuallyGenerated\":%b}}",
                userId, java.time.Instant.now().toString(), codeHash, className, actuallyGenerated
            );
            
            // 异步发送统计数据
            sendStatisticsEvent(eventData);
            
        } catch (Exception e) {
            LOG.warn("记录代码生成事件失败", e);
        }
    }

    /**
     * 记录用户反馈事件
     */
    private void recordUserFeedbackEvent(String codeContent, String feedbackType, String feedbackContent) {
        try {
            String userId = authService.getCurrentUser();
            if (userId == null) {
                LOG.warn("用户未登录，跳过统计事件发送");
                return;
            }

            String codeHash = calculateHash(codeContent);
            
            // 转义引号
            String escapedContent = feedbackContent.replace("\"", "\\\"").replace("\n", "\\n");
            
            // 构建统计事件数据
            String eventData = String.format(
                "{\"eventType\":\"USER_FEEDBACK\",\"userId\":\"%s\",\"timestamp\":\"%s\",\"data\":{\"codeHash\":\"%s\",\"feedbackType\":\"%s\",\"feedbackContent\":\"%s\"}}",
                userId, java.time.Instant.now().toString(), codeHash, feedbackType, escapedContent
            );
            
            // 异步发送统计数据
            sendStatisticsEvent(eventData);
            
        } catch (Exception e) {
            LOG.warn("记录用户反馈事件失败", e);
        }
    }

    /**
     * 发送统计事件到后端
     */
    private void sendStatisticsEvent(String eventData) {
        // 这里可以根据实际情况配置统计服务的URL
        // 暂时只记录日志，实际使用时替换为HTTP调用
        LOG.info("统计事件: " + eventData);
        
        // TODO: 实际实现时替换为HTTP请求
        // 示例：
        // String statisticsUrl = "https://your-backend.com/api/statistics/event";
        // HttpClient.newHttpClient().sendAsync(
        //     HttpRequest.newBuilder()
        //         .uri(URI.create(statisticsUrl))
        //         .header("Content-Type", "application/json")
        //         .POST(HttpRequest.BodyPublishers.ofString(eventData))
        //         .build(),
        //     HttpResponse.BodyHandlers.ofString()
        // );
    }

    /**
     * 计算字符串的哈希值
     */
    private String calculateHash(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString().substring(0, 16); // 取前16位作为短哈希
        } catch (Exception e) {
            LOG.warn("计算哈希值失败", e);
            return String.valueOf(content.hashCode());
        }
    }
} 