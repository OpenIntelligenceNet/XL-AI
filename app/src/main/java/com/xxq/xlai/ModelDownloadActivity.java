package com.xxq.xlai;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;

public class ModelDownloadActivity extends AppCompatActivity {

    private TextView downloadBtn;
    private ImageButton deleteBtn; 
    private LinearLayout progressContainer;
    private ProgressBar progressBar;
    private TextView percentText, statusText, modelSizeText;
    private TextView skipBtn;
    private ImageButton cancelBtn;
    private LinearLayout modelCardsContainer;
    private TextView customDownloadBtn; 

    private boolean isSizeDynamicSet = false;
    private volatile boolean isCancelled = false; 
    private String currentDownloadingFilename = "model.gguf";
    private int currentDownloadingModelIndex = -1;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private Runnable pendingDownloadAction = null;

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

     private final BroadcastReceiver receiver = new DownloadReceiver();

    private class DownloadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            
            if (isCancelled) return;

            String action = intent.getAction();
            String filename = intent.getStringExtra("filename");
            String modelName = intent.getStringExtra("model_name");
            if (modelName == null) modelName = "AI Model";
            
            if ("DOWNLOAD_PROGRESS".equals(action)) {
                int progress = intent.getIntExtra("progress", 0);
                String status = intent.getStringExtra("status");
                String totalSize = intent.getStringExtra("total_size");

                if (filename.equals("model.gguf")) {
                    if (progressContainer.getVisibility() == View.GONE) {
                        downloadBtn.setVisibility(View.GONE);
                        deleteBtn.setVisibility(View.GONE);
                        progressContainer.setVisibility(View.VISIBLE);
                        currentDownloadingFilename = "model.gguf";
                    }

                    progressBar.setProgress(progress);
                    percentText.setText(progress + "%");
                    statusText.setText(status);
                    
                    if (!isSizeDynamicSet && totalSize != null) {
                        modelSizeText.setText(totalSize);
                        getSharedPreferences("download_prefs", MODE_PRIVATE)
                                .edit()
                                .putString("model_size_model.gguf", totalSize)
                                .apply();
                        isSizeDynamicSet = true;
                    }
                } else {
                    updateCustomModelProgress(filename, modelName, progress, status, totalSize);
                }
            }
            else if ("DOWNLOAD_COMPLETE".equals(action)) {
                if (filename == null) return;

                if (filename.equals("model.gguf")) {
                    android.widget.Toast.makeText(context, "Model ready for local inference!", android.widget.Toast.LENGTH_SHORT).show();
                    checkModelState();
                    refreshCustomModelCards();
                } 
                else {
                    android.content.SharedPreferences customPrefs = getSharedPreferences("custom_models", MODE_PRIVATE);
                    int count = customPrefs.getInt("custom_model_count", 0);
                    
                    boolean isFullyComplete = false;
                    
                    for (int i = 0; i < count; i++) {
                        String ggufName = customPrefs.getString("custom_model_" + i + "_filename", "");
                        String mmprojName = customPrefs.getString("custom_model_" + i + "_mmproj_filename", "");
                        boolean hasVision = customPrefs.getBoolean("custom_model_" + i + "_has_vision", false);

                        if (filename.equals(ggufName) || filename.equals(mmprojName)) {
                            if (modelCardsContainer != null) {
                                View progressLayout = modelCardsContainer.findViewWithTag("progress_layout_" + filename);
                                if (progressLayout != null) {
                                    TextView statusView = progressLayout.findViewWithTag("progress_status_" + filename);
                                    ProgressBar bar = progressLayout.findViewWithTag("progress_bar_" + filename);
                                    TextView percentView = progressLayout.findViewWithTag("progress_percent_" + filename);
                                    android.widget.ImageButton cancelBtn = progressLayout.findViewWithTag("cancel_btn_" + filename);
                                    
                                    if (bar != null) bar.setVisibility(View.GONE); 
                                    if (percentView != null) percentView.setVisibility(View.GONE);
                                    if (cancelBtn != null) cancelBtn.setVisibility(View.GONE);
                                    
                                    if (statusView != null) {
                                        if (filename.endsWith(".gguf") && hasVision) {
                                            statusView.setText("Model Downloaded, waiting for Vision adapter...");
                                            
                                            View mmprojLayout = modelCardsContainer.findViewWithTag("progress_layout_" + mmprojName);
                                            if (mmprojLayout != null) {
                                                TextView mmprojStatus = mmprojLayout.findViewWithTag("progress_status_" + mmprojName);
                                                if (mmprojStatus != null) mmprojStatus.setText("Connecting...");
                                            }
                                        } else {
                                            statusView.setText("Completed");
                                        }
                                    }
                                }
                            }

                            File ggufFile = new File(getFilesDir(), ggufName);
                            File mmprojFile = hasVision ? new File(getFilesDir(), mmprojName) : null;

                            boolean ggufDone = ggufFile.exists() && ggufFile.length() > 0;
                            boolean mmprojDone = !hasVision || (mmprojFile != null && mmprojFile.exists() && mmprojFile.length() > 0);

                            if (ggufDone && mmprojDone) {
                                customPrefs.edit().putBoolean("custom_model_" + i + "_downloaded", true).apply();
                                isFullyComplete = true;
                            }
                            break; 
                        }
                    }

                    if (isFullyComplete) {
                        android.widget.Toast.makeText(context, modelName + " ready for local inference!", android.widget.Toast.LENGTH_SHORT).show();
                        refreshCustomModelCards();
                    }
                }
            }
            else if ("DOWNLOAD_ERROR".equals(action)) {
                String error = intent.getStringExtra("error");
                android.widget.Toast.makeText(context, "Connection dropped: " + error, android.widget.Toast.LENGTH_LONG).show();
                resetUiToResume();            
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_download);

