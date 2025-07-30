package com.codereview.plugin.utils;

import com.codereview.plugin.auth.AuthService;
import com.intellij.openapi.diagnostic.Logger;

/**
 * Token测试工具类
 * 提供测试token刷新功能的方法
 */
public class TokenTestUtils {
    private static final Logger LOG = Logger.getInstance(TokenTestUtils.class);
    
    /**
     * 测试token状态
     */
    public static void testTokenStatus() {
        AuthService authService = AuthService.getInstance();
        
        if (!authService.isLoggedIn()) {
            LOG.info("用户未登录，无法测试token状态");
            return;
        }
        
        double remainingMinutes = authService.getTokenRemainingMinutes();
        boolean isExpiringSoon = authService.isTokenExpiringSoon();
        boolean isExpired = authService.isTokenExpired();
        
        LOG.info("=== Token状态测试 ===");
        LOG.info("剩余有效时间: " + String.format("%.2f", remainingMinutes) + " 分钟");
        LOG.info("是否即将过期: " + isExpiringSoon);
        LOG.info("是否已过期: " + isExpired);
        LOG.info("==================");
    }
    
    /**
     * 手动触发token检查
     */
    public static void manualCheckToken() {
        AuthService authService = AuthService.getInstance();
        
        if (!authService.isLoggedIn()) {
            LOG.info("用户未登录，无法检查token");
            return;
        }
        
        LOG.info("手动触发token检查...");
        authService.manualCheckToken();
    }
    
    /**
     * 获取详细token状态信息
     */
    public static String getDetailedTokenInfo() {
        AuthService authService = AuthService.getInstance();
        
        if (!authService.isLoggedIn()) {
            return "用户未登录";
        }
        
        return authService.getTokenStatusInfo();
    }
    
    /**
     * 强制刷新token（测试用）
     */
    public static void forceRefreshToken() {
        AuthService authService = AuthService.getInstance();
        
        if (!authService.isLoggedIn()) {
            LOG.info("用户未登录，无法刷新token");
            return;
        }
        
        LOG.info("开始强制刷新token...");
        authService.forceRefreshToken();
    }
    
    /**
     * 模拟token即将过期（测试用）
     */
    public static void simulateTokenExpiringSoon() {
        LOG.info("模拟token即将过期状态...");
        // 这里可以通过反射或其他方式修改token过期时间
        // 暂时只是记录日志
        LOG.info("请在AuthService中手动调整token过期时间进行测试");
    }
    
    /**
     * 获取详细的token信息
     */
    public static String getTokenInfo() {
        AuthService authService = AuthService.getInstance();
        
        if (!authService.isLoggedIn()) {
            return "用户未登录";
        }
        
        StringBuilder info = new StringBuilder();
        info.append("=== Token详细信息 ===\n");
        info.append("剩余有效时间: ").append(String.format("%.2f", authService.getTokenRemainingMinutes())).append(" 分钟\n");
        info.append("是否即将过期: ").append(authService.isTokenExpiringSoon()).append("\n");
        info.append("是否已过期: ").append(authService.isTokenExpired()).append("\n");
        info.append("==================");
        
        return info.toString();
    }
} 