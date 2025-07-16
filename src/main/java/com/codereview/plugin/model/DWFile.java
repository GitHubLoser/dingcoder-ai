package com.codereview.plugin.model;

/**
 * DW文件模型，用于API传输
 */
public class DWFile {
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件字节数组
     */
    private byte[] fileByteArray;
    
    public DWFile() {
    }
    
    public DWFile(String fileName, byte[] fileByteArray) {
        this.fileName = fileName;
        this.fileByteArray = fileByteArray;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public byte[] getFileByteArray() {
        return fileByteArray;
    }
    
    public void setFileByteArray(byte[] fileByteArray) {
        this.fileByteArray = fileByteArray;
    }
} 