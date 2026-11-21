package com.xxq.xlai;

import android.content.Context;
import android.content.SharedPreferences;

public class ModelRepository {

    private static final String PREF_NAME = "custom_models";
    private static final String ACTIVE_PREF = "model_prefs";
    private static final String PARAMS_PREF = "LlamaModelParams"; // Added reference to parameters

    public static CustomModel getModelById(Context context, int index) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String name = prefs.getString("custom_model_" + index + "_name", "");
        String gguf = prefs.getString("custom_model_" + index + "_filename", "");
        String mmproj = prefs.getString("custom_model_" + index + "_mmproj_filename", "");
        boolean hasVision = prefs.getBoolean("custom_model_" + index + "_has_vision", false);
        boolean isDownloaded = prefs.getBoolean("custom_model_" + index + "_downloaded", false);
        return new CustomModel(index, name, gguf, mmproj, hasVision, isDownloaded);
    }

    public static void setActiveModel(Context context, int index) {
        CustomModel model = getModelById(context, index);
        SharedPreferences prefs = context.getSharedPreferences(ACTIVE_PREF, Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("active_model_id", index);
        editor.putString("custom_model_filename", model.ggufFilename);
        
        if (model.hasVision && model.mmprojFilename != null && !model.mmprojFilename.isEmpty()) {
            editor.putString("custom_mmproj_filename", model.mmprojFilename);
            editor.putBoolean("active_model_has_vision", true);
        } else {
            editor.remove("custom_mmproj_filename");
            editor.putBoolean("active_model_has_vision", false);
        }
        editor.apply();
    }

    public static boolean loadActiveModel(Context context, boolean useMmap) {
        SharedPreferences prefs = context.getSharedPreferences(ACTIVE_PREF, Context.MODE_PRIVATE);
        SharedPreferences paramsPrefs = context.getSharedPreferences(PARAMS_PREF, Context.MODE_PRIVATE);
        
        String ggufFilename = prefs.getString("custom_model_filename", null);
        boolean hasVision = prefs.getBoolean("active_model_has_vision", false);
        
        // 🛑 MEMORY SAVER: Check the UI Switch!
        boolean isVisionEnabledByUser = paramsPrefs.getBoolean("param_video", true);
        
        // Only pass the projector file if the model supports it AND the user has it toggled ON
        String mmprojFilename = (hasVision && isVisionEnabledByUser) ? prefs.getString("custom_mmproj_filename", "") : "";

        if (ggufFilename == null || ggufFilename.isEmpty()) {
            return false;
        }

        return LlamaManager.loadModel(context, ggufFilename, mmprojFilename, useMmap);
    }
}
