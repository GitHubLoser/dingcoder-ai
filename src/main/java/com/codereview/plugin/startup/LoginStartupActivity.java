package com.codereview.plugin.startup;

import com.codereview.plugin.auth.AuthService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * 插件启动活动
 * 现在不再强制用户登录，让用户自己选择何时登录
 */
public class LoginStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        // 不再自动弹出登录对话框
        // 用户可以通过界面上的登录按钮主动登录
        AuthService authService = AuthService.getInstance();
        System.out.println("DingCoder AI 插件已启动，用户登录状态: " + (authService.isLoggedIn() ? "已登录" : "未登录"));
    }
} 