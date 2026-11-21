package com.xxq.xlai; // Our package

public class ApiKeyDetector {

    public static final String PROVIDER_OPENAI = "OpenAI";
    public static final String PROVIDER_ANTHROPIC = "Anthropic";
    public static final String PROVIDER_GOOGLE = "Google AI";
    public static final String PROVIDER_OPENROUTER = "OpenRouter";
    public static final String PROVIDER_XAI = "xAI";
    public static final String PROVIDER_DEEPSEEK = "DeepSeek";
    public static final String PROVIDER_GROQ = "Groq";
    public static final String PROVIDER_MISTRAL = "Mistral";
    public static final String PROVIDER_NVIDIA = "Nvidia";
    public static final String PROVIDER_TOGETHER = "Together AI";

    
    public static String detectProvider(String key) {
        if (key == null) return null;
        String k = key.trim();

        // 1. OpenRouter (Must check before OpenAI because both start with "sk-")
        if (k.startsWith("sk-or-")) {
            return PROVIDER_OPENROUTER;
        }

        // 2. Anthropic (Must check before OpenAI)
        if (k.startsWith("sk-ant-api03-") || k.startsWith("sk-ant-")) {
            return PROVIDER_ANTHROPIC;
        }

        // 3. OpenAI (Project keys sk-proj-... or Legacy sk-...)
        // We also ensure it's not a standard DeepSeek key (which is sk- but shorter)
        if (k.startsWith("sk-proj-") || (k.startsWith("sk-") && k.length() > 40)) {
            return PROVIDER_OPENAI;
        }

        // 4. Google AI Studio (Legacy AIza or new AQ. auth keys)
        if (k.startsWith("AIza") || k.startsWith("AQ.")) {
            return PROVIDER_GOOGLE;
        }

        // 5. xAI (Grok)
        if (k.startsWith("xai-")) {
            return PROVIDER_XAI;
        }

        // 6. Groq
        if (k.startsWith("gsk_")) {
            return PROVIDER_GROQ;
        }

        // 7. Nvidia
        if (k.startsWith("nvapi-")) {
            return PROVIDER_NVIDIA;
        }

        // 8. DeepSeek (Usually starts with sk- but shorter than OpenAI keys)
        if (k.startsWith("sk-") && k.length() <= 40) {
            return PROVIDER_DEEPSEEK;
        }

        // 9. Mistral (No prefix, exactly 32 alphanumeric characters)
        if (k.matches("^[a-zA-Z0-9]{32}$")) {
            return PROVIDER_MISTRAL;
        }

        // 10. Together AI (No prefix, usually a 64-character hex string)
        if (k.matches("^[a-fA-F0-9]{64}$")) {
            return PROVIDER_TOGETHER;
        }

        return "Unknown Provider";
    }
}