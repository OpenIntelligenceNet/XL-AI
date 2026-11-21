package com.xxq.xlai;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import com.google.gson.Gson; // <-- Missing Library/Dependency


import com.google.gson.Gson; // Assuming Gson is used for JSON handling
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

// ⚠️ KEY CHANGE: Renaming the table to "new_chat_messages" prevents conflict
@Entity(tableName = "new_chat_messages")
public class NewChatMessageEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    
    @NonNull
    public String message;
    
    public int type;
    public long timestamp;
    
    // Fields to support the Flask server's structured response
    @NonNull
    public String messageType; 
    public String language;
    
    // Field to store the image URI
    public String imageUri;
    
    // --- NEW FIELDS FOR SEARCH SOURCES ---
    
    // Flag to check if search was performed (optional but efficient)
    public boolean isSearchUsed; 

    // Field to store the List<SourceResult> as a JSON string for Room storage
    public String sourceResultsJson; 

    // --- Constructors ---

    public NewChatMessageEntity(
        @NonNull String message, int type, long timestamp, 
        @NonNull String messageType, String language, String imageUri,
        boolean isSearchUsed, String sourceResultsJson // <-- NEW parameters
    ) {
        this.message = message;
        this.type = type;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.language = language;
        this.imageUri = imageUri;
        this.isSearchUsed = isSearchUsed; // <-- NEW assignment
        this.sourceResultsJson = sourceResultsJson; // <-- NEW assignment
    }

    @Ignore
    public NewChatMessageEntity(
        @NonNull String message, int type, long timestamp, 
        @NonNull String messageType, String language, String imageUri, boolean isNew,
        boolean isSearchUsed, String sourceResultsJson // <-- NEW parameters
    ) {
        this.message = message;
        this.type = type;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.language = language;
        this.imageUri = imageUri;
        this.isNew = isNew;
        this.isSearchUsed = isSearchUsed; // <-- NEW assignment
        this.sourceResultsJson = sourceResultsJson; // <-- NEW assignment
    }

    // --- Ignore Fields ---

    @Ignore
    public boolean isNew = true;

    // --- Getters ---

    public int getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }
    
    @NonNull
    public String getMessageType() {
        return messageType;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public String getImageUri() {
        return imageUri;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }
    
    // --- NEW GETTERS for Source Information ---
    
    public boolean isSearchUsed() {
        return isSearchUsed;
    }

    public String getSourceResultsJson() {
        return sourceResultsJson;
    }
    
    /**
     * Deserializes the JSON string back into a List of SourceResult objects.
     * This is the method the NewChatAdapter will call.
     * @return List of SourceResult objects or an empty list if null/invalid.
     */
    @Ignore
    public List<SourceResult> getSources() {
        if (sourceResultsJson == null || sourceResultsJson.isEmpty() || !isSearchUsed) {
            return Collections.emptyList();
        }
        
        try {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<SourceResult>>() {}.getType();
            return gson.fromJson(sourceResultsJson, listType);
        } catch (Exception e) {
            // Handle parsing errors
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
