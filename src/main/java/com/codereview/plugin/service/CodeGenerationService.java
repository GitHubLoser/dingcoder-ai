package com.codereview.plugin.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码生成服务
 * 负责将代码文本生成Java文件，支持指定目录生成
 */
@Service
public final class CodeGenerationService {
    private static final Logger LOG = Logger.getInstance(CodeGenerationService.class);
    private final Project project;
    
    public CodeGenerationService(Project project) {
        this.project = project;
    }
    
    /**
     * 获取服务实例
     */
    public static CodeGenerationService getInstance(@NotNull Project project) {
        return project.getService(CodeGenerationService.class);
    }
    
    /**
     * 从代码文本中生成Java文件
     * @param codeText 包含Java代码的文本
     * @param showDialog 是否显示确认对话框
     * @return 是否成功生成文件
     */
    public boolean generateJavaFile(String codeText, boolean showDialog) {
        return generateJavaFile(codeText, showDialog, null);
    }
    
    /**
     * 从代码文本中生成Java文件到指定目录
     * @param codeText 包含Java代码的文本
     * @param showDialog 是否显示确认对话框
     * @param targetDirectory 目标目录，如果为null则会检测选中的目录或显示选择对话框
     * @return 是否成功生成文件
     */
    public boolean generateJavaFile(String codeText, boolean showDialog, @Nullable VirtualFile targetDirectory) {
        return generateJavaFile(codeText, showDialog, showDialog, targetDirectory);
    }
    
