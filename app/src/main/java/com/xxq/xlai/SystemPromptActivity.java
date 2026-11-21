package com.xxq.xlai;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.Toolbar;

public class SystemPromptActivity extends AppCompatActivity {

    private EditText etUserNickname;
    private EditText etSystemPrompt;
    private AppCompatButton btnSubmitPrompt;
    private AppCompatButton btnDeletePrompt; // Reference variable added

    // SharedPreferences keys for storing the values
    private static final String PREFS_NAME = "SystemPromptPrefs";
    private static final String KEY_USER_NICKNAME = "user_nickname";
    private static final String KEY_SYSTEM_PROMPT = "custom_system_prompt";

   @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_system_prompt);

    // 1. Setup Toolbar with Back Arrow Action
    Toolbar toolbar = findViewById(R.id.systemPromptToolbar); 
    setSupportActionBar(toolbar);
    
    if (getSupportActionBar() != null) {
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        
        // Override the invisible black system arrow with our theme-aware one
        getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back_arrow);
    }

    // 2. Initialize UI Input Elements
    etUserNickname = findViewById(R.id.etUserNickname);
    etSystemPrompt = findViewById(R.id.etSystemPrompt);
    btnSubmitPrompt = findViewById(R.id.btnSubmitPrompt);
    btnDeletePrompt = findViewById(R.id.btnDeletePrompt); // Bound UI object

    // 3. Load Saved Settings Data (if any exists)
    loadSavedCustomizations();

    // 4. Handle Save/Submit Button Clicks
    btnSubmitPrompt.setOnClickListener(v -> saveCustomizations());

    // 5. Handle Delete/Reset Button Clicks
    btnDeletePrompt.setOnClickListener(v -> removeCustomizations());
  }

    /**
     * Pulls the stored user configurations from local storage and hydrates the UI text fields.
     */
    private void loadSavedCustomizations() {
        String savedNickname = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_USER_NICKNAME, "");
        String savedPrompt = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_SYSTEM_PROMPT, "");

        etUserNickname.setText(savedNickname);
        etSystemPrompt.setText(savedPrompt);
    }

    /**
     * Pulls the strings entered by the user, validates them, and updates local memory tracking.
     */
    private void saveCustomizations() {
        String inputNickname = etUserNickname.getText().toString().trim();
        String inputPrompt = etSystemPrompt.getText().toString().trim();

        //  DYNAMIC CHECK: If BOTH are empty, reject the save and warn the user.
        if (inputNickname.isEmpty() && inputPrompt.isEmpty()) {
            Toast.makeText(this, "Both fields are empty. Nothing to save!", Toast.LENGTH_SHORT).show();
            return; 
        }

        //  If we reach here, at least one field has data (or both). Save them safely.
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_USER_NICKNAME, inputNickname)
                .putString(KEY_SYSTEM_PROMPT, inputPrompt)
                .apply();

        Toast.makeText(this, "Customizations saved successfully!", Toast.LENGTH_SHORT).show();
        finish(); // Closes the settings screen and goes back
    }

    /**
     * Completely wipes out local stored instructions and resets text fields.
     */
    private void removeCustomizations() {
        // 1. Wipe properties out of local data storage
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(KEY_USER_NICKNAME)
                .remove(KEY_SYSTEM_PROMPT)
                .apply();

        // 2. Clear UI visually instantly
        etUserNickname.setText("");
        etSystemPrompt.setText("");

        Toast.makeText(this, "Custom instructions removed. Reverted to default!", Toast.LENGTH_SHORT).show();
        finish(); // Instantly returns user to chat window running under default system configurations
    }

    // 6. Intercept up navigation actions (<- Arrow) to terminate current screen instance safely
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
