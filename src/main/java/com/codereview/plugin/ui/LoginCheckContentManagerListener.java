package com.codereview.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * 登录检查内容管理器监听器，监听工具窗口的激活事件
 */
public class LoginCheckContentManagerListener implements ContentManagerListener {
    private final Project project;
    
    public LoginCheckContentManagerListener(Project project) {
        this.project = project;
    }
    
    @Override
    public void contentAdded(@NotNull ContentManagerEvent event) {
        // 无需处理
    }

    @Override
    public void contentRemoved(@NotNull ContentManagerEvent event) {
        // 无需处理
    }

    @Override
    public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
        // 无需处理
    }

    @Override
    public void selectionChanged(@NotNull ContentManagerEvent event) {
        // 当内容被选中（窗口激活）时更新用户状态
        if (event.getOperation() == ContentManagerEvent.ContentOperation.add) {
            Content content = event.getContent();
            if (content.getComponent() instanceof ChatToolWindowPanel) {
                // 获取当前面板并更新用户状态
                ChatToolWindowPanel panel = (ChatToolWindowPanel) content.getComponent();
                panel.updateUIState();
            }
        }
    }
} 