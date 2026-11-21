package com.xxq.xlai;
import android.widget.PopupWindow;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.widget.ScrollView;
import androidx.recyclerview.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import android.widget.LinearLayout;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
public class BYOKActivity extends AppCompatActivity {

    private RelativeLayout btnAddApiKey;
    private RecyclerView recyclerApiKeys;
    private ApiKeyAdapter apiKeyAdapter;
   private boolean isApiKeysVisible = false;
    private List<String> apiKeys = new ArrayList<>();
    private SharedPreferences prefs;
    private boolean isListVisible = false; // Track the state
    private ImageView ivExpandToggle;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_byok);

       // setContentView
       androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

    androidx.core.view.WindowInsetsControllerCompat windowInsetsController = 
    androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

   if (windowInsetsController != null) {
    // If the app is in Night Mode, icons should be light (false). 
    // If Light Mode, icons should be dark (true).
    boolean isDarkMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                         == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    
    // This forces the status bar icons to be dark in light mode, and light in dark mode
    windowInsetsController.setAppearanceLightStatusBars(!isDarkMode);
   }
        
        
    Toolbar toolbar = findViewById(R.id.byokToolbar);
    setSupportActionBar(toolbar);
    if (getSupportActionBar() != null) {
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    getSupportActionBar().setDisplayShowHomeEnabled(true);
    // We will Override the system black arrow with our theme-aware one
    getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back_arrow);
   }

        
       //  Initialize the Provider List Toggle
       LinearLayout providersHeader = findViewById(R.id.providersHeader);
       LinearLayout providersListContainer = findViewById(R.id.providersListContainer);
       ImageView ivProvidersToggle = findViewById(R.id.ivProvidersToggle);

     final boolean[] isProvidersVisible = {false};

   providersHeader.setOnClickListener(v -> {
    isProvidersVisible[0] = !isProvidersVisible[0];
    if (isProvidersVisible[0]) {
        // Expand
        providersListContainer.setVisibility(View.VISIBLE);
        ivProvidersToggle.setImageResource(R.drawable.ic_expand_more);
    } else {
        // Collapse
        providersListContainer.setVisibility(View.GONE);
        ivProvidersToggle.setImageResource(R.drawable.ic_chevron_right);
    }
   });
        
    // 2. Initialize UI
    btnAddApiKey = findViewById(R.id.btnAddApiKey);
    recyclerApiKeys = findViewById(R.id.recyclerApiKeys);
    recyclerApiKeys.setLayoutManager(new LinearLayoutManager(this));
    prefs = getSharedPreferences("api_keys", MODE_PRIVATE);

    // 3. Wire up the + icon and the > toggle icon
    ImageView ivAddIcon = findViewById(R.id.ivAddIcon);
    ivExpandToggle = findViewById(R.id.ivExpandToggle);

    // Clicking the + icon opens the dialog
    ivAddIcon.setOnClickListener(v -> showAddApiKeyDialog());
    
    // Clicking the text or the rest of the bar can also open the dialog
    btnAddApiKey.setOnClickListener(v -> showAddApiKeyDialog());

     // Clicking the > icon toggles the list visibility
    ivExpandToggle.setOnClickListener(v -> {
    isApiKeysVisible = !isApiKeysVisible;
    if (isApiKeysVisible) {
        recyclerApiKeys.setVisibility(View.VISIBLE);
        ivExpandToggle.setImageResource(R.drawable.ic_expand_more); 
    } else {
        recyclerApiKeys.setVisibility(View.GONE);
        ivExpandToggle.setImageResource(R.drawable.ic_chevron_right); 
    }
   });

       
         
        // Setup the new Toolbar + Button
      ImageView btnAddToolbarCustom = findViewById(R.id.btnAddToolbarCustom);

      btnAddToolbarCustom.setOnClickListener(v -> {
    

    // 2. Add the card and save
    addCustomProviderCard(null);
    saveCustomProviders();
   });
        
    // 4. Set up adapter
    apiKeys = new ArrayList<>();
    apiKeyAdapter = new ApiKeyAdapter(apiKeys);
    recyclerApiKeys.setAdapter(apiKeyAdapter);

    // 5. Load saved keys
    loadApiKeys();
    loadCustomProviders();
        
  }

    private void loadApiKeys() {
        apiKeys.clear();
        int count = prefs.getInt("key_count", 0);
        for (int i = 0; i < count; i++) {
            String key = prefs.getString("key_" + i, null);
            if (key != null) {
                apiKeys.add(key);
            }
        }
        apiKeyAdapter.notifyDataSetChanged();
    }
                 
      private void saveApiKey(String key) {
    int count = prefs.getInt("key_count", 0);
    
    String provider = ApiKeyDetector.detectProvider(key);
    if (provider == null || "Unknown Provider".equals(provider)) {
        Toast.makeText(this, "Warning: Provider not recognized.", Toast.LENGTH_LONG).show();
    } else {
        Toast.makeText(this, provider + " key saved!", Toast.LENGTH_SHORT).show();
    }

    prefs.edit()
            .putString("key_" + count, key)
            .putInt("key_count", count + 1)
            .apply();
            
    apiKeys.add(key);
    apiKeyAdapter.notifyItemInserted(apiKeys.size() - 1);

    // Force the list to open if it's hidden
    if (!isApiKeysVisible) {
        isApiKeysVisible = true;
        recyclerApiKeys.setVisibility(View.VISIBLE);
        ivExpandToggle.setImageResource(R.drawable.ic_expand_more);
    }

    // USE NESTED SCROLL VIEW'S BUILT-IN FULL SCROLL
    androidx.core.widget.NestedScrollView mainScrollView = findViewById(R.id.mainScrollView);
    if (mainScrollView != null) {
        mainScrollView.post(() -> {
            mainScrollView.fullScroll(View.FOCUS_DOWN);
        });
    }
  }
    
    private void deleteApiKey(int position) {
        String key = apiKeys.get(position);
        int count = prefs.getInt("key_count", 0);
        
        // Shift keys AND their saved models down
        for (int i = position; i < count - 1; i++) {
            String nextKey = prefs.getString("key_" + (i + 1), null);
            String nextModels = prefs.getString("models_for_key_" + (i + 1), ""); 
            
            prefs.edit()
                .putString("key_" + i, nextKey)
                .putString("models_for_key_" + i, nextModels) 
                .apply();
        }
        
        // Clean up the very last orphan entry
        prefs.edit()
                .remove("key_" + (count - 1))
                .remove("models_for_key_" + (count - 1)) 
                .putInt("key_count", count - 1)
                .apply();
                
        apiKeys.remove(position);
        apiKeyAdapter.notifyDataSetChanged();
    }

    // DADDY'S DIMMING FIX
    private void dimBackground(boolean dim) {
        android.view.Window window = getWindow();
        android.view.WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.dimAmount = dim ? 0.5f : 0.0f; // 50% dim
        window.setAttributes(layoutParams);
        if (dim) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    private void showAddApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_api_key, null);
        builder.setView(dialogView);

        EditText etApiKey = dialogView.findViewById(R.id.dialog_input);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnSave = dialogView.findViewById(R.id.btn_save);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(BYOKActivity.this, "API key cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            saveApiKey(key);
            dialog.dismiss();
            Toast.makeText(BYOKActivity.this, "API key saved!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

       //  DYNAMIC PROGRESSIVE-DISCLOSURE DIALOG
    private void showAddCustomModelDialog(int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_model_byok, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        LinearLayout modelsContainer = dialogView.findViewById(R.id.models_input_container);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnSave = dialogView.findViewById(R.id.btn_set_value);

        List<View> rowViews = new ArrayList<>();

        // Load existing models if previously saved
        String existingModels = prefs.getString("models_for_key_" + position, "");
        String[] modelArray = existingModels.isEmpty() ? new String[0] : existingModels.split(",");

        if (modelArray.length > 0) {
            for (String modelName : modelArray) {
                addNewModelRow(modelsContainer, rowViews, modelName.trim());
            }
        } else {
            // Start with one empty primary row
            addNewModelRow(modelsContainer, rowViews, "");
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (View row : rowViews) {
                EditText et = row.findViewById(R.id.et_model_string);
                String val = et.getText().toString().trim();
                if (!val.isEmpty()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(val);
                }
            }

            prefs.edit().putString("models_for_key_" + position, sb.toString()).apply();
            apiKeyAdapter.notifyItemChanged(position);
            dialog.dismiss();
            Toast.makeText(BYOKActivity.this, "Models saved!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void addNewModelRow(LinearLayout container, List<View> rowViews, String initialText) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_dialog_model_row, container, false);
        EditText etModel = row.findViewById(R.id.et_model_string);
        ImageView btnAction = row.findViewById(R.id.btn_row_action);

        int rowIndex = rowViews.size();
        if (rowIndex == 0) {
            etModel.setHint("Primary Model (e.g. meta-llama/llama-3-8b)");
        } else {
            etModel.setHint("Fallback " + rowIndex + " (e.g. google/gemini-flash)");
        }

        if (!initialText.isEmpty()) {
            etModel.setText(initialText);
        }

        container.addView(row);
        rowViews.add(row);

        // Update actions across all rows
        refreshRowActions(container, rowViews);

        // Listen for typing on this field
        etModel.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshRowActions(container, rowViews);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void refreshRowActions(LinearLayout container, List<View> rowViews) {
        int total = rowViews.size();
        for (int i = 0; i < total; i++) {
            View row = rowViews.get(i);
            EditText et = row.findViewById(R.id.et_model_string);
            ImageView btn = row.findViewById(R.id.btn_row_action);
            boolean hasText = !et.getText().toString().trim().isEmpty();

            if (i == total - 1) {
                // LAST ROW: Show '+' ONLY when the user has typed at least 1 character
                btn.setImageResource(R.drawable.ic_add);
                btn.setColorFilter(android.graphics.Color.parseColor("#2196F3"));
                btn.setVisibility(hasText ? View.VISIBLE : View.GONE);
                btn.setOnClickListener(v -> addNewModelRow(container, rowViews, ""));
            } else {
                // EARLIER ROWS: Hide '+' completely so only the current bottom row can expand
                btn.setVisibility(View.GONE);
            }
        }
    }


    // PROGRAMMATIC ROW GENERATOR FOR FALLBACKS
    private void addFallbackInputRow(LinearLayout container, List<EditText> inputList, String prefillText) {
        EditText fallbackInput = new EditText(this);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, (int) (16 * getResources().getDisplayMetrics().density), 0, 0); 
        
        fallbackInput.setLayoutParams(params);
        fallbackInput.setPadding(32, 32, 32, 32);
        
        fallbackInput.setBackgroundResource(android.R.drawable.edit_text);
        fallbackInput.setTextColor(android.graphics.Color.parseColor("#E3E2E6"));
        fallbackInput.setHintTextColor(android.graphics.Color.parseColor("#666666"));
        fallbackInput.setHint("Fallback Model (e.g. google/gemini-flash)");
        fallbackInput.setInputType(android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        
        if (!prefillText.isEmpty()) {
            fallbackInput.setText(prefillText);
        }

        container.addView(fallbackInput);
        inputList.add(fallbackInput);
    }

   @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

   // Maps the Provider Name to the actual logo drawable
  private int getProviderIcon(String provider) {
    if (provider == null) return R.drawable.ic_unknown; // Default fallback icon
    
    switch (provider) {
        case ApiKeyDetector.PROVIDER_OPENAI: return R.drawable.ic_openai;
        case ApiKeyDetector.PROVIDER_ANTHROPIC: return R.drawable.ic_anthropic;
        case ApiKeyDetector.PROVIDER_GOOGLE: return R.drawable.ic_google;
        case ApiKeyDetector.PROVIDER_OPENROUTER: return R.drawable.ic_openrouter;
        case ApiKeyDetector.PROVIDER_XAI: return R.drawable.ic_spcxai;
        case ApiKeyDetector.PROVIDER_DEEPSEEK: return R.drawable.ic_deepseek;
        case ApiKeyDetector.PROVIDER_GROQ: return R.drawable.ic_groq;
        case ApiKeyDetector.PROVIDER_MISTRAL: return R.drawable.ic_mistral_ai;
        case ApiKeyDetector.PROVIDER_NVIDIA: return R.drawable.ic_nvidia_logo;
        case ApiKeyDetector.PROVIDER_TOGETHER: return R.drawable.ic_together;
        default: return R.drawable.ic_unknown;
    }
  }
    
    
    

   @Override
  protected void onPause() {
    super.onPause();
    saveCustomProviders();
  }
    
  
  

//  The Dynamic Accordion Generator
  private void addCustomProviderCard(JSONObject existingData) {
    LinearLayout container = findViewById(R.id.customProvidersContainer);
    View card = LayoutInflater.from(this).inflate(R.layout.item_custom_provider_card, container, false);

    // Find inner views
    LinearLayout cardHeader = card.findViewById(R.id.cardHeader);
    LinearLayout cardContent = card.findViewById(R.id.cardContent);
    ImageView ivCardToggle = card.findViewById(R.id.ivCardToggle);
    View cardDivider = card.findViewById(R.id.cardDivider); 
        
    EditText etName = card.findViewById(R.id.etCustomName);
    EditText etUrl = card.findViewById(R.id.etCustomUrl);
    EditText etApiKey = card.findViewById(R.id.etCustomApiKey);
    EditText etModel = card.findViewById(R.id.etCustomModel);
    LinearLayout btnDelete = card.findViewById(R.id.btnDeleteProvider);

    // Populate if loading saved data
    if (existingData != null) {
        try {
            etName.setText(existingData.optString("name", ""));
            etUrl.setText(existingData.optString("url", ""));
            etApiKey.setText(existingData.optString("apiKey", ""));
            etModel.setText(existingData.optString("model", ""));
        } catch (Exception e) { e.printStackTrace(); }
    }

   final boolean[] isExpanded = {false};
        cardHeader.setOnClickListener(v -> {
            isExpanded[0] = !isExpanded[0];
            if (isExpanded[0]) {
                cardContent.setVisibility(View.VISIBLE);
                cardDivider.setVisibility(View.VISIBLE); // Show divider
                ivCardToggle.setImageResource(R.drawable.ic_expand_more);
            } else {
                cardContent.setVisibility(View.GONE);
                cardDivider.setVisibility(View.GONE); // Hide divider
                ivCardToggle.setImageResource(R.drawable.ic_chevron_right);
            }
        });

    // Delete Logic
    btnDelete.setOnClickListener(v -> {
        if (container.getChildCount() > 1) {
            container.removeView(card);
            saveCustomProviders(); 
        } else {
            boolean isNameEmpty = etName.getText().toString().trim().isEmpty();
            boolean isUrlEmpty = etUrl.getText().toString().trim().isEmpty();
            boolean isApiKeyEmpty = etApiKey.getText().toString().trim().isEmpty();
            boolean isModelEmpty = etModel.getText().toString().trim().isEmpty();

            if (isNameEmpty && isUrlEmpty && isApiKeyEmpty && isModelEmpty) {
                Toast.makeText(this, "All fields are empty already", Toast.LENGTH_SHORT).show();
            } else {
                etName.setText("");
                etUrl.setText("");
                etApiKey.setText("");
                etModel.setText("");
                Toast.makeText(this, "Fields cleared. Card cannot be deleted.", Toast.LENGTH_SHORT).show();
                saveCustomProviders();
            }
        }
    });

    container.addView(card);
    
    // Note: We do NOT expand it automatically. It starts closed.
  }
    
   // Saving all cards to SharedPreferences
  private void saveCustomProviders() {
    LinearLayout container = findViewById(R.id.customProvidersContainer);
    JSONArray jsonArray = new JSONArray();

    for (int i = 0; i < container.getChildCount(); i++) {
        View card = container.getChildAt(i);
        EditText etName = card.findViewById(R.id.etCustomName);
        EditText etUrl = card.findViewById(R.id.etCustomUrl);
        EditText etApiKey = card.findViewById(R.id.etCustomApiKey);
        EditText etModel = card.findViewById(R.id.etCustomModel);

        String url = etUrl.getText().toString().trim();
        if (!url.isEmpty()) { // Only save if a URL is provided
            try {
                JSONObject obj = new JSONObject();
                obj.put("name", etName.getText().toString().trim());
                obj.put("url", url);
                obj.put("apiKey", etApiKey.getText().toString().trim());
                obj.put("model", etModel.getText().toString().trim());
                jsonArray.put(obj);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    prefs.edit().putString("custom_providers", jsonArray.toString()).apply();
  }

   //  Loading cards on startup
   private void loadCustomProviders() {
    LinearLayout container = findViewById(R.id.customProvidersContainer);
    container.removeAllViews();
    
    String savedJson = prefs.getString("custom_providers", "[]");
    try {
        JSONArray jsonArray = new JSONArray(savedJson);
        if (jsonArray.length() == 0) {
            addCustomProviderCard(null); // Always show at least one empty card
        } else {
            for (int i = 0; i < jsonArray.length(); i++) {
                addCustomProviderCard(jsonArray.getJSONObject(i));
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
        addCustomProviderCard(null); // Fallback
    }
  }
    
    
    
    // API Key Adapter
    private class ApiKeyAdapter extends RecyclerView.Adapter<ApiKeyViewHolder> {
        private final List<String> keys;

        ApiKeyAdapter(List<String> keys) {
            this.keys = keys;
        }

        @NonNull
        @Override
        public ApiKeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_api_key, parent, false);
            return new ApiKeyViewHolder(view);
        }

       @Override
        public void onBindViewHolder(@NonNull ApiKeyViewHolder holder, int position) {
    String key = keys.get(position);
    
    // 1. Detect the Provider
    String provider = ApiKeyDetector.detectProvider(key);
    
    // 2. Mask the key for preview
    String masked = key.length() > 12 ? key.substring(0, 4) + "..." + key.substring(key.length() - 8) : key;
    holder.tvApiKeyPreview.setText(masked);

    // SET THE PROVIDER PILL (ICON + NAME)
    if (provider != null && !provider.equals("Unknown Provider")) {
        holder.providerPill.setVisibility(View.VISIBLE);
        holder.tvProviderName.setText(provider);
        holder.ivProviderIcon.setImageResource(getProviderIcon(provider));
    } else {
        holder.providerPill.setVisibility(View.GONE); // Hide pill if unknown
    }

    // CHECK AND DISPLAY SAVED MODELS
    String savedModels = prefs.getString("models_for_key_" + position, "");
    
    if (!savedModels.isEmpty()) {
        String primaryModel = savedModels.split(",")[0]; 
        holder.tvCustomModel.setText("Active Model: " + primaryModel);
        holder.tvCustomModel.setTextColor(android.graphics.Color.parseColor("#888888")); 
    } else {
        // If it's Google or OpenRouter, defaults are safe.
        if (ApiKeyDetector.PROVIDER_GOOGLE.equals(provider) || ApiKeyDetector.PROVIDER_OPENROUTER.equals(provider)) {
            holder.tvCustomModel.setText("Using default fallback model");
            holder.tvCustomModel.setTextColor(android.graphics.Color.parseColor("#888888")); 
        } else {
            // For OpenAI, Anthropic, etc., a model MUST be specified
            holder.tvCustomModel.setText(" Model required! Tap to add.");
            holder.tvCustomModel.setTextColor(android.graphics.Color.parseColor("#F44336")); 
        }
    }

        //  CLICK LISTENERS
        holder.tvCustomModel.setOnClickListener(v -> showAddCustomModelDialog(position));

      holder.btnView.setOnClickListener(v -> {
        View popupView = LayoutInflater.from(v.getContext()).inflate(R.layout.popup_view_key, null);
        TextView tvFullKey = popupView.findViewById(R.id.tvFullKey);
        tvFullKey.setText(key);

        final PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        popupWindow.setElevation(10f);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(true);

        int[] location = new int[2];
        v.getLocationOnScreen(location);
        popupWindow.showAtLocation(v, android.view.Gravity.NO_GRAVITY,
                location[0],
                location[1] - v.getHeight() - 10);
      });

         holder.btnDelete.setOnClickListener(v -> {
          deleteApiKey(position);
          Toast.makeText(BYOKActivity.this, "API key deleted", Toast.LENGTH_SHORT).show();
        });

          holder.itemView.setOnLongClickListener(v -> {
           Toast.makeText(BYOKActivity.this, "Full key: " + key, Toast.LENGTH_LONG).show();
           return true;
         });
     }


        @Override
        public int getItemCount() {
            return keys.size();
        }
    }

   static class ApiKeyViewHolder extends RecyclerView.ViewHolder {
    TextView tvApiKeyPreview;
    ImageButton btnDelete;
    ImageButton btnView;
    TextView tvCustomModel; 
    
    // Pill Views
    LinearLayout providerPill;
    ImageView ivProviderIcon;
    TextView tvProviderName;

    ApiKeyViewHolder(View itemView) {
        super(itemView);
        tvApiKeyPreview = itemView.findViewById(R.id.tvApiKeyPreview);
        btnDelete = itemView.findViewById(R.id.btnDelete);
        btnView = itemView.findViewById(R.id.btnView);
        tvCustomModel = itemView.findViewById(R.id.tvCustomModel); 
        
        // Bind pill
        providerPill = itemView.findViewById(R.id.providerPill);
        ivProviderIcon = itemView.findViewById(R.id.ivProviderIcon);
        tvProviderName = itemView.findViewById(R.id.tvProviderName);
      }
   }
}