package com.xxq.xlai;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages")
public class ChatMessageEntity {
    
    @PrimaryKey(autoGenerate = true)
    public int id;

    //  NEW FIELD: This links the message to a specific conversation in your drawer
    @ColumnInfo(index = true)
    public long sessionId; 
    
    @NonNull
    public String message;
    
    public int type;
    public long timestamp;
    
    // Fields for Flask/Server response structure
    @NonNull
    public String messageType; 
    public String language;
    public String imageUri;

    //  UPDATED MAIN CONSTRUCTOR: Now requires 'sessionId'
    public ChatMessageEntity(long sessionId, @NonNull String message, int type, long timestamp, @NonNull String messageType, String language, String imageUri) {
        this.sessionId = sessionId; // <--- Set the session ID
        this.message = message;
        this.type = type;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.language = language;
        this.imageUri = imageUri;
    }

    //  UPDATED IGNORED CONSTRUCTOR (For internal use/isNew flag)
    @Ignore
    public ChatMessageEntity(long sessionId, @NonNull String message, int type, long timestamp, @NonNull String messageType, String language, String imageUri, boolean isNew) {
        this.sessionId = sessionId; // <--- Set the session ID here too
        this.message = message;
        this.type = type;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.language = language;
        this.imageUri = imageUri;
        this.isNew = isNew;
    }

    @Ignore
    public boolean isNew = true;

    // === GETTERS ===
    public long getSessionId() {
        return sessionId;
    }

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

    
    
    public boolean isOfflineModel = false;

    // Fixes the "cannot find symbol: method isOfflineModel()" error
    public boolean isOfflineModel() {
        return isOfflineModel;
    }

   private boolean isStreaming;

    public boolean isStreaming() {
    //  DADDY'S FIX: If the message was loaded from the database (id > 0), 
    // it's obviously finished streaming!
    if (id > 0) return false;
    return isStreaming;
   }

    public void setStreaming(boolean streaming) {
    isStreaming = streaming;
   }
    
    public void setOfflineModel(boolean offlineModel) {
        this.isOfflineModel = offlineModel;
    }

    
    public void setMessage(String message) {
        this.message = message;
    }
    
}
