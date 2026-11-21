package com.xxq.xlai;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class NewChatViewModelFactory implements ViewModelProvider.Factory {
    // ⚠️ KEY CHANGE 1: Store NewChatDao instead of ChatDao
    private final NewChatDao newChatDao; 
    private final Application application;

    // ⚠️ KEY CHANGE 2: Accept NewChatDao in the constructor
    public NewChatViewModelFactory(@NonNull Application application, NewChatDao newChatDao) {
        this.application = application;
        this.newChatDao = newChatDao;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(NewChatViewModel.class)) {
            // ⚠️ KEY CHANGE 3 (Line 24 Fix): Pass the NewChatDao instance
            return (T) new NewChatViewModel(application, newChatDao); 
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
