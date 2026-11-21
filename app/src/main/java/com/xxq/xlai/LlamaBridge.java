package com.xxq.xlai;

import android.util.Log;

public class LlamaBridge {

    private static final String TAG = "LlamaBridge";

    static {
        try {
            Log.i(TAG, "🔍 Loading native library: llama_vision");
            System.loadLibrary("llama_vision");
            Log.i(TAG, "✅ Native library loaded successfully!");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "❌ Failed to load native library: " + e.getMessage());
            throw e;
        }
    }

    public interface StreamCallback {
        void onToken(String token);
        void onError(String error);
        void onComplete();
    }

    // 🛑 Direct dual-file loader matching C++ JNI signature
    public native boolean loadVisionModel(
            String modelPath,
            String mmprojPath, // 👈 Explicit vision projector path
            int contextSize,
            int threads,
            boolean useMmap
    );

    // 🛑 Generation streamer (accepts imagePath or null)
    public native void generateVisionStream(
            String prompt,
            String imagePath,
            int maxTokens,
            float temperature,
            int topK,
            float topP,
            float repeatPenalty,
            StreamCallback callback
    );

    // 🧠 Metadata checks read directly from model tensors
    public native boolean isThinkingModelNative();
    public native boolean isVisionModelNative();
    public native boolean isAudioModelNative();

    // 🛑 Generation controls & memory management
    public native void stopVisionGeneration();
    public native void freeVision();
}
