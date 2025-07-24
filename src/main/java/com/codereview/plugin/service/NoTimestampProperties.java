package com.codereview.plugin.service;


import java.io.*;
import java.util.Properties;


public class NoTimestampProperties extends Properties {
    @Override
    public void store(OutputStream out, String comments) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(out, "ISO-8859-1"))) {
            if (comments != null) {
                writer.write("#" + comments.replace("\n", "\\n") + "\n");
            }
            forEach((key, value) -> {
                try {
                    writer.write(escape(key) + "=" + escape(value) + "\n");
                } catch (IOException e) {
                    throw new UncheckedIOException("写入属性失败", e);
                }
            });
        }
    }

    private String escape(Object obj) {
        return obj.toString()
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
