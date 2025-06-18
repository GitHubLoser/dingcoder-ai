package com.codereview.plugin.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * 鼎码智辅动作类
 */
public class ChatAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 直接打开工具窗口，不再强制登录
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("鼎码智辅");
        if (toolWindow != null) {
            toolWindow.show(null);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 如果没有打开项目，则禁用此动作
        Project project = e.getProject();
        e.getPresentation().setEnabled(project != null);
    }
} 