        downloadBtn = findViewById(R.id.download_button);
        deleteBtn = findViewById(R.id.delete_button);
        progressContainer = findViewById(R.id.download_progress_container);
        progressBar = findViewById(R.id.download_progress_bar);
        percentText = findViewById(R.id.download_percent);
        statusText = findViewById(R.id.download_status);
        skipBtn = findViewById(R.id.skip_button);
        cancelBtn = findViewById(R.id.cancel_download);
        modelSizeText = findViewById(R.id.model_size_text);
        modelCardsContainer = findViewById(R.id.model_cards_container);

        progressContainer.setVisibility(View.GONE);

        requestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (pendingDownloadAction != null) {
                if (!isGranted) {
                    Toast.makeText(this, "Notifications disabled. Downloading silently.", Toast.LENGTH_SHORT).show();
                }
                pendingDownloadAction.run();
                pendingDownloadAction = null;
            }
        });

        if (skipBtn != null) {
            skipBtn.setOnClickListener(v -> finish());
        }

        downloadBtn.setOnClickListener(v -> {
            if (downloadBtn.getText().toString().equals("Launch model")) {
                getSharedPreferences("model_prefs", MODE_PRIVATE)
                        .edit()
                        .putString("selected_model", "v3")
                        .apply();

                Intent returnIntent = new Intent(ModelDownloadActivity.this, MainActivity.class);
                returnIntent.putExtra("TRIGGER_MODEL_LOAD", true);
                returnIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(returnIntent);
                finish();
            } else {
                checkNotificationPermissionAndRun(() -> {
                    isCancelled = false;
                    currentDownloadingFilename = "model.gguf";
                    startDownload("https://huggingface.co/zaakirio/LFM2.5-1.2B-Instruct-Uncensored-GGUF/resolve/main/LFM2.5-1.2B-Instruct-Uncensored-Q8_0.gguf?download=true", currentDownloadingFilename, "LFM2.5 1.2B Instruct");
                });
            }
        });

        deleteBtn.setOnClickListener(v -> {
            File modelFile = new File(getFilesDir(), "model.gguf");
            if (modelFile.exists()) {
                modelFile.delete();
            }            
            getSharedPreferences("model_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("model_downloaded", false)
                    .apply();

            Toast.makeText(this, "Model deleted successfully", Toast.LENGTH_SHORT).show();
            checkModelState();
        });

        String savedSize = getSharedPreferences("download_prefs", MODE_PRIVATE)
                .getString("model_size_model.gguf", null);

        if (savedSize != null) {
            modelSizeText.setText(savedSize);
            isSizeDynamicSet = true;
        }
       
        cancelBtn.setOnClickListener(v -> {
            isCancelled = true;
            stopService(new Intent(ModelDownloadActivity.this, DownloadService.class));
            resetUiToResume();
            Toast.makeText(this, "Download cancelled", Toast.LENGTH_SHORT).show();
        });

        addCustomModelDownloadOption();
        checkModelState();
        refreshCustomModelCards();
    }

    private void checkNotificationPermissionAndRun(Runnable downloadAction) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                downloadAction.run();
            } else {
                pendingDownloadAction = downloadAction;
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            downloadAction.run();
        }
    }

    private void addCustomModelDownloadOption() {
        if (modelCardsContainer == null) return;

        customDownloadBtn = new TextView(this);
        customDownloadBtn.setText("➕ Download your own AI model");
        customDownloadBtn.setTextSize(16);
        customDownloadBtn.setTextColor(ContextCompat.getColor(this, R.color.brand_blue));
        customDownloadBtn.setGravity(android.view.Gravity.CENTER);
        customDownloadBtn.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
        customDownloadBtn.setClickable(true);
        customDownloadBtn.setFocusable(true);
        customDownloadBtn.setBackgroundResource(R.drawable.bg_model_download_outline);
        customDownloadBtn.setOnClickListener(v -> showCustomModelDialog());
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );        
        params.topMargin = dpToPx(16);
        customDownloadBtn.setLayoutParams(params);
        
        forceCustomButtonToBottom();
    }

    private void showCustomModelDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_download_model, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        View btnCancel = dialogView.findViewById(R.id.btn_cancel);
        View btnDownload = dialogView.findViewById(R.id.btn_download);
        com.google.android.material.textfield.TextInputEditText inputGguf = dialogView.findViewById(R.id.dialog_input);
        com.google.android.material.textfield.TextInputEditText inputMmproj = dialogView.findViewById(R.id.dialog_input_mmproj);
        com.google.android.material.textfield.TextInputLayout layoutMmproj = dialogView.findViewById(R.id.layout_mmproj_input);
        android.widget.CheckBox checkboxVision = dialogView.findViewById(R.id.checkbox_vision_support);

        checkboxVision.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                layoutMmproj.setVisibility(View.VISIBLE);
            } else {
                layoutMmproj.setVisibility(View.GONE);
                inputMmproj.setText("");
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        
        btnDownload.setOnClickListener(v -> {
            String ggufUrl = inputGguf.getText().toString().trim();
            String mmprojUrl = inputMmproj.getText().toString().trim();
            boolean isVisionEnabled = checkboxVision.isChecked();

            if (ggufUrl.isEmpty() || !ggufUrl.contains("huggingface.co")) {
                Toast.makeText(this, "Invalid HuggingFace GGUF URL!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isVisionEnabled && (mmprojUrl.isEmpty() || !mmprojUrl.contains("huggingface.co"))) {
                Toast.makeText(this, "Invalid HuggingFace MMPROJ URL!", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            checkNotificationPermissionAndRun(() -> downloadCustomModel(ggufUrl, mmprojUrl, isVisionEnabled));
        });

        dialog.show();
        inputGguf.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    private void downloadCustomModel(String ggufUrl, String mmprojUrl, boolean isVisionEnabled) {
        String modelName = extractModelNameFromUrl(ggufUrl);
        String ggufFilename = "custom_" + System.currentTimeMillis() + ".gguf";
        String mmprojFilename = isVisionEnabled ? "custom_" + System.currentTimeMillis() + ".mmproj" : "";
        
        int modelIndex = saveCustomModelInfo(modelName, ggufUrl, ggufFilename, mmprojUrl, mmprojFilename, isVisionEnabled);
        
        isCancelled = false;
        currentDownloadingFilename = ggufFilename; 
        currentDownloadingModelIndex = modelIndex;
        
        createCustomModelCardWithProgress(modelIndex, modelName, ggufUrl, ggufFilename, mmprojUrl, mmprojFilename, isVisionEnabled);        
        
        startDownload(ggufUrl, ggufFilename, modelName);
        if (isVisionEnabled) {
            startDownload(mmprojUrl, mmprojFilename, "Vision Projector");
        }
    }

    private void createCustomModelCardWithProgress(int index, String name, String ggufUrl, String ggufFilename, String mmprojUrl, String mmprojFilename, boolean hasVision) {
        if (modelCardsContainer == null) return;

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        card.setBackgroundResource(R.drawable.bg_model_card);
        card.setTag("custom_model_" + index);
        
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dpToPx(16);
        card.setLayoutParams(cardParams);
        
        card.addView(createCardHeader(index, name, ggufFilename));

        boolean isGgufComplete = new File(getFilesDir(), ggufFilename).exists();

        card.addView(createLinkText("Learn more", ggufUrl));
        if (!isGgufComplete) {
            card.addView(createFileProgressBlock(ggufFilename, "Connecting..."));
        } else {
            TextView completedText = new TextView(this);
            completedText.setText("Model Downloaded, waiting for Vision adapter...");
            completedText.setTextSize(13);
            completedText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            completedText.setPadding(0, dpToPx(12), 0, dpToPx(4));
            card.addView(completedText);
        }

        if (hasVision) {
            TextView visionLabel = new TextView(this);
            visionLabel.setText("Vision Projector (.mmproj)");
            visionLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            visionLabel.setTextSize(14);
            visionLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dpToPx(16);
            visionLabel.setLayoutParams(labelParams);
            
            card.addView(visionLabel);
            card.addView(createLinkText("Learn more", mmprojUrl));
            
            String initialStatus = isGgufComplete ? "Connecting..." : "Waiting for Text Model to download first...";
            card.addView(createFileProgressBlock(mmprojFilename, initialStatus));
        }
        
        replaceOrAddCard(index, card);
    }

    private LinearLayout createFileProgressBlock(String filename, String initialStatus) {
        LinearLayout progressLayout = new LinearLayout(this);
        progressLayout.setOrientation(LinearLayout.VERTICAL);
        progressLayout.setTag("progress_layout_" + filename);
        LinearLayout.LayoutParams progressLayoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressLayoutParams.topMargin = dpToPx(12);
        progressLayout.setLayoutParams(progressLayoutParams);
        
        File tempFile = new File(getFilesDir(), filename + ".temp");
        int startingProgress = 0;
        if (tempFile.exists() && tempFile.length() > 0 && initialStatus.equals("Connecting...")) {
            long currentBytes = tempFile.length();
            String totalSizeStr = getSharedPreferences("download_prefs", MODE_PRIVATE)
                                    .getString("model_size_" + filename, "?");
            initialStatus = "Resuming at " + formatBytes(currentBytes) + " / " + totalSizeStr;
        }
        
        TextView progressStatus = new TextView(this);
        progressStatus.setText(initialStatus); 
        progressStatus.setTextSize(13);
        progressStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        progressStatus.setTag("progress_status_" + filename);
        progressLayout.addView(progressStatus);
        
        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams progressRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressRowParams.topMargin = dpToPx(4);
        progressRow.setLayoutParams(progressRowParams);
        
        TextView progressPercent = new TextView(this);
        progressPercent.setText(startingProgress + "%");
        progressPercent.setTextSize(14);
        progressPercent.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        progressPercent.setTag("progress_percent_" + filename);
        progressRow.addView(progressPercent);
        
        ProgressBar cardProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        cardProgressBar.setMax(100);
        cardProgressBar.setProgress(startingProgress);
        cardProgressBar.setTag("progress_bar_" + filename);
        cardProgressBar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.progress_tint_pink)));
        
        LinearLayout.LayoutParams progressBarParams = new LinearLayout.LayoutParams(0, dpToPx(12), 1.0f);
        progressBarParams.setMargins(dpToPx(12), 0, dpToPx(12), 0);
        cardProgressBar.setLayoutParams(progressBarParams);
        progressRow.addView(cardProgressBar);
        
        android.widget.ImageButton cancelDownloadBtn = new android.widget.ImageButton(this);
        cancelDownloadBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        cancelDownloadBtn.setBackgroundColor(0x00000000);
        cancelDownloadBtn.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        cancelDownloadBtn.setTag("cancel_btn_" + filename);
        cancelDownloadBtn.setOnClickListener(v -> {
            isCancelled = true; 
            stopService(new Intent(ModelDownloadActivity.this, DownloadService.class));
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::refreshCustomModelCards, 100);
        });
        
        LinearLayout.LayoutParams cancelBtnParams = new LinearLayout.LayoutParams(dpToPx(36), dpToPx(36));
        cancelDownloadBtn.setLayoutParams(cancelBtnParams);
        progressRow.addView(cancelDownloadBtn);
        
        progressLayout.addView(progressRow);
        return progressLayout;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private TextView createLinkText(String text, String url) {
        TextView learnMore = new TextView(this);
        learnMore.setText(text);        
        learnMore.setTextSize(14);
        learnMore.setTextColor(ContextCompat.getColor(this, R.color.brand_blue));
        learnMore.setClickable(true);
        learnMore.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(browserIntent);
        });
        LinearLayout.LayoutParams learnMoreParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        learnMoreParams.topMargin = dpToPx(8);
        learnMore.setLayoutParams(learnMoreParams);
        return learnMore;
    }

    private void addCustomModelCard(int index, String name, String url, String filename, String mmprojUrl, String mmprojFilename, boolean hasVision, boolean isDownloaded) {
        if (modelCardsContainer == null) return;

        File ggufFile = new File(getFilesDir(), filename);
        File mmprojFile = hasVision ? new File(getFilesDir(), mmprojFilename) : null;
        boolean isGgufComplete = ggufFile.exists() && ggufFile.length() > 0;
        boolean isMmprojComplete = !hasVision || (mmprojFile != null && mmprojFile.exists() && mmprojFile.length() > 0);
        boolean isFullyReady = isDownloaded && isGgufComplete && isMmprojComplete;

        File tempGguf = new File(getFilesDir(), filename + ".temp");
        File tempMmproj = hasVision ? new File(getFilesDir(), mmprojFilename + ".temp") : null;
        boolean canResume = (!isGgufComplete && tempGguf.exists() && tempGguf.length() > 0) || 
                            (hasVision && !isMmprojComplete && tempMmproj != null && tempMmproj.exists() && tempMmproj.length() > 0);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        card.setBackgroundResource(R.drawable.bg_model_card);
        card.setTag("custom_model_" + index);
        
        card.addView(createCardHeader(index, name, filename));
        
        String size = getSharedPreferences("download_prefs", MODE_PRIVATE).getString("model_size_" + filename, "Unknown size");
        TextView sizeText = new TextView(this);        
        sizeText.setText(size);
        sizeText.setTextSize(14);
        sizeText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        LinearLayout.LayoutParams sizeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sizeParams.topMargin = dpToPx(4);
        sizeText.setLayoutParams(sizeParams);
        
        card.addView(sizeText);
        card.addView(createLinkText("Learn more", url));

        if (hasVision) {
            TextView visionLabel = new TextView(this);
            visionLabel.setText("Vision Projector (.mmproj)");
            visionLabel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            visionLabel.setTextSize(14);
            visionLabel.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dpToPx(12);
            visionLabel.setLayoutParams(labelParams);
            card.addView(visionLabel);

            String mmprojSize = getSharedPreferences("download_prefs", MODE_PRIVATE).getString("model_size_" + mmprojFilename, "Unknown size");
            TextView mmprojSizeText = new TextView(this);        
            mmprojSizeText.setText(mmprojSize);
            mmprojSizeText.setTextSize(14);
            mmprojSizeText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            mmprojSizeText.setLayoutParams(sizeParams);
            
            card.addView(mmprojSizeText);
            card.addView(createLinkText("Learn more", mmprojUrl));
        }
        
        if (isFullyReady) {
            LinearLayout buttonContainer = new LinearLayout(this);
            buttonContainer.setOrientation(LinearLayout.HORIZONTAL);
            buttonContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams buttonContainerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            buttonContainerParams.topMargin = dpToPx(16);
            buttonContainer.setLayoutParams(buttonContainerParams);
            
            TextView launchBtn = new TextView(this);
            launchBtn.setText("Launch model");
            launchBtn.setTextSize(14);
            launchBtn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            launchBtn.setGravity(android.view.Gravity.CENTER);
            launchBtn.setPadding(0, dpToPx(10), 0, dpToPx(10));
            launchBtn.setClickable(true);
            launchBtn.setBackgroundResource(R.drawable.bg_style_button);
            launchBtn.setOnClickListener(v -> {
                getSharedPreferences("model_prefs", MODE_PRIVATE)                    
                 .edit()
                 .putString("selected_model", "custom")
                 .putString("custom_model_filename", filename)
                 .putString("custom_model_real_name", name) 
                 .putString("custom_mmproj_filename", hasVision ? mmprojFilename : "") 
                 .putBoolean("active_model_has_vision", hasVision) 
                 .commit(); 
        
                  Intent returnIntent = new Intent(ModelDownloadActivity.this, MainActivity.class);
                  returnIntent.putExtra("TRIGGER_MODEL_LOAD", true);
                  returnIntent.putExtra("INTENT_GGUF_FILE", filename);
                  returnIntent.putExtra("INTENT_MMPROJ_FILE", hasVision ? mmprojFilename : "");
                  returnIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                  startActivity(returnIntent);
                  finish();
            });

            LinearLayout.LayoutParams launchBtnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            launchBtn.setLayoutParams(launchBtnParams);
            buttonContainer.addView(launchBtn);
            
            android.widget.ImageButton deleteCustomBtn = new android.widget.ImageButton(this);
            deleteCustomBtn.setImageResource(android.R.drawable.ic_menu_delete);
            deleteCustomBtn.setBackgroundColor(0x00000000);
            deleteCustomBtn.setColorFilter(ContextCompat.getColor(this, R.color.delete_btn_text));
            LinearLayout.LayoutParams deleteBtnParams = new LinearLayout.LayoutParams(dpToPx(42), dpToPx(42));
            deleteBtnParams.setMargins(dpToPx(12), 0, 0, 0);
            deleteCustomBtn.setLayoutParams(deleteBtnParams);
            deleteCustomBtn.setOnClickListener(v -> deleteCustomModelCard(index, filename));            
            buttonContainer.addView(deleteCustomBtn);
            card.addView(buttonContainer);
        } else {
            TextView downloadCardBtn = new TextView(this);
            downloadCardBtn.setText(canResume ? "Resume Download" : "Download");
            downloadCardBtn.setTextSize(14);
            downloadCardBtn.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            downloadCardBtn.setGravity(android.view.Gravity.CENTER);
            downloadCardBtn.setPadding(0, dpToPx(10), 0, dpToPx(10));
            downloadCardBtn.setClickable(true);
            downloadCardBtn.setBackgroundResource(R.drawable.bg_style_button);
            
            downloadCardBtn.setOnClickListener(v -> {
                checkNotificationPermissionAndRun(() -> {
                    isCancelled = false;
                    currentDownloadingModelIndex = index;
                    
                    if (!isGgufComplete) {
                        currentDownloadingFilename = filename;
                        startDownload(url, filename, name);
                    }
                    if (hasVision && !isMmprojComplete) {
                        if (isGgufComplete) currentDownloadingFilename = mmprojFilename; 
                        startDownload(mmprojUrl, mmprojFilename, "Vision Projector");
                    }
                    
                    createCustomModelCardWithProgress(index, name, url, filename, mmprojUrl, mmprojFilename, hasVision);
                });
            });
            
            LinearLayout.LayoutParams downloadBtnParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44));
            downloadBtnParams.topMargin = dpToPx(16);
            downloadCardBtn.setLayoutParams(downloadBtnParams);
            card.addView(downloadCardBtn);
        }
        
        replaceOrAddCard(index, card);
    }

    private RelativeLayout createCardHeader(int index, String name, String filename) {
        RelativeLayout headerLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams headerParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        headerLayout.setLayoutParams(headerParams);

        TextView nameText = new TextView(this);
        nameText.setText(name);
        nameText.setTextSize(18);
        nameText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        nameText.setTypeface(null, android.graphics.Typeface.BOLD);
        
        RelativeLayout.LayoutParams nameParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        nameParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        nameParams.addRule(RelativeLayout.CENTER_VERTICAL);
        nameParams.rightMargin = dpToPx(40); 
        nameText.setLayoutParams(nameParams);

        ImageButton removeCardBtn = new ImageButton(this);
        removeCardBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        removeCardBtn.setBackgroundColor(0x00000000);
        removeCardBtn.setColorFilter(ContextCompat.getColor(this, R.color.icon_secondary));
        
        RelativeLayout.LayoutParams closeParams = new RelativeLayout.LayoutParams(
                dpToPx(32), dpToPx(32)
        );
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        closeParams.addRule(RelativeLayout.CENTER_VERTICAL);
        removeCardBtn.setLayoutParams(closeParams);
        
        removeCardBtn.setOnClickListener(v -> deleteCustomModelCard(index, filename));

        headerLayout.addView(nameText);
        headerLayout.addView(removeCardBtn);

        return headerLayout;
    }

    private void deleteCustomModelCard(int index, String filename) {
        if (!isCancelled && filename.equals(currentDownloadingFilename)) {
            isCancelled = true;
            stopService(new Intent(ModelDownloadActivity.this, DownloadService.class));
        }

        android.content.SharedPreferences prefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        String activeFilename = prefs.getString("custom_model_filename", "");
        
        if (filename.equals(activeFilename)) {
            prefs.edit()
                 .remove("custom_model_filename")
                 .remove("custom_mmproj_filename")
                 .putString("selected_model", "venice") 
                 .apply();
            Toast.makeText(this, "Active model unloaded and deleted.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Model removed from storage.", Toast.LENGTH_SHORT).show();
        }

        File modelFile = new File(getFilesDir(), filename);
        if (modelFile.exists()) modelFile.delete();
        
        android.content.SharedPreferences customPrefs = getSharedPreferences("custom_models", MODE_PRIVATE);
        String mmprojFilename = customPrefs.getString("custom_model_" + index + "_mmproj_filename", "");
        if (!mmprojFilename.isEmpty()) {
            File mmprojFile = new File(getFilesDir(), mmprojFilename);
            if (mmprojFile.exists()) mmprojFile.delete();
        }
        
        customPrefs.edit()
            .putBoolean("custom_model_" + index + "_downloaded", false)
            .remove("custom_model_" + index + "_name")
            .remove("custom_model_" + index + "_url")
            .remove("custom_model_" + index + "_filename")
            .remove("custom_model_" + index + "_mmproj_url")
            .remove("custom_model_" + index + "_mmproj_filename")
            .remove("custom_model_" + index + "_has_vision")
            .apply();
        
        refreshCustomModelCards();
    }

    private void updateCustomModelProgress(String filename, String modelName, int progress, String status, String totalSize) {
        int modelIndex = getModelIndexFromFilename(filename);
        if (modelIndex < 0) return;
        
        LinearLayout card = modelCardsContainer.findViewWithTag("custom_model_" + modelIndex);
        if (card == null) return;
        
        ProgressBar cardProgressBar = card.findViewWithTag("progress_bar_" + filename);
        
        if (cardProgressBar == null) {
            android.content.SharedPreferences prefs = getSharedPreferences("custom_models", MODE_PRIVATE);
            String name = prefs.getString("custom_model_" + modelIndex + "_name", "Unknown Model");
            String url = prefs.getString("custom_model_" + modelIndex + "_url", "");
            String mmprojUrl = prefs.getString("custom_model_" + modelIndex + "_mmproj_url", "");
            String mmprojFilename = prefs.getString("custom_model_" + modelIndex + "_mmproj_filename", "");
            boolean hasVision = prefs.getBoolean("custom_model_" + modelIndex + "_has_vision", false);
            
            currentDownloadingFilename = filename;
            createCustomModelCardWithProgress(modelIndex, name, url, filename, mmprojUrl, mmprojFilename, hasVision);
            
            card = modelCardsContainer.findViewWithTag("custom_model_" + modelIndex);
            if (card == null) return;
            cardProgressBar = card.findViewWithTag("progress_bar_" + filename);
            if (cardProgressBar == null) return;
        }

        TextView progressPercent = card.findViewWithTag("progress_percent_" + filename);
        TextView progressStatus = card.findViewWithTag("progress_status_" + filename);
        
        if (progressPercent != null) progressPercent.setText(progress + "%");
        cardProgressBar.setProgress(progress);
        
        if (progressStatus != null) progressStatus.setText(status);

        if (totalSize != null) {
            getSharedPreferences("download_prefs", MODE_PRIVATE)
                .edit()
                .putString("model_size_" + filename, totalSize)
                .apply();
        }
    }

    private void refreshCustomModelCards() {
        if (modelCardsContainer == null) return;
        
        for (int i = modelCardsContainer.getChildCount() - 1; i >= 0; i--) {
            View child = modelCardsContainer.getChildAt(i);
            if (child.getTag() != null && child.getTag().toString().startsWith("custom_model_")) {
                modelCardsContainer.removeViewAt(i);
            }
        }
        
        android.content.SharedPreferences prefs = getSharedPreferences("custom_models", MODE_PRIVATE);
        int count = prefs.getInt("custom_model_count", 0);
        
        int activeIndex = -1;
        if (DownloadService.isDownloading && DownloadService.activeFilename != null) {
            activeIndex = getModelIndexFromFilename(DownloadService.activeFilename);
        }
        
        for (int i = 0; i < count; i++) {
            if (!prefs.contains("custom_model_" + i + "_name")) continue; 

            String name = prefs.getString("custom_model_" + i + "_name", "Unknown");
            String url = prefs.getString("custom_model_" + i + "_url", "");
            String filename = prefs.getString("custom_model_" + i + "_filename", "");
            
            String mmprojUrl = prefs.getString("custom_model_" + i + "_mmproj_url", "");
            String mmprojFilename = prefs.getString("custom_model_" + i + "_mmproj_filename", "");
            boolean hasVision = prefs.getBoolean("custom_model_" + i + "_has_vision", false);
            boolean isDownloaded = prefs.getBoolean("custom_model_" + i + "_downloaded", false);
            
            if (DownloadService.isDownloading && i == activeIndex) {
                createCustomModelCardWithProgress(i, name, url, filename, mmprojUrl, mmprojFilename, hasVision);
                
                LinearLayout card = modelCardsContainer.findViewWithTag("custom_model_" + i);
                if (card != null) {
                    ProgressBar bar = card.findViewWithTag("progress_bar_" + DownloadService.activeFilename);
                    TextView percent = card.findViewWithTag("progress_percent_" + DownloadService.activeFilename);
                    TextView status = card.findViewWithTag("progress_status_" + DownloadService.activeFilename);
                    
                    if (bar != null) bar.setProgress(DownloadService.activeProgress);
                    if (percent != null) percent.setText(DownloadService.activeProgress + "%");
                    if (status != null) status.setText(DownloadService.activeStatus);
                    
                    if (DownloadService.activeFilename.equals(mmprojFilename)) {
                        ProgressBar ggufBar = card.findViewWithTag("progress_bar_" + filename);
                        TextView ggufPercent = card.findViewWithTag("progress_percent_" + filename);
                        TextView ggufStatus = card.findViewWithTag("progress_status_" + filename);
                        android.widget.ImageButton ggufCancel = card.findViewWithTag("cancel_btn_" + filename);
                        
                        if (ggufBar != null) ggufBar.setVisibility(View.GONE);
                        if (ggufPercent != null) ggufPercent.setVisibility(View.GONE);
                        if (ggufCancel != null) ggufCancel.setVisibility(View.GONE);
                        if (ggufStatus != null) ggufStatus.setText("Model Downloaded, waiting for Vision adapter...");
                    }
                }
            } else {
                addCustomModelCard(i, name, url, filename, mmprojUrl, mmprojFilename, hasVision, isDownloaded);
            }
        }
        
        forceCustomButtonToBottom();
    }

    private void replaceOrAddCard(int index, LinearLayout newCard) {
        View existingCard = modelCardsContainer.findViewWithTag("custom_model_" + index);
        if (existingCard != null) {
            int viewIndex = modelCardsContainer.indexOfChild(existingCard);
            modelCardsContainer.removeView(existingCard);
            modelCardsContainer.addView(newCard, viewIndex);
        } else {
            modelCardsContainer.addView(newCard);
        }
        forceCustomButtonToBottom();
    }

    private void forceCustomButtonToBottom() {
        if (customDownloadBtn != null) {
            if (customDownloadBtn.getParent() != null) {
                ((ViewGroup) customDownloadBtn.getParent()).removeView(customDownloadBtn);
            }
            modelCardsContainer.addView(customDownloadBtn);
        }
    }

    private int getModelIndexFromFilename(String filename) {
        android.content.SharedPreferences prefs = getSharedPreferences("custom_models", MODE_PRIVATE);
        int count = prefs.getInt("custom_model_count", 0);
        
        for (int i = 0; i < count; i++) {
            String storedGguf = prefs.getString("custom_model_" + i + "_filename", "");
            String storedMmproj = prefs.getString("custom_model_" + i + "_mmproj_filename", "");
            
            if (filename.equals(storedGguf) || filename.equals(storedMmproj)) {
                return i;
            }
        }
        return -1;
    }

    private String extractModelNameFromUrl(String url) {
        try {
            String cleanUrl = url.split("\\?")[0]; 
            String[] parts = cleanUrl.split("/");
            for (int i = parts.length - 1; i >= 0; i--) {
                String part = parts[i];
                if (part.endsWith(".gguf")) {
                    return part.replace(".gguf", "").replace("_", " ").replace("-", " ");
                }
            }
            if (parts.length > 0) {
                return parts[parts.length - 1].replace("_", " ").replace("-", " ");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Custom Model";
    }

    private int saveCustomModelInfo(String name, String url, String filename, String mmprojUrl, String mmprojFilename, boolean hasVision) {
        android.content.SharedPreferences prefs = getSharedPreferences("custom_models", MODE_PRIVATE);
        int count = prefs.getInt("custom_model_count", 0);
        
        prefs.edit()
            .putString("custom_model_" + count + "_name", name)
            .putString("custom_model_" + count + "_url", url)
            .putString("custom_model_" + count + "_filename", filename)
            .putString("custom_model_" + count + "_mmproj_url", mmprojUrl)
            .putString("custom_model_" + count + "_mmproj_filename", mmprojFilename)
            .putBoolean("custom_model_" + count + "_has_vision", hasVision)
            .putBoolean("custom_model_" + count + "_downloaded", false)
            .putInt("custom_model_count", count + 1)
            .apply();
        
        return count;
    }

    private void checkModelState() {
        File modelFile = new File(getFilesDir(), "model.gguf");
        boolean isDownloaded = getSharedPreferences("model_prefs", MODE_PRIVATE).getBoolean("model_downloaded", false);

        if (isDownloaded && modelFile.exists()) {
            downloadBtn.setVisibility(View.VISIBLE);
            downloadBtn.setText("Launch model");
            deleteBtn.setVisibility(View.VISIBLE);
            progressContainer.setVisibility(View.GONE);
        } else {
            if (progressContainer.getVisibility() != View.VISIBLE) {
                downloadBtn.setVisibility(View.VISIBLE);
                downloadBtn.setText("Download");
                deleteBtn.setVisibility(View.GONE);
            }
        }
    }

    private void startDownload(String url, String filename, String modelName) {
        if (!isInternetAvailable()) {
            Toast.makeText(this, "No internet connection. Try again!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (filename.equals("model.gguf")) {
            downloadBtn.setVisibility(View.GONE);
            deleteBtn.setVisibility(View.GONE);        
            progressContainer.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            progressBar.setProgressTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.progress_tint_pink)));
            percentText.setText("0%");
            statusText.setText("Connecting...");
        }

        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra("url", url);
        intent.putExtra("filename", filename);
        intent.putExtra("modelName", modelName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void resetUiToResume() {
        if ("model.gguf".equals(currentDownloadingFilename)) {
            downloadBtn.setVisibility(View.VISIBLE);
            downloadBtn.setText("Resume");
            deleteBtn.setVisibility(View.GONE);
            progressContainer.setVisibility(View.GONE);
        } else {
            refreshCustomModelCards(); 
        }
    }

    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        NetworkInfo net = cm.getActiveNetworkInfo();
        return net != null && net.isConnected();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        isCancelled = false; 
        
        if (!DownloadService.isDownloading) {
            resetUiToResume(); 
        } else {
            currentDownloadingFilename = DownloadService.activeFilename;
            currentDownloadingModelIndex = getModelIndexFromFilename(currentDownloadingFilename);
        }
        
        checkModelState(); 
        refreshCustomModelCards(); 
        
        if (DownloadService.isDownloading && "model.gguf".equals(DownloadService.activeFilename)) {
            progressContainer.setVisibility(View.VISIBLE);
            downloadBtn.setVisibility(View.GONE);
            deleteBtn.setVisibility(View.GONE);
            progressBar.setProgress(DownloadService.activeProgress);
            percentText.setText(DownloadService.activeProgress + "%");
            statusText.setText(DownloadService.activeStatus);
            if (DownloadService.activeTotalSize != null && !DownloadService.activeTotalSize.isEmpty()) {
                modelSizeText.setText(DownloadService.activeTotalSize);
            }
        }
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("DOWNLOAD_PROGRESS");
        filter.addAction("DOWNLOAD_COMPLETE");
        filter.addAction("DOWNLOAD_ERROR");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {            
            unregisterReceiver(receiver);
        } catch (Exception ignored) {}
    }
}