package com.codereview.plugin.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

/**
 * 多语言文件生成服务
 */
public class GenerateMessageMappingService {

    private static final Logger LOG = Logger.getInstance(GenerateMessageMappingService.class);


    public static String findPropertiesFilePath(Project project) {
        String userPath = project.getBasePath();
        LOG.info("当前项目根目录：" + userPath);
        java.io.File userDir = new java.io.File(userPath);
        String relativePath = "develop/lang/message-application_zh_CN.properties";
        java.util.List<String> triedPaths = new java.util.ArrayList<>();
        for (java.io.File sub : userDir.listFiles()) {
            if (sub.isDirectory()) {
                java.io.File candidate = new java.io.File(sub, relativePath);
                triedPaths.add(candidate.getAbsolutePath());
                if (candidate.exists()) {
                    return candidate.getAbsolutePath();
                }
            }
        }
        throw new RuntimeException("未找到 message-application_zh_CN.properties 文件");
    }

    /**
     * 写入多语言文件
     * @param key
     * @param chineseValue
     * @throws IOException
     */
    public static void writeUnicodeProperties(Project project, String key, String chineseValue) throws IOException {
        boolean exist = keyIsExist(project, key);
        LOG.info("从多语言文件中读取key是否存在:"+exist);
        if (exist) {
            return;
        }
        String path = findPropertiesFilePath(project);
        
        // 检查文件是否以换行符结尾，如果不是则先添加换行符
        java.io.File file = new java.io.File(path);
        boolean needNewline = true;
        if (file.exists() && file.length() > 0) {
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
                raf.seek(file.length() - 1);
                int lastChar = raf.read();
                needNewline = (lastChar != '\n');
            }
        }
        
        // 直接追加新内容，不使用Properties.store()方法
        try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                new java.io.FileOutputStream(path, true), java.nio.charset.StandardCharsets.UTF_8)) {
            // 如果文件不以换行符结尾，先添加一个换行符
            if (needNewline) {
                writer.write("\n");
            }
            // 确保每次写入都有换行符，避免多个key-value对写在同一行
            writer.write(escape(key) + "=" + escape(chineseValue) + "\n");
            writer.flush(); // 确保立即写入磁盘
        }
    }

    /**
     * 转义字符串，处理特殊字符
     */
    private static String escape(String str) {
        return str.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }



    /**
     * 从多语言文件中读取key是否存在
     * @param key
     * @return
     * @throws IOException
     */
    public static boolean keyIsExist(Project project, String key) throws IOException {
        String path = findPropertiesFilePath(project);
        Properties props = new Properties();
        FileInputStream fis = new FileInputStream(path);
        // 加载属性文件
        props.load(fis);
        return org.apache.commons.lang3.StringUtils.isNotEmpty(props.getProperty(key));
    }

}

