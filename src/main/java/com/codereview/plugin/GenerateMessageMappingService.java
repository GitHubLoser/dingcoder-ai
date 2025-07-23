package com.codereview.plugin;

import org.apache.commons.lang3.StringUtils;
import java.io.*;
import java.util.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


public class GenerateMessageMappingService {

    /**
     * 写入多语言文件（递归查找项目下的 message-application_zh_CN.properties 文件）
     * @param key
     * @param chineseValue
     * @throws IOException
     */
    public static void writeUnicodeProperties(String key, String chineseValue) throws IOException {
        String userPath = System.getProperty("user.dir");
        System.out.println("[多语言] 开始递归查找 message-application_zh_CN.properties，根目录: " + userPath);
        List<File> langFiles = findAllLangFiles(new File(userPath));
        if (langFiles.isEmpty()) {
            System.err.println("[多语言] 未找到 message-application_zh_CN.properties 文件");
            throw new FileNotFoundException("未找到 message-application_zh_CN.properties 文件");
        }
        for (File f : langFiles) {
            System.out.println("[多语言] 找到候选文件: " + f.getAbsolutePath());
        }
        // 只写第一个找到的文件
        File targetFile = langFiles.get(0);
        System.out.println("[多语言] 选中写入目标文件: " + targetFile.getAbsolutePath());
        NoTimestampProperties prop = new NoTimestampProperties();
        try (OutputStream output = new FileOutputStream(targetFile, true)) {
            prop.setProperty(key, convertToUnicode(chineseValue));
            prop.store(output, null);
            System.out.println("[多语言] 已写入 key=" + key + ", value=" + chineseValue);
        }
    }

    /**
     * 递归查找所有 message-application_zh_CN.properties 文件
     */
    public static List<File> findAllLangFiles(File rootDir) {
        List<File> result = new ArrayList<>();
        if (rootDir == null || !rootDir.exists()) return result;
        File[] files = rootDir.listFiles();
        if (files == null) return result;
        for (File file : files) {
            if (file.isDirectory()) {
                result.addAll(findAllLangFiles(file));
            } else if (file.getName().equals("message-application_zh_CN.properties")) {
                System.out.println("[多语言] 发现文件: " + file.getAbsolutePath());
                result.add(file);
            }
        }
        return result;
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
        String userPath = System.getProperty("user.dir");
        String path = userPath+"\\lang\\message-application_zh_CN.properties";
        Properties props = new Properties();
        FileInputStream fis = new FileInputStream(path);
        // 加载属性文件
        props.load(fis);
        return StringUtils.isNotEmpty(props.getProperty(key));
    }


    public static void main(String[] args) throws IOException {
        writeUnicodeProperties("aaa","111");
        writeUnicodeProperties("bbb","111");
    }
}
