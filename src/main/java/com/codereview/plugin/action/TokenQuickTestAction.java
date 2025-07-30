package com.codereview.plugin.action;

import com.codereview.plugin.utils.TokenQuickTestUtils;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;

/**
 * Token快速测试Action
 * 提供即时测试Token自动刷新功能的UI入口
 */
public class TokenQuickTestAction extends AnAction {
    
    private static final Logger LOG = Logger.getInstance(TokenQuickTestAction.class);
    
    public TokenQuickTestAction() {
        super("Token快速测试", "即时测试Token自动刷新功能（模拟即将过期）", null);
    }
    
    @Override
    public void actionPerformed(AnActionEvent e) {
        try {
            LOG.info("用户点击了Token快速测试");
            
            // 执行快速测试（模拟即将过期）
            TokenQuickTestUtils.quickTestWithSimulation();
            
            // 获取测试报告
            String testReport = TokenQuickTestUtils.getQuickTestReport();
            
            // 显示结果
            Messages.showInfoMessage(e.getProject(), testReport, "Token快速测试结果");
            
            LOG.info("Token快速测试完成");
            
        } catch (Exception ex) {
            LOG.error("Token快速测试时发生错误", ex);
            Messages.showErrorDialog(e.getProject(), 
                "Token快速测试失败: " + ex.getMessage(), "错误");
        }
    }
    
    @Override
    public void update(AnActionEvent e) {
        // 总是启用这个Action，即使未登录也可以测试
        e.getPresentation().setEnabled(true);
    }
} 