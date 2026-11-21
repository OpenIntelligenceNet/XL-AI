package com.xxq.xlai;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class ChatViewModelFactory implements ViewModelProvider.Factory {
    private final ChatDao chatDao;
    private final Application application;

    public ChatViewModelFactory(@NonNull Application application, ChatDao chatDao) {
        this.application = application;
        this.chatDao = chatDao;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ChatViewModel.class)) {
            // ⚠️ Corrected line: pass both application and chatDao
            return (T) new ChatViewModel(application, chatDao);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
