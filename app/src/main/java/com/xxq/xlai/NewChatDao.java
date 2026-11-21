package com.xxq.xlai;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * ⚠️ NOTE: For this DAO to work with a separate table, you would typically 
 * need a corresponding 'NewChatMessageEntity' that maps to a different table 
 * name (e.g., 'new_chat_messages') in your AppDatabase.
 * * Since the ChatViewModel logic assumes 'ChatMessageEntity', we'll keep the
 * entity name consistent for simplicity in this refactoring exercise, but 
 * architecturally, this DAO should target a different persistence mechanism
 * or a subset of the main table (e.g., using a WHERE clause based on chat_id).
 */
@Dao
public interface NewChatDao {
    
    // Insert a new chat message into the designated 'new chat' storage
    @Insert
    void insert(NewChatMessageEntity chatMessage);

    // Get all messages for the new chat, ordered by timestamp
    @Query("SELECT * FROM new_chat_messages ORDER BY timestamp ASC")
    LiveData<List<NewChatMessageEntity>> getAllMessages();

    // Get the last 10 messages for context (Sync version)
    @Query("SELECT * FROM new_chat_messages ORDER BY timestamp DESC LIMIT 13")
    List<NewChatMessageEntity> getLast13MessagesSync();
    
    // Optional: A method to clear the new chat history when the activity closes
    @Query("DELETE FROM new_chat_messages")
    void deleteAllMessages();
}
