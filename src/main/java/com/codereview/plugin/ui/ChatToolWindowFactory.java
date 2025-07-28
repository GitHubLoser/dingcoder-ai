package com.codereview.plugin.ui;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.auth.LoginDialog;
import com.codereview.plugin.service.MQTTService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * 鼎码智辅工具窗口工厂类
 */
public class ChatToolWindowFactory implements ToolWindowFactory {
    // 改为Project级别的面板管理
    private static final java.util.Map<Project, MainToolWindowPanel> projectPanels = new java.util.concurrent.ConcurrentHashMap<>();
    
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 检查是否已经为该项目创建了面板
        if (projectPanels.containsKey(project)) {
            return; // 避免重复创建
        }
        
        // 延迟创建面板，避免启动时卡顿
        SwingUtilities.invokeLater(() -> {
            try {
                // 再次检查，确保在延迟执行期间没有其他线程创建
                if (projectPanels.containsKey(project)) {
                    return;
                }
                
                // 创建工具窗口内容
                MainToolWindowPanel panel = new MainToolWindowPanel(project);
                projectPanels.put(project, panel);  // 保存面板引用
                
                // 使用旧版API方式创建Content
                ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
                Content content = contentFactory.createContent(panel, "", false);
                
                // 添加内容管理器监听器
                ContentManager contentManager = toolWindow.getContentManager();
                contentManager.addContentManagerListener(new ContentManagerListener() {
                    @Override
                    public void contentRemoved(@NotNull ContentManagerEvent event) {
                        Content content = event.getContent();
                        if (content.getComponent() instanceof MainToolWindowPanel) {
                            MainToolWindowPanel panel = (MainToolWindowPanel) content.getComponent();
                            // 先清空面板内容
                            panel.clearAllPanelContent();
                            // 再释放资源
                            panel.dispose();
                            projectPanels.remove(project);
                        }
                    }
                    
                    @Override
                    public void selectionChanged(@NotNull ContentManagerEvent event) {
                        // 无需处理
                    }
                    
                    @Override
                    public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
                        // 无需处理
                    }
                    
                    @Override
                    public void contentAdded(@NotNull ContentManagerEvent event) {
                        // 无需处理
                    }
                });
                
                contentManager.addContent(content);
                
                // 添加项目关闭监听器，确保资源正确释放
                project.getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
                    @Override
                    public void projectClosing(@NotNull Project project) {
                        // 项目关闭时清空面板内容并清理资源
                        MainToolWindowPanel panel = projectPanels.get(project);
                        if (panel != null) {
                            // 先清空面板内容
                            panel.clearAllPanelContent();
                            // 再释放资源
                            panel.dispose();
                            projectPanels.remove(project);
                        }
                        
                        // 强制清理MQTT服务
                        try {
                            MQTTService mqttService = MQTTService.getInstance();
                            if (mqttService != null) {
                                mqttService.forceCleanup();
                            }
                        } catch (Exception e) {
                            // 忽略清理时的异常
                        }
                    }
                });
            } catch (Exception e) {
                // 忽略创建时的异常，确保不影响IDEA启动
            }
        });
    }
    
    /**
     * 清空当前面板内容（退出登录时调用）
     */
    public static void clearCurrentPanelContent() {
        // 清空所有项目面板内容
        for (MainToolWindowPanel panel : projectPanels.values()) {
            if (panel != null) {
                try {
                    panel.clearAllPanelContent();
                } catch (Exception e) {
                    // 忽略清理时的异常，确保不影响退出流程
                }
            }
        }
    }
    
    /**
     * 获取当前登录状态，不显示登录对话框
     * @param project 当前项目
     * @return 当前登录状态
     */
    public static boolean isLoggedIn(@NotNull Project project) {
        AuthService authService = AuthService.getInstance();
        return authService.isLoggedIn();
    }
    
    /**
     * 检查用户登录状态，如果未登录则显示登录对话框
     * 返回登录状态：true表示已登录，false表示未登录
     */
    public static boolean checkAndShowLoginDialog(@NotNull Project project) {
        AuthService authService = AuthService.getInstance();
        
        // 如果已登录，直接返回true
        if (authService.isLoggedIn()) {
            return true;
        }
        
        // 未登录，显示登录对话框并等待结果
        final boolean[] result = new boolean[1];
        
        // 在UI线程中显示模态登录对话框（优化版本）
        SwingUtilities.invokeLater(() -> {
            try {
                LoginDialog loginDialog = new LoginDialog(project);
                result[0] = loginDialog.showAndGet() && authService.isLoggedIn();
                
                // 登录成功后，更新所有主面板状态
                if (result[0]) {
                    SwingUtilities.invokeLater(() -> {
                        for (MainToolWindowPanel panel : projectPanels.values()) {
                            if (panel != null) {
                                try {
                                    panel.updateUIState();
                                } catch (Exception e) {
                                    // 忽略更新时的异常
                                }
                            }
                        }
                    });
                }
                
                // 如果登录失败，显示消息
                if (!result[0]) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(
                            null, 
                            "必须登录才能使用鼎码智辅功能",
                            "需要登录",
                            JOptionPane.WARNING_MESSAGE
                        );
                    });
                }
            } catch (Exception e) {
                // 忽略登录对话框的异常
                result[0] = false;
            }
        });
        
        return result[0];
    }
    
        /**
     * 更新当前面板状态（供外部调用）
     */
    public static void updateCurrentPanelStatus() {
        SwingUtilities.invokeLater(() -> {
            for (MainToolWindowPanel panel : projectPanels.values()) {
                if (panel != null) {
                    try {
                        panel.updateUIState();
                    } catch (Exception e) {
                        // 忽略更新时的异常，确保不影响UI流程
                    }
                }
            }
        });
    }

    /**
     * 获取当前主面板（供外部调用）
     */
    public static MainToolWindowPanel getCurrentPanel() {
        // 返回第一个可用的面板（向后兼容）
        if (!projectPanels.isEmpty()) {
            return projectPanels.values().iterator().next();
        }
        return null;
    }
    
    /**
     * 获取指定项目的主面板
     */
    public static MainToolWindowPanel getCurrentPanel(Project project) {
        return projectPanels.get(project);
    }
} 