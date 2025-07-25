package com.codereview.plugin.auth;

import com.codereview.plugin.ui.ChatToolWindowFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import org.apache.commons.collections.MapUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.Properties;
import java.util.Random;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.swing.JOptionPane;

/**
 * 登录对话框
 */
public class LoginDialog extends DialogWrapper {
    private static final Logger LOG = Logger.getInstance(LoginDialog.class);
    private final JBTextField usernameField;
    private final JBPasswordField passwordField;
    private final AuthService authService;
    private JCheckBox rememberPasswordCheckBox;

    public LoginDialog(@Nullable Project project) {
        super(project);
        this.authService = AuthService.getInstance();
        
        // 初始化用户名和密码输入框
        usernameField = new JBTextField(20);
        passwordField = new JBPasswordField();
        rememberPasswordCheckBox = new JCheckBox("记住密码");
        // 读取本地保存的用户名和密码
        Preferences prefs = Preferences.userRoot().node("dingcoder-ai-login");
        String savedUser = prefs.get("username", "");
        String savedPass = prefs.get("password", "");
        boolean remember = prefs.getBoolean("remember", false);
        usernameField.setText(savedUser);
        if (remember && !savedPass.isEmpty()) {
            passwordField.setText(savedPass);
            rememberPasswordCheckBox.setSelected(true);
        }
        
        setTitle("登录到 鼎码智辅");
        setOKButtonText("登录");
        setCancelButtonText("取消");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBPanel<JBPanel<?>> panel = new JBPanel<>(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // 用户名标签和输入框
        panel.add(new JBLabel("用户名:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(usernameField, gbc);
        
        // 密码标签和输入框
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel("密码:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(passwordField, gbc);
        
        // 记住密码复选框
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(rememberPasswordCheckBox, gbc);
        
        // 提示信息
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(10, 5, 5, 5);
        JBLabel infoLabel = new JBLabel("注意: 使用 '邮箱/密码' 作为登录凭证");
        infoLabel.setForeground(Color.GRAY);
        panel.add(infoLabel, gbc);
        
        // 设置面板尺寸
        panel.setPreferredSize(new Dimension(350, 150));
        
        return panel;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (usernameField.getText().trim().isEmpty()) {
            return new ValidationInfo("请输入用户名", usernameField);
        }
        if (passwordField.getPassword().length == 0) {
            return new ValidationInfo("请输入密码", passwordField);
        }
        return null;
    }

    @Override
    protected void doOKAction() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        boolean remember = rememberPasswordCheckBox.isSelected();
        // 保存用户名和密码到本地
        Preferences prefs = Preferences.userRoot().node("dingcoder-ai-login");
        prefs.put("username", username);
        if (remember) {
            prefs.put("password", password);
        } else {
            prefs.remove("password");
        }
        prefs.putBoolean("remember", remember);
        // 登录中，用户名输入框不可编辑
        usernameField.setEditable(false);
        LOG.info("用户点击登录，用户名: " + username);
        
        // 清除之前的错误提示
        setErrorText(null);
        
        // 显示登录中状态
        setOKActionEnabled(false);
        getOKAction().setEnabled(false);
        getCancelAction().setEnabled(false);
        setTitle("登录中，请稍候...");
        
        // 使用后台线程进行登录，避免阻塞UI
        new Thread(() -> {
            try {
                LOG.info("开始调用authService.login");
                authService.login(username, password);
                LOG.info("authService.login调用完成，isLoggedIn=" + authService.isLoggedIn());
                
                SwingUtilities.invokeLater(() -> {
                    // 检查登录状态
                    if (authService.isLoggedIn()) {
                        LOG.info("登录成功，关闭对话框");
                        // 登录成功 - 关闭对话框
                        // 弹趣味消息
                        try {
                            Properties props = new Properties();
                            InputStream in = getClass().getClassLoader().getResourceAsStream("fun-messages.properties");
                            if (in != null) {
                                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                                in.close();
                                Random rand = new Random();
                                int idx = rand.nextInt(2) + 1; // 目前有2条
                                String msg = props.getProperty("login." + idx);
                                if (msg != null) {
                                    JOptionPane.showMessageDialog(null, msg, "趣味提示", JOptionPane.INFORMATION_MESSAGE);
                                }
                            }
                        } catch (Exception ex) {
                            // ignore
                        }
                        dispose();
                        
                        // 更新主面板状态并显示成功消息
                        SwingUtilities.invokeLater(() -> {
                            ChatToolWindowFactory.updateCurrentPanelStatus();
                            LOG.info("主面板状态已更新");
                            
                            // 显示成功消息
                            String successMessage = "登录成功！欢迎使用鼎码智辅";
                            // JOptionPane.showMessageDialog(
                            //     null,
                            //     successMessage,
                            //     "登录成功",
                            //     JOptionPane.INFORMATION_MESSAGE
                            // );
                        });
                    } else {
                        LOG.warn("登录失败，authService.isLoggedIn()=false");
                        // 登录失败 - 恢复UI状态
                        restoreUIAfterFailure("登录失败，请检查用户名和密码是否正确");
                        usernameField.setEditable(true);
                    }
                });
            } catch (Exception e) {
                LOG.error("登录过程中发生异常", e);
                SwingUtilities.invokeLater(() -> {
                    String errorMessage = "登录过程中发生错误：" + e.getMessage();
                    restoreUIAfterFailure(errorMessage);
                    usernameField.setEditable(true);
                });
            }
        }).start();
    }
    
    /**
     * 登录失败后恢复UI状态
     */
    private void restoreUIAfterFailure(String errorMessage) {
        LOG.warn("登录失败: " + errorMessage);
        // 恢复标题和按钮状态
        setTitle("登录到 DingCoder AI");
        setOKActionEnabled(true);
        getOKAction().setEnabled(true);
        getCancelAction().setEnabled(true);
        
        // 设置错误提示
        setErrorText(errorMessage, usernameField);
        
        // 显示错误对话框
        JOptionPane.showMessageDialog(
            getContentPanel(),
            errorMessage + "\n\n请检查：\n" +
            "1. 用户名和密码是否正确\n" +
            "2. 网络连接是否正常\n" +
            "3. 服务器是否可访问",
            "登录失败",
            JOptionPane.ERROR_MESSAGE
        );
        usernameField.setEditable(true);
    }
} 