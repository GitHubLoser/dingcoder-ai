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
            
            // 显示测试选择对话框
            String[] options = {"MQTT重连内存泄漏测试", "Token刷新+MQTT重连测试", "取消"};
            int choice = Messages.showChooseDialog(e.getProject(), 
                "请选择要执行的测试类型：\n\n" +
                "1. MQTT重连内存泄漏测试：专门测试MQTT重连是否会导致内存泄漏\n" +
                "2. Token刷新+MQTT重连测试：测试Token刷新和MQTT重连功能\n\n" +
                "测试过程请查看IDEA的日志输出。", 
                "选择测试类型", 
                null, 
                options, 
                options[0]);
            
            if (choice == 0) {
                // MQTT重连内存泄漏测试
                LOG.info("用户选择了MQTT重连内存泄漏测试");
                
                Messages.showInfoMessage(e.getProject(), 
                    "开始MQTT重连内存泄漏测试...\n" +
                    "测试将模拟10次断开重连，请查看日志输出。", 
                    "测试开始");
                
                new Thread(() -> {
                    try {
                        TokenQuickTestUtils.testMqttReconnectMemoryLeak();
                        
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                            Messages.showInfoMessage(e.getProject(), 
                                "MQTT重连内存泄漏测试完成！\n" +
                                "请查看IDEA的日志输出了解详细信息。", 
                                "测试完成");
                        });
                        
                    } catch (Exception ex) {
                        LOG.error("MQTT重连内存泄漏测试时发生错误", ex);
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                            Messages.showErrorDialog(e.getProject(), 
                                "测试失败: " + ex.getMessage(), "错误");
                        });
                    }
                }, "MqttMemoryLeakTest").start();
                
            } else if (choice == 1) {
                // Token刷新+MQTT重连测试
                LOG.info("用户选择了Token刷新+MQTT重连测试");
                
                Messages.showInfoMessage(e.getProject(), 
                    "开始测试Token刷新和MQTT重连功能...\n" +
                    "测试将持续约10秒，请查看日志输出。", 
                    "测试开始");
                
                new Thread(() -> {
                    try {
                        TokenQuickTestUtils.testTokenRefreshAndMqttReconnect();
                        
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
                
            } else {
                // 用户取消
                LOG.info("用户取消了测试");
            }
            
        } catch (Exception ex) {
            LOG.error("启动测试时发生错误", ex);
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