    public boolean generateJavaFile(String codeText, boolean showDiffDialog, boolean showSuccessDialog, @Nullable VirtualFile targetDirectory) {
        // 提取Java代码块
        String javaCode = extractJavaCode(codeText);
        if (javaCode == null || javaCode.trim().isEmpty()) {
            if (showDiffDialog) {
                JOptionPane.showMessageDialog(null, "未找到有效的Java代码", "错误", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }
        
        // 提取类名
        String className = extractClassName(javaCode);
        if (className == null) {
            if (showDiffDialog) {
                JOptionPane.showMessageDialog(null, "无法确定类名", "错误", JOptionPane.ERROR_MESSAGE);
            }
            return false;
        }
        
        // 确定目标目录
        VirtualFile finalTargetDir = targetDirectory;
        if (finalTargetDir == null) {
            finalTargetDir = getTargetDirectory(showDiffDialog);
            if (finalTargetDir == null) {
                return false; // 用户取消了目录选择
            }
        }
        
        try {
            // 检查文件差异
            FileDiffService fileDiffService = FileDiffService.getInstance(project);
            if (fileDiffService != null) {
                FileDiffService.FileDiffInfo diffInfo = fileDiffService.checkFileDifferences(className, javaCode, finalTargetDir);
                
                // 如果文件存在且有差异，显示差异对话框
                if (diffInfo != null && diffInfo.hasDifferences() && showDiffDialog) {
                    boolean shouldOverwrite = com.codereview.plugin.ui.FileDiffDialog.showSingleFileDiff(project, diffInfo);
                    if (!shouldOverwrite) {
                        return false; // 用户选择跳过
                    }
                }
            } else {
                LOG.warn("FileDiffService实例创建失败，跳过差异检测");
            }
            
            // 在写命令中执行文件创建
            boolean[] success = {false};
            VirtualFile finalDir = finalTargetDir;
            WriteCommandAction.runWriteCommandAction(project, () -> {
                try {
                    // 创建包目录（如果代码中有包声明）
                    VirtualFile packageDir = finalDir;
                    String packageName = extractPackageName(javaCode);
                    // 注释掉创建包目录的功能
                    /*if (packageName != null && !packageName.isEmpty()) {
                        if (showDialog) {
                            String[] packageParts = packageName.split("\\.");
                            for (String part : packageParts) {
                                packageDir = createDirectoryIfNotExists(packageDir, part);
                            }
                        } else {
                            String[] packageParts = packageName.split("\\.");
                            for (String part : packageParts) {
                                packageDir = createDirectoryIfNotExists(packageDir, part);
                            }
                        }
                    }*/
                    
                    // 创建Java文件
                    String fileName = className + ".java";
                    VirtualFile existingFile = packageDir.findChild(fileName);
                    if (existingFile != null) {
                        // 删除现有文件
                        existingFile.delete(this);
                    }
                    
                    // 创建新文件
                    VirtualFile javaFile = packageDir.createChildData(this, fileName);
                    javaFile.setBinaryContent(javaCode.getBytes("UTF-8"));
                    
                    // 格式化代码
                    ApplicationManager.getApplication().invokeLater(() -> {
                        PsiManager psiManager = PsiManager.getInstance(project);
                        PsiFile psiFile = psiManager.findFile(javaFile);
                        if (psiFile instanceof PsiJavaFile) {
                            CodeStyleManager.getInstance(project).reformat(psiFile);
                        }
                    });
                    
                    success[0] = true;
                    LOG.info("成功生成Java文件: " + javaFile.getPath());
                    
                    if (showSuccessDialog) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            JOptionPane.showMessageDialog(
                                null,
                                "成功生成Java文件: " + fileName + "\n路径: " + javaFile.getPath(),
                                "生成成功",
                                JOptionPane.INFORMATION_MESSAGE
                            );
                        });
                    }
                    
                } catch (Exception e) {
                    LOG.error("生成Java文件时出错", e);
                    if (showSuccessDialog) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            JOptionPane.showMessageDialog(
                                null,
                                "生成文件时出错: " + e.getMessage(),
                                "错误",
                                JOptionPane.ERROR_MESSAGE
                            );
                        });
                    }
                }
            });
            
            return success[0];
            
        } catch (Exception e) {
            LOG.error("生成Java文件时出错", e);
            if (showSuccessDialog) {
                JOptionPane.showMessageDialog(
                    null,
                    "生成文件时出错: " + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                );
            }
            return false;
        }
    }
    
    /**
     * 获取目标目录
     * 优先级：当前选中目录 > 用户手动选择目录 > 默认src/main/java目录
     */
    private @Nullable VirtualFile getTargetDirectory(boolean showDialog) {
        // 1. 尝试获取当前选中的目录
        VirtualFile selectedDir = getCurrentSelectedDirectory();
        if (selectedDir != null) {
            if (showDialog) {
                // 使用自定义按钮，只有"是"和"取消"，去掉"否"按钮
                String[] options = {"是", "取消"};
                int result = JOptionPane.showOptionDialog(
                    null,
                    "检测到选中目录: " + selectedDir.getPath() + "\n是否在此目录生成文件？",
                    "目录确认",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]  // 默认选择"是"
                );
                if (result == 0) {  // 选择"是"
                    return selectedDir;
                } else {  // 选择"取消"或关闭对话框
                    return null;
                }
            } else {
                return selectedDir; // 批量生成时直接使用选中目录
            }
        }
        
        // 2. 显示目录选择对话框
        if (showDialog) {
            FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
            descriptor.setTitle("选择生成Java文件的目录");
            descriptor.setDescription("请选择要生成Java文件的目录");
            
            // 设置默认目录为src/main/java（如果存在）
            VirtualFile defaultDir = getDefaultJavaSourceDirectory();
            
            VirtualFile chosenDir = FileChooser.chooseFile(descriptor, project, defaultDir);
            if (chosenDir != null && chosenDir.isDirectory()) {
                return chosenDir;
            }
        }
        
        // 3. 回退到默认src/main/java目录
        return getOrCreateDefaultJavaSourceDirectory();
    }
    
    /**
     * 获取当前在项目视图中选中的目录
     */
    public @Nullable VirtualFile getCurrentSelectedDirectory() {
        try {
            ProjectView projectView = ProjectView.getInstance(project);
            if (projectView != null) {
                AbstractProjectViewPane currentProjectViewPane = projectView.getCurrentProjectViewPane();
                if (currentProjectViewPane != null) {
                    Object selectedElement = currentProjectViewPane.getSelectedElement();
                    if (selectedElement instanceof PsiDirectory) {
                        return ((PsiDirectory) selectedElement).getVirtualFile();
                    } else if (selectedElement instanceof PsiFile) {
                        return ((PsiFile) selectedElement).getVirtualFile().getParent();
                    }
                }
            }
            
            // 备用方法：尝试通过DataContext获取
            // 注意：在工具窗口中这可能不工作，但值得尝试
            return null;
        } catch (Exception e) {
            LOG.warn("无法获取当前选中的目录", e);
            return null;
        }
    }
    
    /**
     * 获取默认的Java源码目录
     */
    private @Nullable VirtualFile getDefaultJavaSourceDirectory() {
        VirtualFile projectRoot = project.getBaseDir();
        if (projectRoot == null) {
            return null;
        }
        
        // 尝试查找常见的Java源码目录
        String[] possiblePaths = {
            "src/main/java",
            "src/java",
            "src"
        };
        
        for (String path : possiblePaths) {
            VirtualFile dir = projectRoot.findFileByRelativePath(path);
            if (dir != null && dir.isDirectory()) {
                return dir;
            }
        }
        
        return projectRoot; // 如果都没找到，返回项目根目录
    }
    
    /**
     * 获取或创建默认的Java源码目录
     */
    private @Nullable VirtualFile getOrCreateDefaultJavaSourceDirectory() {
        try {
            VirtualFile projectRoot = project.getBaseDir();
            if (projectRoot == null) {
                return null;
            }
            
            // 创建src/main/java目录结构
            VirtualFile srcDir = createDirectoryIfNotExists(projectRoot, "src");
            VirtualFile mainDir = createDirectoryIfNotExists(srcDir, "main");
            return createDirectoryIfNotExists(mainDir, "java");
        } catch (Exception e) {
            LOG.warn("无法创建默认Java源码目录", e);
            return project.getBaseDir();
        }
    }
    
    /**
     * 创建目录（如果不存在）
     */
    private VirtualFile createDirectoryIfNotExists(VirtualFile parent, String name) throws Exception {
        VirtualFile child = parent.findChild(name);
        if (child == null) {
            child = parent.createChildDirectory(this, name);
        }
        return child;
    }
    
    /**
     * 从文本中提取Java代码块
     */
    private String extractJavaCode(String text) {
        // 匹配```java代码块
        Pattern pattern = Pattern.compile("```java\\s*\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        
        // 如果没有代码块标记，检查是否直接是Java代码
        if (text.contains("class ") || text.contains("interface ") || text.contains("enum ")) {
            return text.trim();
        }
        
        return null;
    }
    
    /**
     * 从Java代码中提取类名
     */
    private String extractClassName(String javaCode) {
        // 匹配public class、class、public interface、interface等
        Pattern pattern = Pattern.compile("(?:public\\s+)?(?:class|interface|enum)\\s+(\\w+)");
        Matcher matcher = pattern.matcher(javaCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * 从Java代码中提取包名
     */
    private String extractPackageName(String javaCode) {
        Pattern pattern = Pattern.compile("package\\s+([\\w\\.]+);");
        Matcher matcher = pattern.matcher(javaCode);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
} 