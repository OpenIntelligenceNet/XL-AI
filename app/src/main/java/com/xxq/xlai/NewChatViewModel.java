package com.xxq.xlai;

import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Collections; // Added for Collections.emptyList() in case of error

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class NewChatViewModel extends AndroidViewModel {

    private final NewChatDao newchatDao;
    private final ExecutorService executorService;
    
    private final LiveData<List<NewChatMessageEntity>> chatHistory;
    
    // 1. Controls initial loading of history AND the send button state.
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false); 
    
    // 2. Controls the "three dots" typing indicator ONLY. (NEW)
    private final MutableLiveData<Boolean> isAITyping = new MutableLiveData<>(false); 
    
    private final MutableLiveData<List<NewChatMessageEntity>> chatDisplayList = new MutableLiveData<>();
    private final List<Long> messagesToAnimateTimestamps = new ArrayList<>();

    private static final int HISTORY_CONTEXT_LIMIT = 13; 

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build();

    

    public NewChatViewModel(@NonNull Application application, NewChatDao dao) {
     super(application);
     this.newchatDao = dao;
     this.executorService = Executors.newSingleThreadExecutor();
    
    // Set loading to true initially for the initial progress bar
    isLoading.setValue(true); 
    
    this.chatHistory = dao.getAllMessages();

    chatHistory.observeForever(messages -> {
        // Update the display list using the new logic
        updateDisplayList(messages);
        
        if (messages == null || messages.isEmpty()) {
            messagesToAnimateTimestamps.clear();
        }
        
        // ⚠️ FIX: Only set isLoading to false AFTER the history is fetched once.
        if (Boolean.TRUE.equals(isLoading.getValue())) {
            // Use postValue to ensure this runs after all synchronous setup.
            isLoading.postValue(false); 
        }
    });
    
    // ⚠️ NEW: Observe the AI typing state to trigger display list updates.
    isAITyping.observeForever(isTyping -> {
        // Trigger updateDisplayList whenever the typing state changes.
        updateDisplayList(chatHistory.getValue());
    });
  }


    public LiveData<List<NewChatMessageEntity>> getChatDisplayList() {
        return chatDisplayList;
    }
    
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }
    
    public List<Long> getMessagesToAnimate() {
        return messagesToAnimateTimestamps;
    }

    // New utility function to clear chat history (call this if you want a fresh chat)
    public void clearChatHistory() {
        executorService.execute(newchatDao::deleteAllMessages); // Assuming you have this method in your DAO
    }

   private void updateDisplayList(List<NewChatMessageEntity> history) {
    if (history == null) {
        history = new ArrayList<>();
    }
    
    List<NewChatMessageEntity> displayList = new ArrayList<>(history);
    
    // Only add the typing indicator if isAITyping is true
    if (Boolean.TRUE.equals(isAITyping.getValue())) { 
        // ➡️ FIX 1: ADDED false, null for isSearchUsed and sourceResultsJson
        displayList.add(new NewChatMessageEntity("", NewChatMessage.TYPE_TYPING, System.currentTimeMillis(), "typing", null, null, false, null));
    }
    chatDisplayList.postValue(displayList);
  }

    public LiveData<Boolean> getIsAITyping() {
    return isAITyping;
   }
  
    private void addMessageAndAnimate(NewChatMessageEntity message) {
        executorService.execute(() -> {
            newchatDao.insert(message);
            messagesToAnimateTimestamps.add(message.getTimestamp());
        });
    }
    
    // ⬅️ CRITICAL CHANGE: Added isSearchActive parameter
    public void sendMessageToAI(String userMessage, Uri imageUri, boolean isSearchActive) {
        // 1. Create the new user message entity
        // ➡️ FIX 2: ADDED false, null for isSearchUsed and sourceResultsJson
        NewChatMessageEntity newUserMessage = new NewChatMessageEntity(userMessage, NewChatMessage.TYPE_USER, System.currentTimeMillis(), "text", null, imageUri != null ? imageUri.toString() : null, false, null);

        // 2. Set flags
        isLoading.postValue(true); 
        isAITyping.postValue(true); 

        // 3. Prepare full history for the API: Get current LiveData history and MANUALLY append the new message
        List<NewChatMessageEntity> currentMessages = chatHistory.getValue();
        if (currentMessages == null) {
            currentMessages = new ArrayList<>();
        }
        
        // ➡️ CRITICAL FIX: Create the network-ready list by cloning the LiveData value and adding the new message.
        List<NewChatMessageEntity> fullHistoryForAI = new ArrayList<>(currentMessages);
        fullHistoryForAI.add(newUserMessage); 

        // 4. Immediately add the new message to the database to update the UI
        addMessageAndAnimate(newUserMessage); 
        
        // 5. Execute the network request with the correct history list and the search flag
        executorService.execute(() -> {
            if (imageUri != null) {
                // Pass the correct list AND the search flag
                sendHistoryToAIWithContextWithImage(fullHistoryForAI, imageUri, isSearchActive);
            } else {
                // Pass the correct list AND the search flag
                sendHistoryToAIWithContextTextOnly(fullHistoryForAI, isSearchActive);
            }
        });
    }

    // ⚠️ MODIFIED: Failure/Response logic to reset both flags
    private void handleResponse(Call call, Response response, Gson gson, boolean isSuccessful) {
        // Reset flags regardless of success/failure
        isAITyping.postValue(false);
        isLoading.postValue(false);

        NewChatMessageEntity aiResponse;
        
        // Use a try-catch block to safely call methods that throw IOException (like response.body().string())
        try {
            if (isSuccessful && response != null && response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string(); // IOException possible here
                
                // --- Start of Server Response Parsing ---
                Reply reply = gson.fromJson(responseBody, Reply.class);
                String messageType = reply.type != null ? reply.type : "text";

                // Handle SourceResult parsing
                boolean isSearchUsed = reply.sourceResults != null && !reply.sourceResults.isEmpty();
                String sourceResultsJson = null;
                if (isSearchUsed) {
                    // Re-serialize the list back into JSON string for Room storage
                    sourceResultsJson = gson.toJson(reply.sourceResults);
                }

                aiResponse = new NewChatMessageEntity(
                        reply.reply,
                        NewChatMessage.TYPE_AI, 
                        System.currentTimeMillis(),
                        messageType,
                        reply.language,
                        null,
                        // ➡️ FIX 3: ADDED isSearchUsed and sourceResultsJson (parsed from server response)
                        isSearchUsed,
                        sourceResultsJson
                );
            } else {
                // Network failure or non-successful response
                int code = response != null ? response.code() : 0;
                String errorMsg = isSuccessful ? "HTTP Error: " + code : "Network Failed: " + call.request().url().host();
                // ➡️ FIX 4: ADDED false, null for isSearchUsed and sourceResultsJson
                aiResponse = new NewChatMessageEntity("❌ " + errorMsg, NewChatMessage.TYPE_AI, System.currentTimeMillis(), "text", null, null, false, null);
            }
        } catch (IOException e) {
            // Catch IO errors (like failure to read response body)
            // ➡️ FIX 5: ADDED false, null for isSearchUsed and sourceResultsJson
            aiResponse = new NewChatMessageEntity("❌ IO Exception: " + e.getMessage(), NewChatMessage.TYPE_AI, System.currentTimeMillis(), "text", null, null, false, null);
        } catch (JsonSyntaxException e) {
            // Catch JSON parsing errors
            // ➡️ FIX 6: ADDED false, null for isSearchUsed and sourceResultsJson
            aiResponse = new NewChatMessageEntity("❌ Response parsing failed: " + e.getMessage(), NewChatMessage.TYPE_AI, System.currentTimeMillis(), "text", null, null, false, null);
        }
        
        addMessageAndAnimate(aiResponse);
    }
    
    // ⬅️ CRITICAL CHANGE: Added isSearchActive parameter
    private void sendHistoryToAIWithContextTextOnly(List<NewChatMessageEntity> history, boolean isSearchActive) {
        try {
            Gson gson = new Gson();
            JsonArray messages = new JsonArray();

            List<NewChatMessageEntity> messagesToSend = history;
            if (history.size() > HISTORY_CONTEXT_LIMIT) {
                messagesToSend = history.subList(history.size() - HISTORY_CONTEXT_LIMIT, history.size());
            }

            if (messagesToSend != null) {
                for (NewChatMessageEntity msg : messagesToSend) {
                    JsonObject messageObject = new JsonObject();
                    messageObject.addProperty("role", msg.getType() == NewChatMessage.TYPE_USER ? "user" : "assistant");
                    messageObject.addProperty("content", msg.getMessage() != null ? msg.getMessage() : "");
                    messages.add(messageObject);
                }
            }

            JsonObject jsonPayload = new JsonObject();
            jsonPayload.add("messages", messages);
            // ⬅️ CRITICAL: Add the search flag to the JSON payload
            jsonPayload.addProperty("is_search_active", isSearchActive); 

            RequestBody requestBody = RequestBody.create(jsonPayload.toString(), MediaType.get("application/json; charset=utf-8"));

            String url = "https://venice.pythonanywhere.com/ask";
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    handleResponse(call, null, gson, false);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    handleResponse(call, response, gson, true);
                }
            });
        } catch (Exception e) {
            isAITyping.postValue(false);
            isLoading.postValue(false);
            // ➡️ FIX 7: ADDED false, null for isSearchUsed and sourceResultsJson
            addMessageAndAnimate(new NewChatMessageEntity("❌ Exception: " + e.getMessage(), NewChatMessage.TYPE_AI, System.currentTimeMillis(), "text", null, null, false, null));
        }
    }

    // ⬅️ CRITICAL CHANGE: Added isSearchActive parameter
    private void sendHistoryToAIWithContextWithImage(List<NewChatMessageEntity> history, Uri imageUri, boolean isSearchActive) {
        try {
            Gson gson = new Gson();
            JsonArray messageHistory = new JsonArray();

            List<NewChatMessageEntity> messagesToSend = history;
            if (history.size() > HISTORY_CONTEXT_LIMIT) {
                messagesToSend = history.subList(history.size() - HISTORY_CONTEXT_LIMIT, history.size());
            }

            // userMessage is the text part of the last message in the list
            String userMessage = "";
            if (!messagesToSend.isEmpty()) {
                NewChatMessageEntity lastMessage = messagesToSend.get(messagesToSend.size() - 1);
                if (lastMessage.getType() == NewChatMessage.TYPE_USER) {
                    userMessage = lastMessage.getMessage();
                }
            }

            // Create the history array for the JSON payload (everything but the last message which is multimodal)
            if (messagesToSend != null) {
                for (NewChatMessageEntity msg : messagesToSend) {
                    JsonObject messageObject = new JsonObject();
                    messageObject.addProperty("role", msg.getType() == NewChatMessage.TYPE_USER ? "user" : "assistant");
                    messageObject.addProperty("content", msg.getMessage() != null ? msg.getMessage() : "");
                    messageHistory.add(messageObject);
                }
            }

            JsonObject jsonPayload = new JsonObject();
            jsonPayload.add("messages", messageHistory);
            jsonPayload.addProperty("userMessage", userMessage); // Flask uses this to know the text component
            // ⬅️ CRITICAL: Add the search flag to the JSON payload
            jsonPayload.addProperty("is_search_active", isSearchActive); 

            MultipartBody.Builder builder = new MultipartBody.Builder().setType(MultipartBody.FORM);
            RequestBody jsonBody = RequestBody.create(jsonPayload.toString(), MediaType.get("application/json; charset=utf-8"));
            builder.addFormDataPart("data", null, jsonBody);

            if (imageUri != null) {
                byte[] imageBytes = getBytesFromUri(imageUri);
                if (imageBytes != null) {
                    RequestBody imageBody = RequestBody.create(imageBytes, MediaType.get("image/*"));
                    builder.addFormDataPart("image", "image.jpg", imageBody);
                }
            }

            RequestBody requestBody = builder.build();
            String url = "https://venice.pythonanywhere.com/ask_with_image";
            Request request = new Request.Builder().url(url).post(requestBody).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e) {
                    handleResponse(call, null, gson, false);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                    handleResponse(call, response, gson, true);
                }
            });
        } catch (Exception e) {
            isAITyping.postValue(false);
            isLoading.postValue(false);
            // ➡️ FIX 8: ADDED false, null for isSearchUsed and sourceResultsJson
            addMessageAndAnimate(new NewChatMessageEntity("❌ Exception: " + e.getMessage(), NewChatMessage.TYPE_AI, System.currentTimeMillis(), "text", null, null, false, null));
        }
    }

    private byte[] getBytesFromUri(Uri uri) {
        try {
            ContentResolver contentResolver = getApplication().getContentResolver();
            InputStream inputStream = contentResolver.openInputStream(uri);
            if (inputStream == null) return null;

            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];

            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            return byteBuffer.toByteArray();
        } catch (Exception e) {
            Log.e("ChatViewModel", "Error getting bytes from URI", e);
            return null;
        }
    }
    
    static class Reply {
        String reply;
        String type;
        String language;
        // ⚠️ NEW: Field to receive search results from Flask
        List<SourceResult> sourceResults;
    }
}
