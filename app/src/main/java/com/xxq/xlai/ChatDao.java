package com.xxq.xlai;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ChatDao {

    // ========================
    // SESSION OPERATIONS (For the Drawer)
    // ========================
    @Insert
    long insertSession(ChatSessionEntity session); // Returns the new ID

    @Query("SELECT * FROM chat_sessions ORDER BY lastTimestamp DESC")
    LiveData<List<ChatSessionEntity>> getAllSessions();

    @Query("SELECT * FROM chat_sessions WHERE sessionId = :sessionId LIMIT 1")
    ChatSessionEntity getSessionByIdSync(long sessionId);
   
    @Query("DELETE FROM chat_messages WHERE timestamp = :timestamp")
    void deleteMessageByTimestamp(long timestamp);
    
   @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
   ChatMessageEntity getLastMessageForSession(long sessionId);

   @Update
   void updateMessage(ChatMessageEntity message);
    @Update
    void updateSession(ChatSessionEntity session); // To update timestamp when new msg arrives

    //  ADD SESSION DELETION METHOD
    @Query("DELETE FROM chat_sessions WHERE sessionId = :sessionId")
    void deleteSession(long sessionId); // Deleted in MainActivity:390
    
    // Returns a plain List for use in background threads (executorService)
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<ChatMessageEntity> getMessagesForSessionSync(long sessionId);
   
    //  Deletes any session that has 0 messages
    @Query("DELETE FROM chat_sessions WHERE sessionId NOT IN (SELECT DISTINCT sessionId FROM chat_messages)")
    void deleteEmptySessions();

       @Query("SELECT s.sessionId, s.title, " +
           "COALESCE(" +
           "  (SELECT m.message FROM chat_messages m WHERE m.sessionId = s.sessionId AND m.message LIKE '%' || :query || '%' ORDER BY m.timestamp DESC LIMIT 1), " +
           "  (SELECT m.message FROM chat_messages m WHERE m.sessionId = s.sessionId ORDER BY m.timestamp ASC LIMIT 1), " +
           "  'No messages yet'" +
           ") AS matchedPrompt, " +
           "s.lastTimestamp " +
           "FROM chat_sessions s " +
           "WHERE s.title LIKE '%' || :query || '%' " +
           "OR EXISTS (SELECT 1 FROM chat_messages m WHERE m.sessionId = s.sessionId AND m.message LIKE '%' || :query || '%') " +
           "ORDER BY s.lastTimestamp DESC")
    List<ChatSearchResult> searchChatsWithSnippetSync(String query);

    
    
    // ========================
    // MESSAGE OPERATIONS (For the Chat Window)
    // ========================
    
    @Insert
    void insertMessage(ChatMessageEntity chatMessage);

    // Only get messages for the specific OPEN window
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    LiveData<List<ChatMessageEntity>> getMessagesForSession(long sessionId);

      @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND timestamp >= :fromTimestamp")
    void deleteMessagesFromTimestamp(long sessionId, long fromTimestamp);
    
    // Keep your sync method if you use it for logic, but filter by session usually
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 10")
    List<ChatMessageEntity> getLast10MessagesSync(long sessionId);

   @Query("UPDATE chat_sessions SET lastTimestamp = :newTimestamp WHERE sessionId = :sessionId")
    void updateSessionTimestamp(long sessionId, long newTimestamp);
    @Query("UPDATE chat_sessions SET title = :newTitle WHERE sessionId = :sessionId")
    void updateSessionTitle(long sessionId, String newTitle);

    //  ADD MESSAGE DELETION METHOD (Crucial for cleanup)
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    void deleteMessagesForSession(long sessionId);
}
