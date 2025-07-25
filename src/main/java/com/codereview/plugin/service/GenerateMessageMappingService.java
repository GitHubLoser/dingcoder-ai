package com.codereview.plugin.service;

import com.intellij.openapi.diagnostic.Logger;

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


    private static String findPropertiesFilePath() {
        String userPath = System.getProperty("user.dir");
        java.io.File userDir = new java.io.File(userPath);
        String relativePath = "develop/lang/message-application_zh_CN.properties";
        java.util.List<String> triedPaths = new java.util.ArrayList<>();

        for (java.io.File sub : userDir.listFiles()) {
            LOG.info("当前目录："+ sub.getAbsolutePath());
            if (sub.isDirectory()) {
                java.io.File candidate = new java.io.File(sub, relativePath);
                triedPaths.add(candidate.getAbsolutePath());
                if (candidate.exists()) {
                    return candidate.getAbsolutePath();
                }
            }
        }
        LOG.info("[DEBUG] Tried paths for message-application_zh_CN.properties:");
        for (String p : triedPaths) {
            LOG.info("实际message-application_zh_CN.properties的查找路径：" + p);
        }

        throw new RuntimeException("未找到 message-application_zh_CN.properties 文件");
    }

    /**
     * 写入多语言文件
     * @param key
     * @param chineseValue
     * @throws IOException
     */
    public static void writeUnicodeProperties(String key, String chineseValue) throws IOException {
        boolean exist = keyIsExist(key);
        if (exist) {
            return;
        }
        String path = findPropertiesFilePath();
        NoTimestampProperties prop = new NoTimestampProperties();
        try (OutputStream output = new FileOutputStream(path,true)) {
            prop.setProperty(key, convertToUnicode(chineseValue));
            prop.store(output, null);
        }
    }

    /**
     * 自动将中文转换为Unicode格式
     * @param str
     * @return
     */
    private static String convertToUnicode(String str) {
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (c > 127) {
                sb.append("\\u").append(String.format("%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }



    /**
     * 从多语言文件中读取key是否存在
     * @param key
     * @return
     * @throws IOException
     */
    public static boolean keyIsExist(String key) throws IOException {
        String path = findPropertiesFilePath();
        Properties props = new Properties();
        FileInputStream fis = new FileInputStream(path);
        // 加载属性文件
        props.load(fis);
        return org.apache.commons.lang3.StringUtils.isNotEmpty(props.getProperty(key));
    }

}
