package com.codereview.plugin.startup;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.service.MQTTService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationListener;
import org.jetbrains.annotations.NotNull;

/**
 * IDEA应用退出监听器
 * 负责在IDEA退出时执行登出操作
 */
public class ApplicationShutdownListener implements ApplicationListener {
    private static final Logger LOG = Logger.getInstance(ApplicationShutdownListener.class);

    @Override
    public void applicationExiting() {
        LOG.info("IDEA正在退出，执行登出操作");
        performLogout();
    }

    /**
     * 执行登出操作
     */
    private void performLogout() {
        try {
            LOG.info("开始执行登出操作");
            
            // 模拟完整的手动退出登录流程
            AuthService authService = AuthService.getInstance();
            if (authService.isLoggedIn()) {
                LOG.info("检测到用户已登录，执行完整退出登录流程");
                
                // 1. 调用AuthService的logout方法（与手动退出登录一致）
                authService.logout();
                
                // 2. 清空所有面板内容
                try {
                    com.codereview.plugin.ui.ChatToolWindowFactory.clearCurrentPanelContent();
                    LOG.info("已清空所有面板内容");
                } catch (Exception e) {
                    LOG.error("清空面板内容时出错", e);
                }
                
                // 3. 更新所有面板状态
                try {
                    com.codereview.plugin.ui.ChatToolWindowFactory.updateCurrentPanelStatus();
                    LOG.info("已更新所有面板状态");
                } catch (Exception e) {
                    LOG.error("更新面板状态时出错", e);
                }
                
                // 4. 强制断开MQTT连接
                try {
                    com.codereview.plugin.service.MQTTService mqttService = com.codereview.plugin.service.MQTTService.getInstance();
                    if (mqttService != null && mqttService.isConnected()) {
                        mqttService.disconnect();
                        LOG.info("已强制断开MQTT连接");
                    }
                } catch (Exception e) {
                    LOG.error("断开MQTT连接时出错", e);
                }
            }
            
            LOG.info("完整退出登录流程完成");
        } catch (Exception e) {
            LOG.error("登出操作时发生错误", e);
        }
    }
} 