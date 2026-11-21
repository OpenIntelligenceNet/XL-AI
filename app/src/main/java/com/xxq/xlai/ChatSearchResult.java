package com.xxq.xlai;

public class ChatSearchResult {
    public long sessionId;
    public String title;
    public String matchedPrompt;
    public long lastTimestamp;

    public ChatSearchResult(long sessionId, String title, String matchedPrompt, long lastTimestamp) {
        this.sessionId = sessionId;
        this.title = title;
        this.matchedPrompt = matchedPrompt;
        this.lastTimestamp = lastTimestamp;
    }
}
