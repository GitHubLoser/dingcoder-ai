package com.codereview.plugin.ui;

import com.codereview.plugin.service.CodeGenerationService;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.ide.highlighter.JavaFileType;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码消息面板
 * 显示单条从MQTT接收的代码消息，包含确认生成按钮
 */
public class CodeMessagePanel extends JBPanel<CodeMessagePanel> {
    private final String codeText;
    private final Project project;
    private Editor editor;
    private JButton generateButton;
    private JButton copyButton;
    private boolean isGenerated = false;
    
    // 颜色定义
    private static final Color CARD_BACKGROUND = new JBColor(Color.WHITE, new Color(43, 43, 43));
    private static final Color BORDER_COLOR = new JBColor(new Color(230, 230, 230), new Color(70, 70, 70));
    private static final Color TITLE_BACKGROUND = new JBColor(new Color(247, 248, 250), new Color(50, 50, 50));
    private static final Color CODE_BACKGROUND = new JBColor(new Color(251, 252, 253), new Color(40, 40, 40));
    private static final Color SUCCESS_BACKGROUND = new JBColor(new Color(236, 253, 243), new Color(20, 40, 20));
    private static final Color BUTTON_PRIMARY = new JBColor(new Color(22, 119, 255), new Color(52, 139, 255));
    private static final Color BUTTON_SUCCESS = new JBColor(new Color(40, 167, 69), new Color(60, 187, 89));
    
    public CodeMessagePanel(String codeText, Project project) {
        super(new BorderLayout());
        this.codeText = codeText;
        this.project = project;
        
        initializeUI();
        
        // 创建主卡片
        createMainCard();
        
        // 设置面板属性
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        setOpaque(false);
    }
    
    private void initializeUI() {
        setBorder(JBUI.Borders.empty(8));
    }
    
