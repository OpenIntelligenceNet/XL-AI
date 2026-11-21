package com.xxq.xlai;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class Settings extends AppCompatActivity {

    private RelativeLayout btnSystemPrompt;
    private RelativeLayout btnBringYourOwnKey; // 🏆 DADDY'S NEW BUTTON

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

       // 1. Setup Toolbar and Back Navigation
      Toolbar toolbar = findViewById(R.id.settingsToolbar);
       setSupportActionBar(toolbar);
       if (getSupportActionBar() != null) {
         getSupportActionBar().setDisplayHomeAsUpEnabled(true);
         getSupportActionBar().setDisplayShowHomeEnabled(true);
    
        // 🎯 THE FIX: Use our theme-aware custom back arrow
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back_arrow);
       }

        // 2. Initialize UI Components
        btnSystemPrompt = findViewById(R.id.btnSystemPrompt);
        btnBringYourOwnKey = findViewById(R.id.btnBringYourOwnKey); // 🏆 BIND IT, YOU CRIED OVER THIS PREVIOSLY

        // 3. Setup Click Listeners
        btnSystemPrompt.setOnClickListener(v -> {
            // ✅ Launch the SystemPromptActivity directly
            Intent intent = new Intent(this, SystemPromptActivity.class);
            startActivity(intent);
        });

        // 🏆 DADDY'S NEW LISTENER: Launch BYOK Activity
        btnBringYourOwnKey.setOnClickListener(v -> {
            Intent intent = new Intent(this, BYOKActivity.class);
            startActivity(intent);
        });
    }

    // 4. Handle Back Arrow Click (<-)
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Modern, safe way to handle the back button click
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}