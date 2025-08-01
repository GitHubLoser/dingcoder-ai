package com.codereview.plugin.action;

import com.codereview.plugin.utils.TokenQuickTestUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;

/**
 * Token刷新和MQTT重连测试Action
 * 提供测试Token刷新和MQTT重连功能的UI入口
 */
public class TokenRefreshMqttTestAction extends AnAction {
    
    private static final Logger LOG = Logger.getInstance(TokenRefreshMqttTestAction.class);
    
    public TokenRefreshMqttTestAction() {
        super("Token刷新+MQTT重连测试", "测试Token刷新和MQTT重连功能", null);
    }
    
    @Override
    public void actionPerformed(AnActionEvent e) {
        try {
            LOG.info("用户点击了Token刷新+MQTT重连测试");
            
            // 显示开始提示
            Messages.showInfoMessage(e.getProject(), 
                "开始测试Token刷新和MQTT重连功能...\n" +
                "测试将持续约10秒，请查看日志输出。", 
                "测试开始");
            
            // 在新线程中执行测试，避免阻塞UI
            new Thread(() -> {
                try {
                    // 执行Token刷新和MQTT重连测试
                    TokenQuickTestUtils.testTokenRefreshAndMqttReconnect();
                    
                    // 测试完成后显示结果
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showInfoMessage(e.getProject(), 
                            "Token刷新和MQTT重连测试完成！\n" +
                            "请查看IDEA的日志输出了解详细信息。", 
                            "测试完成");
                    });
                    
                } catch (Exception ex) {
                    LOG.error("Token刷新和MQTT重连测试时发生错误", ex);
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(e.getProject(), 
                            "测试失败: " + ex.getMessage(), "错误");
                    });
                }
            }, "TokenRefreshMqttTest").start();
            
        } catch (Exception ex) {
            LOG.error("启动Token刷新和MQTT重连测试时发生错误", ex);
            Messages.showErrorDialog(e.getProject(), 
                "启动测试失败: " + ex.getMessage(), "错误");
        }
    }
    
    @Override
    public void update(AnActionEvent e) {
        // 总是启用这个Action
        e.getPresentation().setEnabled(true);
    }
} 