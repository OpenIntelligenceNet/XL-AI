package com.xxq.xlai;

/**
 * Data model for a single search result source.
 * This class is also used by Gson for deserialization in NewChatMessageEntity.
 */
public class SourceResult {
    
    private final String url;
    private final String title;
    private final String snippet;
    private final String domain;
    
    // NOTE: Gson requires a no-argument constructor for some operations, 
    // but often works fine with a fully-parameterized one if used correctly. 
    // It's safer to provide it.
    public SourceResult() {
        this("", "", "", "");
    }

    public SourceResult(String url, String title, String snippet, String domain) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.domain = domain;
    }

    // --- Getters (Required for access in Adapter and ViewModel) ---

    public String getUrl() { 
        return url; 
    }
    
    public String getTitle() { 
        return title; 
    }
    
    public String getSnippet() { 
        return snippet; 
    }
    
    public String getDomain() { 
        return domain; 
    }
}
 



