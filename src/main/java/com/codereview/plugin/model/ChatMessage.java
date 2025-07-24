package com.codereview.plugin.model;

import java.util.UUID;

public class ChatMessage {
    private final String content;
    private final boolean isUser;
    private final long timestamp;
    private final String uuid = UUID.randomUUID().toString();

    public ChatMessage(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = System.currentTimeMillis();
    }

    public String getContent() {
        return content;
    }

    public boolean isUser() {
        return isUser;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getUuid() { return uuid; }
} 