    private void createMainCard() {
        JPanel cardPanel = new JBPanel<>(new BorderLayout());
        cardPanel.setBackground(CARD_BACKGROUND);
        cardPanel.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COLOR, 1, true),
            new EmptyBorder(0, 0, 0, 0)
        ));
        
        // 添加阴影效果
        cardPanel.putClientProperty("JComponent.roundRect", true);
        
        // 创建头部
        JPanel headerPanel = createHeaderPanel();
        cardPanel.add(headerPanel, BorderLayout.NORTH);
        
        // 创建代码内容区域
        JPanel codePanel = createCodePanel();
        cardPanel.add(codePanel, BorderLayout.CENTER);
        
        // 创建底部按钮区域
        JPanel footerPanel = createFooterPanel();
        cardPanel.add(footerPanel, BorderLayout.SOUTH);
        
        add(cardPanel, BorderLayout.CENTER);
    }
    
    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JBPanel<>(new BorderLayout());
        headerPanel.setBackground(TITLE_BACKGROUND);
        headerPanel.setBorder(JBUI.Borders.empty(12, 16, 8, 16));
        
        // 文件图标和标题
        String className = extractClassName(codeText);
        JPanel titlePanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titlePanel.setOpaque(false);
        
        // 文件图标
        JBLabel iconLabel = new JBLabel("☕");
        iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        iconLabel.setBorder(JBUI.Borders.emptyRight(8));
        titlePanel.add(iconLabel);
        
        // 文件名
        JBLabel titleLabel = new JBLabel();
        if (className != null) {
            titleLabel.setText(className + ".java");
            titleLabel.setToolTipText("Java类文件: " + className);
        } else {
            titleLabel.setText("代码片段");
            titleLabel.setToolTipText("Java代码片段");
        }
        Font boldFont = UIUtil.getFont(UIUtil.FontSize.NORMAL, null).deriveFont(Font.BOLD);
        titleLabel.setFont(boldFont);
        titleLabel.setForeground(JBColor.foreground());
        titlePanel.add(titleLabel);
        
        headerPanel.add(titlePanel, BorderLayout.WEST);
        
        // 状态标签
        JBLabel statusLabel = new JBLabel("AI生成");
        Font smallFont = UIUtil.getFont(UIUtil.FontSize.SMALL, null);
        statusLabel.setFont(smallFont);
        statusLabel.setForeground(JBColor.GRAY);
        headerPanel.add(statusLabel, BorderLayout.EAST);
        
        return headerPanel;
    }
    
    private JPanel createCodePanel() {
        JPanel containerPanel = new JBPanel<>(new BorderLayout());
        containerPanel.setBackground(CARD_BACKGROUND);
        containerPanel.setBorder(JBUI.Borders.empty(0, 16, 8, 16));

        // 创建编辑器
        EditorFactory editorFactory = EditorFactory.getInstance();
        Document document = editorFactory.createDocument(formatCodeForDisplay(codeText));
        editor = editorFactory.createEditor(document, project, JavaFileType.INSTANCE, true);

        // 配置编辑器
        EditorEx editorEx = (EditorEx) editor;
        EditorSettings settings = editorEx.getSettings();
        settings.setFoldingOutlineShown(false);
        settings.setLineMarkerAreaShown(false);
        settings.setLineNumbersShown(true);
        settings.setVirtualSpace(false);
        settings.setWheelFontChangeEnabled(false);
        settings.setAdditionalColumnsCount(0);
        settings.setAdditionalLinesCount(0);
        settings.setRightMarginShown(false);
        settings.setShowIntentionBulb(false);
        editorEx.setHorizontalScrollbarVisible(true);
        editorEx.setVerticalScrollbarVisible(true);

        // 设置编辑器背景色
        editorEx.setBackgroundColor(CODE_BACKGROUND);

        // 设置编辑器大小
        editor.getComponent().setPreferredSize(new Dimension(0, 200));

        // 添加编辑器到容器
        containerPanel.add(editor.getComponent(), BorderLayout.CENTER);

        return containerPanel;
    }
    
    private JPanel createFooterPanel() {
        JPanel footerPanel = new JBPanel<>(new BorderLayout());
        footerPanel.setBackground(CARD_BACKGROUND);
        footerPanel.setBorder(JBUI.Borders.empty(8, 16, 12, 16));
        
        // 按钮面板
        JPanel buttonPanel = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        
        // 复制按钮
        copyButton = createStyledButton("📋 复制", BUTTON_PRIMARY);
        copyButton.addActionListener(e -> copyCodeToClipboard());
        buttonPanel.add(copyButton);
        
        // 生成按钮
        generateButton = createStyledButton("✨ 生成Java文件", BUTTON_PRIMARY);
        generateButton.addActionListener(e -> generateJavaFile());
        buttonPanel.add(generateButton);
        
        footerPanel.add(buttonPanel, BorderLayout.EAST);
        
        return footerPanel;
    }
    
    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        Font normalFont = UIUtil.getFont(UIUtil.FontSize.NORMAL, null);
        button.setFont(normalFont);
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setBorder(JBUI.Borders.empty(8, 16));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // 添加悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(bgColor.darker());
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(bgColor);
                }
            }
        });
        
        return button;
    }
    
    private void copyCodeToClipboard() {
        try {
            String code = formatCodeForDisplay(codeText);
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(code), null);
            
            // 临时改变按钮文本表示复制成功
            String originalText = copyButton.getText();
            copyButton.setText("✓ 已复制");
            Timer timer = new Timer(2000, e -> copyButton.setText(originalText));
            timer.setRepeats(false);
            timer.start();
        } catch (Exception e) {
            // 复制失败时显示错误
            JOptionPane.showMessageDialog(this, "复制失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * 生成Java文件
     */
    private void generateJavaFile() {
        CodeGenerationService codeGenService = CodeGenerationService.getInstance(project);
        
        if (codeGenService.generateJavaFile(codeText, true)) {
            markAsGenerated();
        }
    }
    
    /**
     * 标记为已生成
     */
    public void markAsGenerated() {
        isGenerated = true;
        generateButton.setText("✓ 已生成");
        generateButton.setBackground(BUTTON_SUCCESS);
        generateButton.setEnabled(false);
        
        // 改变整个卡片的背景色表示已生成
        Component cardPanel = getComponent(0);
        if (cardPanel instanceof JPanel) {
            cardPanel.setBackground(SUCCESS_BACKGROUND);
        }
    }
    
    /**
     * 获取代码文本
     */
    public String getCodeText() {
        return codeText;
    }
    
    /**
     * 检查是否已生成
     */
    public boolean isGenerated() {
        return isGenerated;
    }
    
    /**
     * 从代码文本中提取类名
     */
    private String extractClassName(String text) {
        // 首先提取Java代码块
        String javaCode = text;
        Pattern codeBlockPattern = Pattern.compile("```java\\s*\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher codeBlockMatcher = codeBlockPattern.matcher(text);
        if (codeBlockMatcher.find()) {
            javaCode = codeBlockMatcher.group(1);
        }
        
        // 提取类名
        Pattern classPattern = Pattern.compile("(?:public\\s+)?(?:class|interface|enum)\\s+(\\w+)");
        Matcher classMatcher = classPattern.matcher(javaCode);
        if (classMatcher.find()) {
            return classMatcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 格式化代码用于显示
     */
    private String formatCodeForDisplay(String codeText) {
        // 如果是代码块格式，提取纯代码
        Pattern pattern = Pattern.compile("```java\\s*\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(codeText);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // 如果不是代码块格式，直接返回
        return codeText.trim();
    }
    
    /**
     * 释放资源
     */
    public void dispose() {
        if (editor != null) {
            EditorFactory.getInstance().releaseEditor(editor);
            editor = null;
        }
    }
} 