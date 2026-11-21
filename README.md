# XL AI: Hybrid Multi-Provider AI Client

**XL AI** is a professional-grade, lightweight Android application designed to bridge the gap between cloud-based AI APIs and on-device local inference. Built with a focus on resilience, privacy, and performance, XL AI ensures your AI never goes down, regardless of your network or hardware.

##  Key Features

*   **⚡ Zero-Config Default Engine:** Just want to chat immediately? The app features a built-in default mode managed by **OpenIntelligenceNet**. It routes seamlessly through our private Flask backend, allowing you to use the AI instantly without needing to find or paste a single API key.
*   **🔄 Unlimited Fallback Routing:** Never experience a dropped generation again. If your primary API key hits a rate limit or fails, the app automatically cascades through your configured fallback models, providers, and custom endpoints.
*   **🔑 Bring Your Own Key (BYOK):** Full support for major providers including OpenAI, Anthropic, Google AI, OpenRouter, Groq, DeepSeek, Mistral, and any custom OpenAI-compatible endpoint.
*   **📱 Local Inference:** Run quantized GGUF models (Llama, Qwen, Gemma, etc.) entirely on-device via a custom C++ JNI bridge to `llama.cpp`.
*   **🛡️ Resume-Enabled Downloads:** Flawless local model downloads that survive app crashes, network drops, and device reboots.
*   **🪶 Lightweight & Optimized:** Designed to run efficiently even on budget devices (optimized for 6GB RAM and above) with aggressive memory management and Markdown rendering.

## 🧠 Architecture: Cloud, Local, and Hybrid

XL AI is built for total flexibility:
1.  **Default Mode:** Sends requests to the `OpenIntelligenceNet` managed Flask backend (Powering the `XL-Stealth` models). No configuration required.
2.  **BYOK Cloud Mode:** Sends requests directly from your phone to your chosen provider (OpenAI, OpenRouter, etc.) using your personal API keys.
3.  **Local Mode:** Runs GGUF models entirely offline using the phone's CPU/GPU.

## 🛠 Tech Stack

*   **Core:** Kotlin & Java (Android SDK 34)
*   **Local Engine:** C++ (JNI) powering `llama.cpp` and `llama_vision`
*   **Networking:** OkHttp + Gson (Server-Sent Events streaming)
*   **Backend:** Python Flask (Hosted on PythonAnywhere)
*   **Database:** Room Persistence Library
*   **UI/UX:** Material Design 3, custom dynamic bottom sheets, and Markwon for lightning-fast Markdown rendering.

## 🔒 Security Notice
API keys you provide (BYOK) are stored locally on your device via `SharedPreferences`. For the default engine, no personal data is stored on the server; the Flask backend simply acts as an anonymous proxy to the underlying LLM provider.

---
