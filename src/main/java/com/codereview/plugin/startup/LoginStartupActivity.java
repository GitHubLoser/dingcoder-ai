package com.codereview.plugin.startup;

import com.codereview.plugin.auth.AuthService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * 插件启动活动
 * 现在只检查登录状态，不执行登出操作
 */
public class LoginStartupActivity implements StartupActivity {
    @Override
    public void runActivity(@NotNull Project project) {
        // 移除启动时的登出操作，避免IDEA启动卡住
        // AuthService.getInstance().logout();
        
        // 只检查登录状态，不执行清理操作
        AuthService authService = AuthService.getInstance();
        System.out.println("DingCoder AI 插件已启动，用户登录状态: " + (authService.isLoggedIn() ? "已登录" : "未登录"));
    }
} 