package com.xxq.xlai;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// ChatSessionEntity.class AND incremented version to 3
@Database(entities = {ChatMessageEntity.class, NewChatMessageEntity.class, ChatSessionEntity.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ChatDao chatDao();
    public abstract NewChatDao newChatDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "chat_database")
                            // This will wipe data because we changed schema (Version 2 -> 3)
                            // This is fine for development.
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
