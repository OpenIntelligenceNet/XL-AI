package com.xxq.xlai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;

public class LlamaManager {

    private static volatile boolean isLoaded = false;
    private static volatile boolean isLoading = false;
    private static volatile boolean isGenerating = false;
    public static volatile boolean isBackendBusy = false;
    private static volatile boolean wasManuallyFreed = false;
    private static volatile boolean isVisionSupported = false;
    private static volatile boolean isAudioSupported = false;
    private static volatile boolean isThinkingModel = false;
    
    private static final Object lock = new Object();
    private static final LlamaBridge bridge = new LlamaBridge();

    private static void showToastSafe(Context context, String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_LONG).show());
    }

       public static boolean loadModel(Context context, String filename, String mmprojFilename, boolean useMmap) {
        synchronized (lock) {
            if (isBackendBusy || isLoading || isGenerating) return false;
            isBackendBusy = true;
            isLoaded = false; 
            isLoading = true;
            wasManuallyFreed = false;
        }

        try {
            File filesDir = context.getFilesDir(); 
            File modelFile = new File(filesDir, filename != null ? filename : "model.gguf");

            if (!modelFile.exists() || modelFile.length() <= 0) {
                showToastSafe(context, "GGUF file missing or empty");
                synchronized (lock) { isBackendBusy = false; isLoading = false; }
                return false;
            }

            if (modelFile.length() < 100 * 1024 * 1024) { 
                showToastSafe(context, "GGUF file is corrupted! Please re-download.");
                synchronized (lock) { isBackendBusy = false; isLoading = false; }                
                return false;
            }

            String mmprojPath = "";
            if (mmprojFilename != null && !mmprojFilename.isEmpty()) {
                File mmprojFile = new File(filesDir, mmprojFilename);
                if (mmprojFile.exists() && mmprojFile.length() > 0) {
                    mmprojPath = mmprojFile.getAbsolutePath();
                }
            }

            int cpuCores = Runtime.getRuntime().availableProcessors();
            int optimalThreads = Math.max(1, Math.min(cpuCores - 1, 6)); 

            // THE CURE FOR THE TOKEN LOOP:
            // Fetch the context size from your UI settings instead of hardcoding 2048.
            // Vision models need a MASSIVE context window (at least 4096) to fit the image!
            android.content.SharedPreferences paramsPrefs = context.getSharedPreferences("LlamaModelParams", Context.MODE_PRIVATE);
            int contextSize = paramsPrefs.getInt("param_context_window", 4096); 
            
            android.util.Log.d("VisionDebug", "Allocating C++ Context Window size: " + contextSize);

            // 🚀 Pass the dynamic contextSize to the bridge!
            boolean result = bridge.loadVisionModel(
                    modelFile.getAbsolutePath(), mmprojPath, contextSize, optimalThreads, useMmap
            );
            
            synchronized (lock) {
                isLoaded = result;
                isLoading = false;
                isBackendBusy = false; 
                
                if (result) {
                    isThinkingModel = bridge.isThinkingModelNative();
                    isAudioSupported  = bridge.isAudioModelNative();
                    
                    if (mmprojPath != null && !mmprojPath.isEmpty()) {
                        isVisionSupported = true;
                    } else {
                        isVisionSupported = bridge.isVisionModelNative();
                    }
                }
            }
            return result;
        } catch (Throwable e) {
            e.printStackTrace();
            showToastSafe(context, "LOAD ERROR:\n" + e.getMessage());
            synchronized (lock) {
                isLoaded = false;
                isLoading = false;
                isBackendBusy = false; 
                isVisionSupported = false;
                isAudioSupported = false;
                isThinkingModel = false;
            }
            return false;
        }
    }


    public static void setSystemPrompt(String prompt) {
        synchronized (lock) {
        }
    }

    public static void generateStream(
            String prompt, String imagePath, int maxTokens, float temperature, int topK, 
            float topP, float repeatPenalty, LlamaBridge.StreamCallback uiCallback) {
            
        synchronized (lock) {            
            if (isBackendBusy || isLoading || !isLoaded || isGenerating) {
                if (uiCallback != null) {
                    uiCallback.onError("Backend busy or model not loaded.");
                }
                return;
            }
            isGenerating = true;
        }

        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                bridge.generateVisionStream(prompt, imagePath, maxTokens, temperature, topK, topP, 
                                     repeatPenalty, new LlamaBridge.StreamCallback() {
                    @Override
                    public void onToken(String token) {
                        mainHandler.post(() -> {
                            if (uiCallback != null) uiCallback.onToken(token);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        synchronized (lock) { isGenerating = false; }
                        mainHandler.post(() -> {
                            if (uiCallback != null) uiCallback.onError(error);
                        });
                    }

                    @Override
                    public void onComplete() {
                        synchronized (lock) { isGenerating = false; }
                        mainHandler.post(() -> {
                            if (uiCallback != null) uiCallback.onComplete();
                        });
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
                synchronized (lock) { isGenerating = false; }
                mainHandler.post(() -> {
                    if (uiCallback != null) uiCallback.onError(e.getMessage());
                });
            }
        }).start();
    }

    public static boolean isVisionSupported() {        
        synchronized (lock) { return isVisionSupported; }
    }

    public static boolean isAudioSupported() {
        synchronized (lock) { return isAudioSupported; }
    }

    public static boolean isThinkingModel() {
        synchronized (lock) { return isThinkingModel; }
    }

    public static boolean wasManuallyFreed() {
        synchronized (lock) { return wasManuallyFreed; }
    }
   
    public static boolean isLoaded() {
        synchronized (lock) { return isLoaded; }
    }
    
    public static boolean isBackendBusy() {
        synchronized (lock) { return isBackendBusy || isLoading; }
    }

    public static void reset() {
        synchronized (lock) {
            if (isLoading) return; 
            try { 
                isGenerating = false; 
                bridge.stopVisionGeneration();
            } catch (Throwable ignored) {}
        }
    }
    
    public static void resetManuallyFreedFlag() {
        synchronized (lock) { wasManuallyFreed = false; }
    }
    
       public static void free() {
        // 1. Check state safely, but let go of the lock immediately after!
        synchronized (lock) {
            if (isLoading) return; 
        }
        
        try {
            // 2. Instantly tell C++ to break the generation loop (non-blocking)
            bridge.stopVisionGeneration(); 
            
            // 3. Wait for C++ to finish and free native memory. 
            //  We are doing this WITHOUT holding the Java lock,
            // which allows the onComplete() callback to execute and prevents the deadlock!
            bridge.freeVision(); 
            
            // 4. Safely grab the lock again only AFTER C++ is completely dead, to reset UI states.
            synchronized (lock) {
                isGenerating = false;
                isBackendBusy = false;
                isLoaded = false;
                isVisionSupported = false;
                isAudioSupported = false;
                isThinkingModel = false;
                wasManuallyFreed = true;
            }
        } catch (Throwable ignored) {}
    }

}
