package com.codereview.plugin.action;

import com.codereview.plugin.ui.CodeReviewPanel;
import com.codereview.plugin.ui.ChatToolWindowFactory;
import com.codereview.plugin.ui.MainToolWindowPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;

/**
 * 代码评审右键菜单Action
 */
public class CodeReviewAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        
        if (editor == null || file == null) {
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return;
        }

        // 获取选中代码的行数信息
        Document document = editor.getDocument();
        int startLine = document.getLineNumber(selectionModel.getSelectionStart()) + 1;
        int endLine = document.getLineNumber(selectionModel.getSelectionEnd()) + 1;
        
        // 打开并激活工具窗口
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("鼎码智辅");
        
        if (toolWindow != null) {
            toolWindow.activate(() -> {
                // 获取主面板并切换到代码审查tab
                MainToolWindowPanel mainPanel = ChatToolWindowFactory.getCurrentPanel();
                if (mainPanel != null) {
                    mainPanel.switchToCodeReviewTab();
                }
                
                // 添加选中代码到评审面板
                CodeReviewPanel reviewPanel = CodeReviewPanel.getInstance();
                if (reviewPanel != null) {
                    String description = "右键添加的代码片段: " + 
                                       selectedText.substring(0, Math.min(50, selectedText.length())) + "...";
                    reviewPanel.addFileToReview(file.getName(), description, startLine, endLine);
                }
            });
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        // 只有在有项目、编辑器且有选中文本时才显示此菜单项
        boolean hasSelection = false;
        if (editor != null) {
            SelectionModel selectionModel = editor.getSelectionModel();
            hasSelection = selectionModel.hasSelection() && 
                          selectionModel.getSelectedText() != null && 
                          !selectionModel.getSelectedText().trim().isEmpty();
        }
        
        e.getPresentation().setEnabled(project != null && editor != null && hasSelection);
        e.getPresentation().setVisible(project != null && editor != null && hasSelection);
    }
} 