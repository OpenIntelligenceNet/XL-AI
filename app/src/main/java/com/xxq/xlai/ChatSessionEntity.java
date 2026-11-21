package com.xxq.xlai;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;

@Entity(tableName = "chat_sessions")
public class ChatSessionEntity {
    
    @PrimaryKey(autoGenerate = true)
    public long sessionId; // Unique ID for the folder
    
    public String title;   // The heading shown in drawer (e.g., "How to bake cake...")
    public long lastTimestamp; // To sort by "Yesterday", "Today"

    // 🏆 NEW FIELD: Ties this specific chat history to the model that created it.
    // Adding a defaultValue helps prevent crashes if you ever write a Room Migration.
    @ColumnInfo(defaultValue = "venice") 
    public String modelName;

    // ⚠️ UPDATED CONSTRUCTOR: Now requires the model name when creating a new session
    public ChatSessionEntity(String title, long lastTimestamp, String modelName) {
        this.title = title;
        this.lastTimestamp = lastTimestamp;
        this.modelName = modelName;
    }
}

