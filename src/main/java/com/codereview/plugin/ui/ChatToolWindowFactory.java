package com.codereview.plugin.ui;

import com.codereview.plugin.auth.AuthService;
import com.codereview.plugin.auth.LoginDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
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
    private static ChatToolWindowPanel currentPanel = null;
    
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 创建工具窗口内容
        ChatToolWindowPanel panel = new ChatToolWindowPanel(project);
        currentPanel = panel;  // 保存面板引用
        
        // 使用旧版API方式创建Content
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        Content content = contentFactory.createContent(panel, "", false);
        
        // 添加内容管理器监听器
        ContentManager contentManager = toolWindow.getContentManager();
        contentManager.addContentManagerListener(new ContentManagerListener() {
            @Override
            public void contentRemoved(@NotNull ContentManagerEvent event) {
                Content content = event.getContent();
                if (content.getComponent() instanceof ChatToolWindowPanel) {
                    ChatToolWindowPanel panel = (ChatToolWindowPanel) content.getComponent();
                    panel.dispose();
                    if (panel == currentPanel) {
                        currentPanel = null;
                    }
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
        
        // 在UI线程中显示模态登录对话框
        ApplicationManager.getApplication().invokeAndWait(() -> {
            LoginDialog loginDialog = new LoginDialog(project);
            result[0] = loginDialog.showAndGet() && authService.isLoggedIn();
            
            // 登录成功后，更新主面板状态
            if (result[0] && currentPanel != null) {
                SwingUtilities.invokeLater(() -> currentPanel.updateUIState());
            }
            
            // 如果登录失败，显示消息
            if (!result[0]) {
                JOptionPane.showMessageDialog(
                    null, 
                    "必须登录才能使用鼎码智辅功能",
                    "需要登录",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        });
        
        return result[0];
    }
    
    /**
     * 更新当前面板状态（供外部调用）
     */
    public static void updateCurrentPanelStatus() {
        if (currentPanel != null) {
            SwingUtilities.invokeLater(() -> currentPanel.updateUIState());
        }
    }
} 