package com.xxq.xlai;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.text.style.ForegroundColorSpan;
import android.graphics.Typeface;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ViewGroup;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import android.text.SpannableStringBuilder;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.style.RelativeSizeSpan;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import androidx.lifecycle.MutableLiveData;
import androidx.annotation.NonNull;
import android.widget.HorizontalScrollView;


public class MainActivity extends AppCompatActivity implements ChatAdapter.ChatActionListener {


    // === VIEW MODEL & DATA ===
    private ChatViewModel chatViewModel;
    private ChatAdapter chatAdapter;
    private List<ChatMessageEntity> currentMessages;
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    // Session Management
    private long currentSessionId = -1; // -1 = New Chat (Draft Mode)
    private ChatDao chatDao; 
   
    private ChatHistoryAdapter historyAdapter;
     private boolean currentTurnIsFlask = false;   
     private boolean flaskStickToBottom = false;  
     private Runnable flaskIdleReveal;             
    // === UI COMPONENTS ===
    private EditText inputMessage;
    private ImageButton sendMessageButton;
   //  DADDY'S FINAL FIX: one pin per send, tracked by the user message's timestamp
   private boolean netAsleep = false;   // true = net sleeps tonight. Do NOT write "final" — final breaks it again.
    // so we can detect the exact frame the stream ends
    private boolean wasStreaming = false;
       //  The flag that gives YOU control over the scroll
    private ChatSpacerDecoration chatSpacerDecoration;
       //  Tracks if your finger is actively dragging the screen
    private boolean isUserScrolling = false;
    private boolean pinLocked = false;            // true from send → stream end
   private boolean pinArmedForInsert = false;   //  awake for the insert frame only
    private boolean userGrabbedDuringPin = false; // once she scrolls, the net lets go
               // re-entrancy guard for the layout net
    private DrawerLayout drawerLayout;
    private ImageButton drawerButton;
    private ImageButton attachButton;
    private ImageButton removeImageButton;
    private LinearLayout localLlmsButton;
    private ImageButton editButton; 
    private ImageButton optionsButton; 
    private boolean isModelLoading = false;
    private FrameLayout imagePreviewContainer;
    private ImageView imagePreview;
    private Uri selectedImageUri;
    private boolean isProcessingRetry = false;
    private boolean isWaitingForResponse = false;
    private LinearLayout editMessagePillContainer;
    private ImageButton cancelEditButton;
    private ChatMessageEntity currentlyEditingMessage = null;

    private TextView modelNameTextView;
    private String currentModel;
    // Race condition shield for session loading
    private long activeLoadingSessionId = -1;
    // Frame rate limit for live streaming
    private long lastStreamRenderTime = 0;

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
   private long lastSendTime = 0;

    // Welcome Screen Layout & Warning Text
    private LinearLayout welcomeScreen;
    private TextView welcomeMainTitleTextView; 
    private TextView welcomeTitleTextView; 
    private RecyclerView historyRecyclerView; 
    private String lastAttemptedMessage = "";
    private Uri lastAttemptedImageUri = null;
    // Standard Button, NOT AppCompatButton!    // DADDY'S FIX: The Lock Flag
    private boolean isSnappingToTop = false;
    private String currentTab = "Default"; // Default, Local, Cloud
    private ActivityResultLauncher<PickVisualMediaRequest> galleryLauncher;
    private static final boolean ALLOW_IMAGE_ONLY_SEND = false;
    
    @Override
  protected void onCreate(Bundle savedInstanceState) {
   
    
   
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
       if (savedInstanceState != null) {
    currentSessionId = savedInstanceState.getLong("CURRENT_SESSION_ID", -1);
      } else {
    currentSessionId = -1; // cold launch = fresh chat
    }  
    
    // 1. INITIALIZE DATABASE & VIEWMODEL
    chatDao = AppDatabase.getDatabase(this).chatDao();
    chatViewModel = new ViewModelProvider(this, new ChatViewModelFactory(getApplication(), chatDao)).get(ChatViewModel.class);

       if (savedInstanceState != null) {
     chatViewModel.clearPendingAnimations();
    }
        
    //  The Startup Cleanup Fix: Wipes out any empty sessions left behind by a force-close
    new Thread(() -> {
        chatDao.deleteEmptySessions();
    }).start();
        
    // 2. BIND VIEWS
    bindViews();

       if (currentSessionId != -1) {
    setWelcomeScreenVisible(false);
    progressBar.setVisibility(android.view.View.VISIBLE);
    if (chatViewModel != null) {
        chatViewModel.setSessionId(currentSessionId); 
    }
   }
        
      //  DADDY'S FIX: Proper premium AI layout
   currentMessages = new ArrayList<>();
    chatAdapter = new ChatAdapter(this, currentMessages);

    // Use YOUR TopAnchorLayoutManager — it gags focus-driven auto-scroll
    LinearLayoutManager layoutManager = new TopAnchorLayoutManager(this);
   recyclerView.setLayoutManager(layoutManager);
    recyclerView.setAdapter(chatAdapter);

    //  THE LADDER IS BACK — but it's the DYNAMIC one that shrinks as text grows
    chatSpacerDecoration = new ChatSpacerDecoration();
    recyclerView.addItemDecoration(chatSpacerDecoration);

    // Kill item animations so inserts don't trigger a "show off the new item" scroll
    recyclerView.setItemAnimator(null);
    if (recyclerView.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
        ((androidx.recyclerview.widget.SimpleItemAnimator) recyclerView.getItemAnimator())
                .setSupportsChangeAnimations(false);
    }

    //  NO spacer decoration. Delete the old addItemDecoration line entirely.
    // (You can leave the ChatSpacerDecoration class in the file; just stop attaching it.)
      
    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
         if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                //  THE TOUCH-AT-SEND FIX: ignore any drag inside 1s of hitting send.
                // A drag queued from before the send can no longer kill the pin.
                if (System.currentTimeMillis() - lastSendTime > 1000) {
                    if (pinLocked) userGrabbedDuringPin = true;  // only let her grab AFTER it settles
                    isUserScrolling = true;
                }
            }
        }
    });

          recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
        if (netAsleep || !pinLocked || userGrabbedDuringPin) return;
        //  Removed the hardcoded string checks! The `pinLocked` flag handles this natively now.

        if (!(recyclerView.getLayoutManager() instanceof LinearLayoutManager)) return;

        List<ChatMessageEntity> cur = chatAdapter.getCurrentList();
        if (cur == null) return;
        int userPos = -1;
        for (int i = cur.size() - 1; i >= 0; i--) {
            if (cur.get(i).getType() == ChatMessage.TYPE_USER) { userPos = i; break; }
        }
        if (userPos < 0) return;

        LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
        View userView = lm.findViewByPosition(userPos);

        if (userView == null) {                       
            recyclerView.stopScroll();
            lm.scrollToPositionWithOffset(userPos, 0);
            return;
        }
        if (userView.getTop() != recyclerView.getPaddingTop()) {   
            recyclerView.stopScroll();
            lm.scrollToPositionWithOffset(userPos, 0);
        }
    });


    // 4. Progress Bar & Observers
    progressBar.setVisibility(View.GONE);
    chatAdapter.setChatActionListener(this);
    setupTtsObserver();

        
    // 4. SETUP DRAWER HISTORY
    setupDrawerHistory();// Add this right after you set your adapter or layout manager!
    //  Add this right after you set your adapter or layout manager!
    if (recyclerView.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
        ((androidx.recyclerview.widget.SimpleItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
    }

           // 4. SETUP DRAWER HISTORY & SEARCH
    setupDrawerHistory();
    setupDrawerSearch(); //  ADD THIS LINE SO THE LISTENERS ACTIVATE!

        
        
    // ================================================================
    //  NEW additions below: ASYNC MODEL LOADER & UI ACTIONS BINDING
    // ================================================================

    // 5. ASYNC MODEL LOADER
    // Triggers the background thread file checking & native model compilation
   // 5. ASYNC MODEL LOADER
    new Thread(() -> {
       // DADDY'S FIX: Read from disk! RAM dies when app closes.
       boolean wasFreed = getSharedPreferences("LlamaModelParams", MODE_PRIVATE)
               .getBoolean("was_manually_freed", false);
               
       if (!wasFreed) {
           android.content.SharedPreferences prefs = getSharedPreferences("LlamaModelParams", MODE_PRIVATE);
           boolean useMmap = prefs.getBoolean("param_mmap", true);
          // 1. Fetch the saved Vision Projector filename
            android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
            android.content.SharedPreferences paramsPrefs = getSharedPreferences("LlamaModelParams", MODE_PRIVATE);
            String customMmproj = modelPrefs.getString("custom_mmproj_filename", "");

// 2. Obey the Vision Switch to save memory
            String targetMmproj = paramsPrefs.getBoolean("param_video", true) ? customMmproj : "";

// 3. Call the updated method with ALL FOUR arguments
            boolean loaded = LlamaManager.loadModel(MainActivity.this, getTargetFilename(), targetMmproj, useMmap);

       }      
    }).start();
     

    
   checkAndSaveActiveModelCapabilities();

      chatViewModel.getIsTyping().observe(this, isTyping -> {
    android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
    String selectedModel = modelPrefs.getString("selected_model", "venice");
    boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel);

    if (isLocalModel) {
        if (isTyping) {
            isWaitingForResponse = true;
            sendMessageButton.setEnabled(false);
            sendMessageButton.setAlpha(0.5f);
            sendMessageButton.setImageResource(R.drawable.ic_send_arrow);
            sendMessageButton.setImageTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.send_btn_disabled_icon)));
            sendMessageButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.send_btn_disabled_bg)));
        } else if (!isWaitingForResponse) {
            updateSendButtonState();
        }
        return;
    }

    //  ONLINE / CLOUD MODELS
    isWaitingForResponse = isTyping;

    if (isTyping) {
        sendMessageButton.setEnabled(true);
        sendMessageButton.setAlpha(1.0f);
        sendMessageButton.setImageResource(R.drawable.ic_stop);
        sendMessageButton.setImageTintList(android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(this, R.color.send_btn_active_icon)));
        sendMessageButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(this, R.color.send_btn_active_bg)));
    } else {
     updateSendButtonState();
    }
   });

        
     

         
            // ROUTED SNAP: Universally pins on SEND. The Display List observer will disarm it if Flask is detected.
    chatViewModel.getSnapToTopEvent().observe(this, shouldSnap -> {
        if (shouldSnap != null && shouldSnap) {
            isUserScrolling = false; 
            if (chatAdapter != null) {
                // Pin EVERYTHING initially. (API Key & Local stay pinned, Flask unpins itself).
                pinUserToTop(chatAdapter.getCurrentList()); 
            }
            chatViewModel.resetSnapToTopEvent();
        }
    });

        
     chatViewModel.toastEvent.observe(this, message -> {
       if (message != null && !message.isEmpty()) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
       }
    });

      chatViewModel.getCurrentStreamingText().observe(this, text -> {
    if (text == null || chatAdapter == null || recyclerView == null) return;

    //  THE UI THROTTLE (The Hang Fix)  
    long now = System.currentTimeMillis();  
    if (now - lastStreamRenderTime < 40) return;   
    lastStreamRenderTime = now;  

    int lastPos = chatAdapter.getItemCount() - 1;  
    if (lastPos < 0) return;  

    chatAdapter.getCurrentList().get(lastPos).setMessage(text);  
    RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(lastPos);  
    
    if (holder instanceof ChatAdapter.AIViewHolder) {  
        ChatAdapter.AIViewHolder aiHolder = (ChatAdapter.AIViewHolder) holder;  

        if (aiHolder.aiActionsLayout != null) {  
            aiHolder.aiActionsLayout.setVisibility(View.GONE);  
        }  
          
        // FIX: Pass RAW text. DO NOT heal here! 
        // smartStreamRender -> buildUnits will handle the magic.
        chatAdapter.smartStreamRender(aiHolder.aiMessageContainer, text);  

        // CHUNK-DRIVEN CATCH: Keeps it pinned while it types  
        if (pinLocked && !userGrabbedDuringPin) {  
            int userPos = lastPos - 1;  
            if (userPos >= 0 && recyclerView.getLayoutManager() instanceof androidx.recyclerview.widget.LinearLayoutManager) {  
                androidx.recyclerview.widget.LinearLayoutManager lm =   
                    (androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager();  
                android.view.View uv = lm.findViewByPosition(userPos);  
                  
                if (uv == null || uv.getTop() != recyclerView.getPaddingTop()) {  
                    recyclerView.stopScroll();  
                    lm.scrollToPositionWithOffset(userPos, 0);  
                }  
            }  
        }  
    }  
   });

     chatViewModel.getChatDisplayList().observe(this, chatMessageEntities -> {
    
    //  1. THE UPGRADED CHAT CONTAMINATION FIREWALL 
    if (chatMessageEntities != null && !chatMessageEntities.isEmpty()) {
        long incomingSessionId = chatMessageEntities.get(0).getSessionId();
        if (currentSessionId != -1 && incomingSessionId != currentSessionId && incomingSessionId != -1) {
            if (chatAdapter != null) chatAdapter.setMessages(new java.util.ArrayList<>(), new java.util.ArrayList<>());
            progressBar.setVisibility(android.view.View.VISIBLE);
            return; 
        }
    }

    //  TTS ICON MEMORY FIX
    if (chatViewModel != null && chatAdapter != null) {
        Long activeTtsId = chatViewModel.getCurrentlyPlayingTtsId().getValue();
        chatAdapter.setCurrentlyPlayingMessageId(activeTtsId);
    }

    // 3. Empty State / Loading Logic
    if (chatMessageEntities == null || chatMessageEntities.isEmpty()) {
        unpinConversation(); 
        if (chatAdapter != null) chatAdapter.setMessages(new java.util.ArrayList<>(), new java.util.ArrayList<>());
        wasStreaming = false;
        
        if (currentSessionId == -1) {
            setWelcomeScreenVisible(true);
            progressBar.setVisibility(android.view.View.GONE);
        } else {
            setWelcomeScreenVisible(false);
            progressBar.setVisibility(android.view.View.VISIBLE);
        }
        return;
    }

    progressBar.setVisibility(android.view.View.GONE);
    
    if (chatAdapter == null) return;

    ChatMessageEntity lastMessage = chatMessageEntities.get(chatMessageEntities.size() - 1);
    
    boolean isStreaming   = lastMessage.isStreaming();
    int oldCount          = chatAdapter.getItemCount();
    int newCount          = chatMessageEntities.size();
    boolean isNewAdded    = (oldCount != newCount);
    
    //  Detect the exact moment the 3-dots vanish and the AI bubble takes its place!
    boolean isTypingSwappedForAI = false;
    if (oldCount > 0 && newCount > 0 && oldCount == newCount) {
        int oldLastType = chatAdapter.getCurrentList().get(oldCount - 1).getType();
        if (oldLastType == ChatMessage.TYPE_TYPING && lastMessage.getType() == ChatMessage.TYPE_AI) {
            isTypingSwappedForAI = true;
        }
    }

    boolean streamEnded   = (wasStreaming && !isStreaming);
    wasStreaming          = isStreaming;

    boolean lastIsUser    = (lastMessage.getType() == ChatMessage.TYPE_USER);
    boolean lastIsTyping  = (lastMessage.getType() == ChatMessage.TYPE_TYPING);
    boolean lastIsAI      = (lastMessage.getType() == ChatMessage.TYPE_AI);

    // 4. RESOLVE LAYOUT PINNING FIRST (The Giant Space Fix)
    if (lastIsUser || isStreaming) {
        pinUserToTop(chatMessageEntities);
    } else {
        unpinConversation();
    }

    // ── 5. UPDATE ADAPTER ──
    // 'isTypingSwappedForAI` so the adapter actually receives the first chunk!
    if (isNewAdded || isTypingSwappedForAI || streamEnded || (!isStreaming && lastIsAI && !isNewAdded)) {
        chatAdapter.setMessages(new java.util.ArrayList<>(chatMessageEntities), chatViewModel.getMessagesToAnimate());
        
        if (streamEnded) {
            recyclerView.postDelayed(() -> {
                if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                }
            }, 150);
        }
    }


    // ── 6. CAMERA SCROLLS (WITH THE BULLETPROOF FIX) ─────────────────────────
    if (lastIsTyping) {
        recyclerView.post(() -> {
            if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            }
        });
    } 
    else if (lastIsUser || isStreaming) {
        // Locked automatically by schedulePin() inside pinUserToTop.
    } 
    else  if (streamEnded) {
            recyclerView.postDelayed(() -> {
                if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                }
            }, 150);

            // NEW: runs after the rebind above, so the finished message becomes selectable
            recyclerView.postDelayed(() -> {
                if (chatAdapter == null || chatAdapter.getItemCount() == 0) return;
                RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(chatAdapter.getItemCount() - 1);
                if (vh instanceof ChatAdapter.AIViewHolder) {
                    chatAdapter.finalizeStreamedMessage(((ChatAdapter.AIViewHolder) vh).aiMessageContainer);
                }
            }, 400);
        
        
    }
    else if (!isStreaming && lastIsAI && !isNewAdded) {
        recyclerView.postDelayed(() -> {
            if (chatAdapter != null && chatAdapter.getItemCount() > 0 && recyclerView.getLayoutManager() != null) {
                recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            }
        }, 200);
    }
    else if (isNewAdded && !isStreaming) {
        if (oldCount == 0) {
            recyclerView.postDelayed(() -> {
                if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                    if (recyclerView.getLayoutManager() instanceof androidx.recyclerview.widget.LinearLayoutManager) {
                        ((androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager()).scrollToPositionWithOffset(chatAdapter.getItemCount() - 1, 0);
                    } else {
                        recyclerView.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }
                }
            }, 100);
        } else {
            recyclerView.post(() -> {
                if (chatAdapter != null && chatAdapter.getItemCount() > 0) {
                    recyclerView.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                }
            });
        }
    }

    setWelcomeScreenVisible(false);
   });

        
   chatAdapter.setOnRetryCallback(() -> {
    if (isWaitingForResponse) return; 
    
    if (!isNetworkAvailable()) {
        android.widget.Toast.makeText(MainActivity.this, 
            "Still offline. Please connect and try again.", 
            android.widget.Toast.LENGTH_SHORT).show();
        return;
    }
    
    isWaitingForResponse = true;
    if (sendMessageButton != null) {
        sendMessageButton.setEnabled(false);
    }
    
    // 1. Fetch the custom prompt data
    android.content.SharedPreferences promptPrefs = getSharedPreferences("SystemPromptPrefs", MODE_PRIVATE);
    String savedName = promptPrefs.getString("user_nickname", "").trim();
    String savedPrompt = promptPrefs.getString("custom_system_prompt", "").trim();

    StringBuilder finalSystemPrompt = new StringBuilder();
    if (!savedName.isEmpty()) {
        finalSystemPrompt.append("The user's name is ").append(savedName).append(". ");
    }
    if (!savedPrompt.isEmpty()) {
        finalSystemPrompt.append(savedPrompt);
    }
    
    String promptPayload = finalSystemPrompt.toString().trim();

    // 2. Trigger the ViewModel and pass the payload as a new 2nd parameter
    chatViewModel.retryTemporaryMessage(true, promptPayload);
   });

   // REMOVED: Automatic progress bar control based on isTyping (redundant now)

     // REMOVED: We remove the automatic control of the progress bar based on isTyping.
/*
    chatViewModel.getIsTyping().observe(this, isTyping -> {
    if (isTyping) {
        progressBar.setVisibility(View.VISIBLE);
    } else {
        progressBar.setVisibility(View.GONE);
    }
    });
*/

    //  FULL UPDATED LAUNCHER
   galleryLauncher = registerForActivityResult(
    new ActivityResultContracts.PickVisualMedia(), 
    uri -> {
        if (uri != null) {
            // STEP A: Immediately copy the image to your app's internal storage
            Uri permanentUri = copyToInternalStorage(uri);
            
            if (permanentUri != null) {
                // STEP B: Update the UI with the permanent path
                displayImagePreview(permanentUri);
                
                // STEP C: (Crucial) When you eventually send the message, 
                // make sure your Database/Adapter uses permanentUri.toString()
                Log.d("PhotoPicker", "Permanent Image Path: " + permanentUri.toString());
            } else {
                Toast.makeText(this, "Failed to process image.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.d("PhotoPicker", "No media selected");
        }
    }
   );

       currentModel = resolveModelDisplayName(
        getSharedPreferences("model_prefs", MODE_PRIVATE));

       // ONLY ONE SET CALL
       setStyledModelText(currentModel);

      // 7. SETUP LISTENERS
      setupListeners();

       // 8. START FRESH only if there's no chat to restore
   if (currentSessionId == -1) {
      resetToNewChat();
    }

        new android.os.Handler().postDelayed(() -> {

        if (shouldShowModelScreen()) {
        markModelScreenShown();

        Intent intent = new Intent(MainActivity.this, ModelDownloadActivity.class);
        startActivity(intent);
       }

     }, 2500);

         
        
        
  }

    
   @Override
  public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    //  THE REAL FIX: Force Android to rebuild the screen with the new colors!
    recreate();
  }
    
    
    
    private boolean hasSendableInput() {
    boolean hasText = !inputMessage.getText().toString().trim().isEmpty();
    boolean hasImage = selectedImageUri != null;
    return ALLOW_IMAGE_ONLY_SEND ? (hasText || hasImage) : hasText;
   }


  private void updateSendButtonState() {
    if (isWaitingForResponse) return;   // while a reply is running, the isTyping observer owns the button

    boolean canSend = hasSendableInput();
    sendMessageButton.setEnabled(canSend);
    sendMessageButton.setAlpha(1.0f);
    sendMessageButton.setImageResource(R.drawable.ic_send_arrow);

    int bgColor = canSend ? R.color.send_btn_active_bg : R.color.send_btn_disabled_bg;
    int iconColor = canSend ? R.color.send_btn_active_icon : R.color.send_btn_disabled_icon;

    sendMessageButton.setImageTintList(android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(this, iconColor)));
    sendMessageButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
            androidx.core.content.ContextCompat.getColor(this, bgColor)));
  }

    
    
    
    
       private boolean isSearchModeActive = false;
    private int defaultDrawerWidth = -1;
    private android.os.Handler searchDebounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable activeSearchRunnable;
    private android.animation.ObjectAnimator skeletonPulseAnimator;
  
          private void setupDrawerSearch() {
        View navView = findViewById(R.id.nav_view);
        LinearLayout searchBarContainer = findViewById(R.id.search_bar_container);
        ImageView searchBarIcon = findViewById(R.id.search_bar_icon);
        EditText searchEditText = findViewById(R.id.search_input_edit_text);
        ImageView searchClearBtn = findViewById(R.id.search_clear_btn);
        View normalDrawerScroll = findViewById(R.id.normal_drawer_scroll_view);
        View searchOverlayContainer = findViewById(R.id.search_overlay_container);
        View skeletonLayout = findViewById(R.id.search_skeleton_layout);
        RecyclerView searchResultsRv = findViewById(R.id.search_results_recycler_view);
        TextView searchEmptyText = findViewById(R.id.search_empty_text);

        searchResultsRv.setLayoutManager(new LinearLayoutManager(this));
        
        //  IDENTICAL TO setupDrawerHistory(): Call loadSession() + closeDrawers() directly!
        ChatSearchAdapter searchAdapter = new ChatSearchAdapter(result -> {
            exitDrawerSearchMode(); 
            if (result.sessionId == currentSessionId) {
                drawerLayout.closeDrawers();
                Toast.makeText(this, "Already in this chat.", Toast.LENGTH_SHORT).show();
            } else {
                loadSession(result.sessionId);
                drawerLayout.closeDrawers();
            }
        });
        searchResultsRv.setAdapter(searchAdapter);

        // Pulsing animation for the Skeleton UI
        skeletonPulseAnimator = android.animation.ObjectAnimator.ofFloat(skeletonLayout, "alpha", 1.0f, 0.35f);
        skeletonPulseAnimator.setDuration(550);
        skeletonPulseAnimator.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        skeletonPulseAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);

        View.OnClickListener enterSearchListener = v -> {
            if (!isSearchModeActive) {
                enterDrawerSearchMode(navView, searchBarContainer, searchBarIcon, searchEditText, normalDrawerScroll, searchOverlayContainer);
                triggerDatabaseSearch("", skeletonLayout, searchResultsRv, searchEmptyText, searchAdapter);
            }
        };
        searchBarContainer.setOnClickListener(enterSearchListener);
        searchEditText.setOnClickListener(enterSearchListener);

        searchEditText.setOnTouchListener((v, event) -> {
            if (!isSearchModeActive && event.getAction() == android.view.MotionEvent.ACTION_UP) {
                enterSearchListener.onClick(v);
                return true;
            }
            return false;
        });

        searchBarIcon.setOnClickListener(v -> {
            if (isSearchModeActive) {
                exitDrawerSearchMode();
            } else {
                enterSearchListener.onClick(v);
            }
        });

        searchClearBtn.setOnClickListener(v -> searchEditText.setText(""));

        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isSearchModeActive) return;
                String query = s.toString();
                searchClearBtn.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                triggerDatabaseSearch(query, skeletonLayout, searchResultsRv, searchEmptyText, searchAdapter);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void enterDrawerSearchMode(View navView, LinearLayout searchBarContainer, ImageView searchBarIcon,
                                       EditText searchEditText, View normalDrawerScroll, View searchOverlayContainer) {
        isSearchModeActive = true;

        // DO NOT change navView.getLayoutParams().width or lock the drawer!
        // That is what corrupts DrawerLayout's slide physics and causes the 5% freeze.

        // 1. Expand search bar right margin from 60dp -> 12dp and swap icon to Back Arrow
        LinearLayout.LayoutParams barParams = (LinearLayout.LayoutParams) searchBarContainer.getLayoutParams();
        int margin12dp = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 12f, getResources().getDisplayMetrics());
        barParams.setMarginEnd(margin12dp);
        searchBarContainer.setLayoutParams(barParams);

        searchBarIcon.setImageResource(androidx.appcompat.R.drawable.abc_ic_ab_back_material);

        // 2. Hide normal drawer items and show Search Overlay
        normalDrawerScroll.setVisibility(View.GONE);
        searchOverlayContainer.setVisibility(View.VISIBLE);

        // 3. Activate EditText & pop up keyboard
        searchEditText.setFocusableInTouchMode(true);
        searchEditText.setFocusable(true);
        searchEditText.setClickable(true);
        searchEditText.requestFocus();

        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchEditText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void exitDrawerSearchMode() {
        isSearchModeActive = false;

        LinearLayout searchBarContainer = findViewById(R.id.search_bar_container);
        ImageView searchBarIcon = findViewById(R.id.search_bar_icon);
        EditText searchEditText = findViewById(R.id.search_input_edit_text);
        ImageView searchClearBtn = findViewById(R.id.search_clear_btn);
        View normalDrawerScroll = findViewById(R.id.normal_drawer_scroll_view);
        View searchOverlayContainer = findViewById(R.id.search_overlay_container);

        if (activeSearchRunnable != null) {
            searchDebounceHandler.removeCallbacks(activeSearchRunnable);
        }
        if (skeletonPulseAnimator != null && skeletonPulseAnimator.isRunning()) {
            skeletonPulseAnimator.cancel();
        }

        // 1. Hide keyboard and clear search input
        searchEditText.setText("");
        searchEditText.clearFocus();
        searchEditText.setFocusable(false);
        searchEditText.setClickable(false);
        searchClearBtn.setVisibility(View.GONE);

        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
        }

        // 2. Restore search bar margin without touching navView's LayoutParams!
        LinearLayout.LayoutParams barParams = (LinearLayout.LayoutParams) searchBarContainer.getLayoutParams();
        int margin60dp = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 60f, getResources().getDisplayMetrics());
        barParams.setMarginEnd(margin60dp);
        searchBarContainer.setLayoutParams(barParams);

        // 3. Swap Back Arrow back to Search Icon & restore normal drawer items
        searchBarIcon.setImageResource(R.drawable.ic_search);
        searchOverlayContainer.setVisibility(View.GONE);
        normalDrawerScroll.setVisibility(View.VISIBLE);
    }



    private void triggerDatabaseSearch(String query, View skeletonLayout, RecyclerView searchResultsRv,
                                       TextView searchEmptyText, ChatSearchAdapter searchAdapter) {
        if (activeSearchRunnable != null) {
            searchDebounceHandler.removeCallbacks(activeSearchRunnable);
        }

        // 1. Immediately show pulsing Skeleton UI & hide previous results while searching
        searchResultsRv.setVisibility(View.GONE);
        searchEmptyText.setVisibility(View.GONE);
        skeletonLayout.setVisibility(View.VISIBLE);
        if (!skeletonPulseAnimator.isRunning()) {
            skeletonPulseAnimator.start();
        }

        activeSearchRunnable = () -> new Thread(() -> {
            List<ChatSearchResult> matchedResults = chatDao.searchChatsWithSnippetSync(query.trim());

            runOnUiThread(() -> {
                if (!isSearchModeActive) return;

                if (skeletonPulseAnimator.isRunning()) {
                    skeletonPulseAnimator.cancel();
                }
                skeletonLayout.setAlpha(1.0f);
                skeletonLayout.setVisibility(View.GONE);

                if (matchedResults == null || matchedResults.isEmpty()) {
                    searchResultsRv.setVisibility(View.GONE);
                    searchEmptyText.setVisibility(View.VISIBLE);
                } else {
                    searchEmptyText.setVisibility(View.GONE);
                    searchResultsRv.setVisibility(View.VISIBLE);
                    searchAdapter.setResults(matchedResults, query);
                }
            });
        }).start();

        // 240ms debounce gives the Skeleton UI a crisp, realistic pulse while avoiding DB spam
        searchDebounceHandler.postDelayed(activeSearchRunnable, 240);
    }

    
    private String resolveModelDisplayName(android.content.SharedPreferences modelPrefs) {
    String selected = modelPrefs.getString("selected_model", "venice");
    switch (selected) {
        case "stealth_build":
            return "XL Stealth-Build";
        case "nvidia":
            return "XL-Stealth-Large";
        case "v3":
            return "XL-Qwen3.5 2B";
        case "byok": {
            String model = modelPrefs.getString("selected_byok_model", "");
            return model.isEmpty() ? "XL Stealth-1" : model;
        }
        case "custom": {
            String name = modelPrefs.getString("custom_model_real_name", "");
            return name.isEmpty() ? "Custom Model" : name;
        }
        default:
            return "XL Stealth-1";
    }
  }
    
    
  //  Opens the dynamic ladder, then pins AFTER the spacer is measured
   private void pinUserToTop(List<ChatMessageEntity> list) {
        if (list == null) return;
        pinLocked = true;                 //  arm the net
        userGrabbedDuringPin = false;     //  fresh send → we own the camera again

        int userPos = -1;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getType() == ChatMessage.TYPE_USER) { userPos = i; break; }
        }
        if (userPos < 0) return;

        if (chatSpacerDecoration != null) {
            chatSpacerDecoration.setActive(true);
            pinArmedForInsert = true;   // arm the catch for the incoming insert
            recyclerView.invalidateItemDecorations();
        }
        schedulePin(userPos);
    }

    // The timing fix: pin on the NEXT completed layout, not the current (wrong) one
    private void schedulePin(final int target) {
        if (recyclerView == null) return;

        final android.view.ViewTreeObserver.OnGlobalLayoutListener[] holder =
                new android.view.ViewTreeObserver.OnGlobalLayoutListener[1];
        holder[0] = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this); // ONE shot
                recyclerView.stopScroll();
                if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                    ((LinearLayoutManager) recyclerView.getLayoutManager())
                            .scrollToPositionWithOffset(target, 0);   // flush under the toolbar
                }
            }
        };
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(holder[0]);

        // belt-and-braces if the layout somehow doesn't fire
        recyclerView.postDelayed(() -> {
            recyclerView.stopScroll();
            if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
                ((LinearLayoutManager) recyclerView.getLayoutManager())
                        .scrollToPositionWithOffset(target, 0);
            }
        }, 200);
    }

    //  Collapse the ladder when the conversation is done / cleared
   private void unpinConversation() {
       pinArmedForInsert = false;
        pinLocked = false;                // net off
        userGrabbedDuringPin = false;
        if (chatSpacerDecoration != null) {
            chatSpacerDecoration.setActive(false);
            recyclerView.invalidateItemDecorations();
        }
    }
    
    
     private void setupTtsObserver() {
        chatViewModel.getCurrentlyPlayingTtsId().observe(this, playingId -> {
            chatAdapter.setCurrentlyPlayingMessageId(playingId);
            
            //   Do NOT use notifyDataSetChanged()!
            // We manually reach into the visible screen and swap the play/pause icons.
            // This prevents the RecyclerView from recalculating heights and yanking the camera!
            if (recyclerView != null) {
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    android.view.View child = recyclerView.getChildAt(i);
                    androidx.recyclerview.widget.RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                    
                    if (holder instanceof ChatAdapter.AIViewHolder) {
                        ChatAdapter.AIViewHolder aiHolder = (ChatAdapter.AIViewHolder) holder;
                        if (aiHolder.boundMessage != null) {
                            boolean isPlaying = playingId != null && playingId.equals(aiHolder.boundMessage.getTimestamp());
                            aiHolder.btnTtsPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                        }
                    }
                }
            }
        });
    }

    
  public static class TopAnchorLayoutManager extends androidx.recyclerview.widget.LinearLayoutManager {
    public TopAnchorLayoutManager(android.content.Context context) {
        super(context);
    }

    @Override
    public boolean requestChildRectangleOnScreen(
            androidx.recyclerview.widget.RecyclerView parent,   //  NOT android.view.View
            android.view.View child,
            android.graphics.Rect rect,
            boolean immediate,
            boolean focusedChildVisible) {
        //  Gag the auto-scroll so the growing AI bubble can't yank you to the bottom
        return false;
    }
  }
    
       // Helper method to convert DP to Pixels dynamically
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    
   @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent); 

    if (intent != null && intent.getBooleanExtra("TRIGGER_MODEL_LOAD", false)) {
        
        android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        String selectedModel = modelPrefs.getString("selected_model", "v3");

        //  1. DYNAMICALLY UPDATE UI based on the actual model
        if ("v3".equals(selectedModel)) {
            this.currentModel = "XL-Qwen3.5 2B";
            setStyledModelText("XL-Qwen3.5 2B");
        } else if ("custom".equals(selectedModel)) {
            String customName = modelPrefs.getString("custom_model_real_name", "Custom Model");
            this.currentModel = customName;
            setStyledModelText(customName);
            
            //  SAFE BOOT: Turn off Vision temporarily to save 1GB of RAM during the transition
            getSharedPreferences("LlamaModelParams", MODE_PRIVATE)
                .edit().putBoolean("param_video", false).apply();
        }

        this.isWaitingForResponse = false; 
        
        if (chatViewModel != null) {
            ((androidx.lifecycle.MutableLiveData<Boolean>) chatViewModel.getIsTyping()).postValue(false);
        }

        //  2. ROUTE TO OUR SAFE LOADER
        // Instead of running a raw thread, we use our master method that includes 
        // Intent payload parsing and the 500ms OS Garbage Collection breather!
        launchLlamaInference(null);
    }
  }

   

 private String getRealPathFromURI(Context context, android.net.Uri contentUri) {
    if (contentUri == null) return null;
    
    // Create a temporary file in the cache directory
    File tempFile = new File(context.getCacheDir(), "vision_temp_" + System.currentTimeMillis() + ".jpg");
    
    try (java.io.InputStream inputStream = context.getContentResolver().openInputStream(contentUri);
         java.io.OutputStream outputStream = new java.io.FileOutputStream(tempFile)) {
        
        if (inputStream == null) return null;
        
        byte[] buffer = new byte[8 * 1024]; // 8KB buffer for fast copying
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        outputStream.flush();
        
        // This returns the EXACT absolute path C++ needs (e.g., /data/user/0/...)
        return tempFile.getAbsolutePath(); 
        
    } catch (Exception e) {
        e.printStackTrace();
        return null;
    }
  }

   private android.net.Uri copyToInternalStorage(android.net.Uri temporaryUri) {
    try {
        java.io.InputStream inputStream;
        
        // 1. Safely handle both file:// and content:// URIs
        if ("file".equals(temporaryUri.getScheme()) && temporaryUri.getPath() != null) {
            inputStream = new java.io.FileInputStream(new java.io.File(temporaryUri.getPath()));
        } else {
            inputStream = getContentResolver().openInputStream(temporaryUri);
        }
        
        // 2. Decode the raw data into a standard Android Bitmap
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
        inputStream.close();
        
        if (bitmap == null) {
            android.util.Log.e("VisionDebug", "BitmapFactory failed to decode the image stream!");
            return null;
        }
        
        // 3. Create a fresh file in your app's directory
        String fileName = "chat_img_" + System.currentTimeMillis() + ".jpg";
        java.io.File localFile = new java.io.File(getFilesDir(), fileName);
        java.io.FileOutputStream outputStream = new java.io.FileOutputStream(localFile);
        
        // 4. Force 100% JPEG standard format
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, outputStream);
        
        outputStream.flush();
        outputStream.close();
        
        return android.net.Uri.fromFile(localFile);
        
    } catch (Exception e) {
        android.util.Log.e("VisionDebug", "Exception in copyToInternalStorage: " + e.getMessage());
        e.printStackTrace();
        return null;
    }
  }
  
          @Override
    public void onEditUserMessage(ChatMessageEntity message) {
        if (isWaitingForResponse || message == null) return;

        // Online models only check
        android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        String selectedModel = modelPrefs.getString("selected_model", "venice");
        boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel);
        if (isLocalModel || message.isOfflineModel()) {
            return;
        }

        this.currentlyEditingMessage = message;

        // SELF-HEALING BIND: Always grab the pill view directly so it can NEVER be null!
        if (editMessagePillContainer == null) {
            editMessagePillContainer = findViewById(R.id.edit_message_pill_container);
        }
        if (cancelEditButton == null) {
            cancelEditButton = findViewById(R.id.cancel_edit_button);
        }

       if (editMessagePillContainer != null) {
        // Apply exact Reference Pill style
         android.graphics.drawable.GradientDrawable pillBg = new android.graphics.drawable.GradientDrawable();
          pillBg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
    
        // Dynamic Pill Background
         pillBg.setColor(androidx.core.content.ContextCompat.getColor(this, R.color.edit_pill_bg));
    
           float radius = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 50f, getResources().getDisplayMetrics());
               pillBg.setCornerRadius(radius);
          editMessagePillContainer.setBackground(pillBg);
         editMessagePillContainer.setVisibility(View.VISIBLE);
      }

        if (cancelEditButton != null) {
            cancelEditButton.setOnClickListener(v -> cancelEditMode());
        }

        String oldText = message.getMessage() != null ? message.getMessage() : "";
        inputMessage.setText(oldText);
        inputMessage.setSelection(oldText.length());
        inputMessage.requestFocus();

        if (message.getImageUri() != null && !message.getImageUri().isEmpty()) {
            selectedImageUri = android.net.Uri.parse(message.getImageUri());
            imagePreviewContainer.setVisibility(View.VISIBLE);
            Glide.with(this).load(selectedImageUri).into(imagePreview);
        }

        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(inputMessage, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }



    private void cancelEditMode() {
    currentlyEditingMessage = null;
    if (editMessagePillContainer != null) {
        editMessagePillContainer.setVisibility(View.GONE);
    }
    inputMessage.setText("");
    removeImagePreview();       // clears selectedImageUri and refreshes the button
    updateSendButtonState();
   }

        
  private void bindViews() {
  // Main Chat Views
  recyclerView = findViewById(R.id.recyclerView);
  inputMessage = findViewById(R.id.inputMessage);
  sendMessageButton = findViewById(R.id.send_message_button);
  progressBar = findViewById(R.id.progressBar);
  
  // Welcome Screen Views
  welcomeScreen = findViewById(R.id.welcome_screen); 
  welcomeMainTitleTextView = findViewById(R.id.welcome_main_title_textview); 
  welcomeTitleTextView = findViewById(R.id.welcome_title_textview); 

  // Image Preview
  imagePreviewContainer = findViewById(R.id.image_preview_container);
  imagePreview = findViewById(R.id.image_preview);
  removeImageButton = findViewById(R.id.remove_image_button);
  attachButton = findViewById(R.id.attach_button);

  // Drawer & Toolbar
  drawerLayout = findViewById(R.id.drawer_layout);
  
  //   Set the scrim color programmatically so it doesn't crash XML!
  drawerLayout.setScrimColor(
      androidx.core.content.ContextCompat.getColor(this, R.color.drawer_scrim)
  );

  View navDrawerContent = findViewById(R.id.nav_view);
  drawerButton = findViewById(R.id.drawer_button);
  editButton = findViewById(R.id.edit_button); 
  optionsButton = findViewById(R.id.options_button); 
  historyRecyclerView = navDrawerContent.findViewById(R.id.history_recycler_view); 
  
  //  NEW: Bind the Local LLMs button
  localLlmsButton = navDrawerContent.findViewById(R.id.local_llms_button);

  // Model Selector
  modelNameTextView = findViewById(R.id.model_selector);

  // Drawer Internal Buttons
  setupDrawerInternalButtons(navDrawerContent);
  
        // If a chat was active, hide the welcome screen immediately to prevent the flash
   if (currentSessionId != -1) {
    setWelcomeScreenVisible(false);
    progressBar.setVisibility(android.view.View.VISIBLE);
   }
 }

   private void setStyledModelText(String fullText) {
    if (fullText == null || modelNameTextView == null) return;

    SpannableString spannable = new SpannableString(fullText);
    int end = Math.min(2, fullText.length());

    // Dynamic Black/White for "XL"
    spannable.setSpan(new StyleSpan(Typeface.BOLD), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    spannable.setSpan(new ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)), 0, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

    // Dynamic Grey for the rest
    if (fullText.length() > end) {
        spannable.setSpan(new StyleSpan(Typeface.NORMAL), end, fullText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        spannable.setSpan(new ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)), end, fullText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        spannable.setSpan(new RelativeSizeSpan(0.9f), end, fullText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    modelNameTextView.setText(spannable);
  }
    
   private boolean shouldShowModelScreen() {
    return getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("model_screen_shown", false) == false;
   }

   private void markModelScreenShown() {
    getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("model_screen_shown", true)
            .apply();
   }

    private void setupDrawerHistory() {
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        historyAdapter = new ChatHistoryAdapter(session -> {
            if (session.sessionId == currentSessionId) {
                drawerLayout.closeDrawers();
                Toast.makeText(this, "Already in this chat.", Toast.LENGTH_SHORT).show();
            } else {
                loadSession(session.sessionId);
                drawerLayout.closeDrawers();
            }
        });
        
        historyRecyclerView.setAdapter(historyAdapter);

        chatDao.getAllSessions().observe(this, sessions -> {
            historyAdapter.setSessions(sessions);
            //  Keep current chat highlighted whenever the list refreshes or reorders
            historyAdapter.setSelectedSessionId(currentSessionId);
        });
    }
    
    private void setupDrawerInternalButtons(View navDrawerContent) {
        View newChatBtn = navDrawerContent.findViewById(R.id.new_chat_button);
        if (newChatBtn != null) {
            newChatBtn.setOnClickListener(v -> {
                resetToNewChat();
                drawerLayout.closeDrawers();
            });
        }

      View exploreGemsButton = navDrawerContent.findViewById(R.id.explore_gems_button);
      if (exploreGemsButton != null) {
      exploreGemsButton.setOnClickListener(v -> {
        android.widget.Toast.makeText(MainActivity.this,
                "This option will come soon!", android.widget.Toast.LENGTH_SHORT).show();
        drawerLayout.closeDrawer(GravityCompat.START);
        });
     }
        
    }

       private void setupListeners() {
    optionsButton.setEnabled(true);
    optionsButton.setClickable(true);
    optionsButton.setImageResource(R.drawable.ic_more_vert);
    optionsButton.setOnClickListener(v -> showPopupMenu());

    attachButton.setOnClickListener(v -> {
        showAttachmentOptions();
    });
 
    removeImageButton.setOnClickListener(v -> removeImagePreview());
    imagePreview.setOnClickListener(v -> previewImage());
    drawerButton.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    modelNameTextView.setOnClickListener(v -> showModelSelectorDialog());

    localLlmsButton.setOnClickListener(v -> {
        drawerLayout.closeDrawer(GravityCompat.START);
        android.content.Intent intent = new android.content.Intent(MainActivity.this, ModelDownloadActivity.class);
        startActivity(intent);
    });

    sendMessageButton.setOnClickListener(v -> {
        android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        String selectedModel = modelPrefs.getString("selected_model", "venice");
        boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel);

        if (isWaitingForResponse) {
            if (!isLocalModel) {
                chatViewModel.stopResponse();
            }
        } else {
            handleSendMessage();
        }
    });

   inputMessage.addTextChangedListener(new TextWatcher() {
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    @Override
     public void onTextChanged(CharSequence s, int start, int before, int count) {
    if (isWaitingForResponse) {
        android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        String selectedModel = modelPrefs.getString("selected_model", "venice");
        boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel);

        sendMessageButton.setEnabled(!isLocalModel);
        return;
    }
    updateSendButtonState();
   }

    @Override
    public void afterTextChanged(Editable s) {}
   });
  }

     


  private void handleSendMessage() {
    if (isWaitingForResponse) return;

    String userMsg = inputMessage.getText().toString().trim();
    final android.net.Uri imageToTransmit = selectedImageUri; 
    
    android.util.Log.d("VisionDebug", "========== NEW MESSAGE INITIATED ==========");
    android.util.Log.d("VisionDebug", "User Message: " + userMsg);
    android.util.Log.d("VisionDebug", "Original Image URI: " + (imageToTransmit != null ? imageToTransmit.toString() : "NULL"));

    if (userMsg.isEmpty() && imageToTransmit == null) return;
    
    if (userMsg.isEmpty() && !ALLOW_IMAGE_ONLY_SEND) {
        android.widget.Toast.makeText(this, "Add a message to send with the image.",
                android.widget.Toast.LENGTH_SHORT).show();
        return;
    }
        
    android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
    android.content.SharedPreferences paramsPrefs = getSharedPreferences("LlamaModelParams", MODE_PRIVATE); 
    
    String selectedModel = modelPrefs.getString("selected_model", "venice");
    String customRealName = modelPrefs.getString("custom_model_real_name", ""); 

    boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel); 
    boolean isOnline = isNetworkAvailable();
    
    if (!isLocalModel && !isOnline && chatViewModel.getErrorMessage().getValue() != null) {
        sendMessageButton.setAlpha(0.5f);
        sendMessageButton.postDelayed(() -> sendMessageButton.setAlpha(1.0f), 150);
        android.widget.Toast.makeText(this, "No internet connection, try again after sometime.", android.widget.Toast.LENGTH_SHORT).show();
        inputMessage.setText(""); 
        return; 
    }
    
    if (!isLocalModel && chatViewModel.getErrorMessage().getValue() == null) {
        lastAttemptedMessage = userMsg;
        lastAttemptedImageUri = imageToTransmit;
    }

    android.view.inputmethod.InputMethodManager imm = 
        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) {
        imm.hideSoftInputFromWindow(inputMessage.getWindowToken(), 0);
    }

    setWelcomeScreenVisible(false);

    if (currentSessionId == -1) {
        createNewSession(userMsg);
    }

    chatViewModel.setSessionId(currentSessionId); 
    final long activeSessionId = currentSessionId;

    // Highlight active chat in drawer & bump it to the top of Recent
    if (historyAdapter != null) {
        historyAdapter.setSelectedSessionId(activeSessionId);
    }
    new Thread(() -> {
        chatDao.updateSessionTimestamp(activeSessionId, System.currentTimeMillis());
    }).start();

    //  IF EDITING A MESSAGE: Hide the pill and synchronously prune ViewModel + Room DB!
    final ChatMessageEntity messageBeingEdited = currentlyEditingMessage;
    currentlyEditingMessage = null;
    if (editMessagePillContainer != null) {
        editMessagePillContainer.setVisibility(View.GONE);
    }

    if (messageBeingEdited != null) {
        final long cutoffTimestamp = messageBeingEdited.getTimestamp();
        chatViewModel.truncateHistoryForEdit(activeSessionId, cutoffTimestamp);
    }

    inputMessage.setText("");
    removeImagePreview(); 
    updateToolbarState(false);
    isWaitingForResponse = true; 
    lastSendTime = System.currentTimeMillis(); 

    if (isLocalModel) {
        if (isModelLoading || LlamaManager.isBackendBusy()) {            
            android.widget.Toast.makeText(this, "Model is currently loading in the background... please wait.", android.widget.Toast.LENGTH_LONG).show();            
            isWaitingForResponse = false; 
            return;
        }

        inputMessage.setEnabled(false);
        sendMessageButton.setImageResource(R.drawable.ic_send_arrow);
        sendMessageButton.setEnabled(false); 
        sendMessageButton.setAlpha(0.5f); 

        long currentTime = System.currentTimeMillis();
        boolean isVisionEnabledByUser = paramsPrefs.getBoolean("param_video", true);

        String tempImagePath = null;
        if (imageToTransmit != null && isVisionEnabledByUser) {
            android.util.Log.d("VisionDebug", "Forcing image through Bitmap converter...");
            android.net.Uri permanentUri = copyToInternalStorage(imageToTransmit);
            
            if (permanentUri != null) {
                tempImagePath = permanentUri.getPath();  
                android.util.Log.d("VisionDebug", "Image converted and saved successfully: " + tempImagePath);
            } else {
                android.util.Log.e("VisionDebug", "CRITICAL ERROR: copyToInternalStorage returned NULL!");
            }
        }
        final String finalImagePath = tempImagePath;

        ChatMessageEntity userEntity = new ChatMessageEntity(
                activeSessionId, userMsg, ChatMessage.TYPE_USER, currentTime, "text", null, finalImagePath
        );
        
        chatViewModel.addMessageAndAnimate(userEntity);
        chatViewModel.setTypingState(true);

        android.content.SharedPreferences promptPrefs = getSharedPreferences("SystemPromptPrefs", MODE_PRIVATE);
        String savedPrompt = promptPrefs.getString("custom_system_prompt", "").trim();

        new Thread(() -> {
            try {
                paramsPrefs.edit().putBoolean("was_manually_freed", false).apply();
                LlamaManager.resetManuallyFreedFlag();

                if (!LlamaManager.isLoaded() || (isVisionEnabledByUser && finalImagePath != null && !LlamaManager.isVisionSupported())) {
                    if (LlamaManager.isBackendBusy()) {
                        runOnUiThread(this::resetUIStateOnComplete);
                        return;
                    }

                    isModelLoading = true;
                    Context safeContext = getApplicationContext();
                    runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "Loading AI model into memory...", android.widget.Toast.LENGTH_SHORT).show());
                    
                    boolean useMmap = paramsPrefs.getBoolean("param_mmap", true);

                    String customFilename = "";
                    String customMmproj = "";
                    if ("custom".equals(selectedModel) || (isLocalModel && selectedModel.equals("v3"))) {
                        customFilename = modelPrefs.getString("custom_model_filename", "");
                        
                        if (isVisionEnabledByUser) {
                            customMmproj = modelPrefs.getString("custom_mmproj_filename", "");
                            
                            if (customMmproj.isEmpty() && !customFilename.isEmpty()) {
                                android.content.SharedPreferences customRepo = getSharedPreferences("custom_models", MODE_PRIVATE);
                                int count = customRepo.getInt("custom_model_count", 0);
                                for (int i = 0; i < count; i++) {
                                    if (customFilename.equals(customRepo.getString("custom_model_" + i + "_filename", ""))) {
                                        customMmproj = customRepo.getString("custom_model_" + i + "_mmproj_filename", "");
                                        if (!customMmproj.isEmpty()) {
                                            modelPrefs.edit().putString("custom_mmproj_filename", customMmproj)
                                                      .putBoolean("active_model_has_vision", true).apply();
                                            android.util.Log.d("VisionDebug", "🏆 Auto-recovered Projector: " + customMmproj);
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    String filenameToLoad = customFilename.isEmpty() ? null : customFilename;
                    
                    android.util.Log.d("VisionDebug", "-> Text Model: " + filenameToLoad);
                    android.util.Log.d("VisionDebug", "-> mmproj: " + customMmproj);

                    boolean loadSuccess = LlamaManager.loadModel(safeContext, filenameToLoad, customMmproj, useMmap);

                    if (!loadSuccess || !LlamaManager.isLoaded()) {
                        runOnUiThread(() -> {
                            android.widget.Toast.makeText(MainActivity.this, "Critical Error: Failed to load model.", android.widget.Toast.LENGTH_LONG).show();
                            resetUIStateOnComplete();
                        });
                        return; 
                    }
                    isModelLoading = false;
                }

                String processedUserMsg = userMsg;
                if (finalImagePath != null) {
                    processedUserMsg = "<image>\n" + userMsg;
                }
                
                String finalFormattedPrompt = buildPromptForModel(selectedModel, customRealName, savedPrompt, processedUserMsg);
                
                final long aiTimestamp = System.currentTimeMillis();
                final ChatMessageEntity aiEntity = new ChatMessageEntity(
                        activeSessionId, "", ChatMessage.TYPE_AI, aiTimestamp, "text", null, null
                );
                aiEntity.setOfflineModel(true);
                aiEntity.setStreaming(true);
                
                final StringBuilder responseBuilder = new StringBuilder();
                final boolean[] isFirstToken = {true}; 
                
                int maxTokens = paramsPrefs.getInt("param_max_tokens", 2048);
                float temperature = paramsPrefs.getFloat("param_temperature", 0.7f);
                int topK = paramsPrefs.getInt("param_top_k", 40);
                float topP = paramsPrefs.getFloat("param_top_p", 0.9f);
                float repeatPenalty = paramsPrefs.getFloat("param_repeat_penalty", 1.1f);

                LlamaManager.generateStream(
                    finalFormattedPrompt, 
                    finalImagePath,
                    maxTokens,
                    temperature,
                    topK,
                    topP,
                    repeatPenalty,
                    new LlamaBridge.StreamCallback() {        
                        @Override
                        public void onToken(String token) {
                            responseBuilder.append(token);
                            String currentText = responseBuilder.toString();
                            aiEntity.setMessage(currentText);

                            runOnUiThread(() -> {
                                try {
                                    if (isFirstToken[0]) {
                                        chatViewModel.setTypingState(false);                                        
                                        chatViewModel.addMessageAndAnimate(aiEntity);
                                        isFirstToken[0] = false;
                                        return; 
                                    }

                                    if (chatViewModel.getCurrentStreamingText() instanceof androidx.lifecycle.MutableLiveData) {
                                        ((androidx.lifecycle.MutableLiveData<String>) chatViewModel.getCurrentStreamingText()).setValue(currentText);
                                    }
                                } catch (Exception uiEx) {
                                    uiEx.printStackTrace();
                                }
                            });
                        }

                        @Override
                        public void onComplete() {
                            String finalResponse = responseBuilder.toString();
                            aiEntity.setMessage(finalResponse);
                            aiEntity.setStreaming(false); 

                            runOnUiThread(() -> {
                                if (chatViewModel.getCurrentStreamingText() instanceof androidx.lifecycle.MutableLiveData) {
                                    ((androidx.lifecycle.MutableLiveData<String>) chatViewModel.getCurrentStreamingText()).setValue(finalResponse);
                                }
                                
                                chatViewModel.saveStreamedResponse(activeSessionId, finalResponse);

                                int lastIndex = chatAdapter.getItemCount() - 1;
                                if (lastIndex >= 0) {
                                    chatAdapter.notifyItemChanged(lastIndex); 
                                }

                                resetUIStateOnComplete();
                            });
                        }
                          
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {                                
                                android.widget.Toast.makeText(MainActivity.this, "Error: " + error, android.widget.Toast.LENGTH_LONG).show();
                                resetUIStateOnComplete();
                            });  
                        }
                    }
                );

            } catch (Exception e) {
                runOnUiThread(() -> {
                    android.widget.Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    resetUIStateOnComplete();
                });
            } finally {
                isModelLoading = false;
            }
        }).start();

    } else {
        if (isOnline) {
            dispatchMessageToAI(userMsg, imageToTransmit, false);
        } else {
            isWaitingForResponse = false;
            resetUIStateOnComplete();
            chatViewModel.sendOfflineMessage(userMsg, imageToTransmit);        
        }
    }
  }






    
    private void resetUIStateOnComplete() {
    chatViewModel.setTypingState(false);
    isWaitingForResponse = false;
    sendMessageButton.setImageResource(R.drawable.ic_send_arrow);
    sendMessageButton.setEnabled(true);
    sendMessageButton.setAlpha(1.0f);
    inputMessage.setEnabled(true);
  }
 
  //  HELPER METHOD: Checks if the phone has an active internet connection
   private boolean isNetworkAvailable() {
    android.net.ConnectivityManager connectivityManager = 
        (android.net.ConnectivityManager) getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
    
    if (connectivityManager != null) {
        android.net.NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    return false;
  }
     private void createNewSession(String firstMessage) {
        currentSessionId = System.currentTimeMillis();
        String title = firstMessage.length() > 25 ? firstMessage.substring(0, 25) + "..." : firstMessage;

        //  HIGHLIGHT THIS NEW SESSION IN THE DRAWER IMMEDIATELY
        if (historyAdapter != null) {
            historyAdapter.setSelectedSessionId(currentSessionId);
        }

        android.content.SharedPreferences prefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        String selectedModel = prefs.getString("selected_model", "venice"); 
        String customFilename = prefs.getString("custom_model_filename", "");

        String dbModelIdentifier = selectedModel;
        if ("custom".equals(selectedModel)) {
            dbModelIdentifier = "custom|" + customFilename + "|" + currentModel;
        }

        final String finalModelIdentifier = dbModelIdentifier;
        final String apiModelName = selectedModel; 

        new Thread(() -> {
            ChatSessionEntity newSession = new ChatSessionEntity(title, System.currentTimeMillis(), finalModelIdentifier);
            newSession.sessionId = currentSessionId; 
            chatDao.insertSession(newSession);
            
            if (chatViewModel != null) {
                chatViewModel.generatePunchyTitle(currentSessionId, firstMessage, apiModelName);
            }
            
        }).start();
    }



    // ==================================================
    // LOGIC: STATE SWITCHING (WELCOME / CHAT)
    // ==================================================

      private void resetToNewChat() {
        // RACE CONDITION GUARD: Cancel any background session load operations immediately!
        this.activeLoadingSessionId = -1;

        if (recyclerView != null) {
            recyclerView.stopScroll(); 
        }

        if (chatAdapter != null) {
            chatAdapter.setMessages(new java.util.ArrayList<>(), new java.util.ArrayList<>());
        }

        unpinConversation(); 

        //  CLEAR ANY ACTIVE EDIT MODE PILL
        currentlyEditingMessage = null;
        if (editMessagePillContainer != null) {
            editMessagePillContainer.setVisibility(android.view.View.GONE);
        }

        // 1. Reset Local IDs for the new chat
        currentSessionId = -1;
        lastAttemptedMessage = "";
        lastAttemptedImageUri = null;
        selectedImageUri = null;

        //  CLEAR THE GREY HIGHLIGHT IN THE DRAWER FOR BRAND NEW CHAT
        if (historyAdapter != null) {
            historyAdapter.setSelectedSessionId(-1);
        }
        
        // 2. Clear Input UI
        inputMessage.setText("");
        removeImagePreview();
        setWelcomeScreenVisible(true);
        progressBar.setVisibility(android.view.View.GONE); 
        
        // 3. Tell ViewModel to reset UI states (but NOT the ghost data)
        chatViewModel.resetStateForNewChat();
        
        // 4. Force ViewModel to recognize Session -1 (New Chat)
        chatViewModel.setSessionId(-1L);
   
       
      
     getSharedPreferences("app_state", MODE_PRIVATE).edit()
        .putLong("last_active_session_id", -1).apply();
      
       
    }


    


       private String buildPromptForModel(String selectedModel, String customRealName, String systemPrompt, String userMsg) {
    StringBuilder promptBuilder = new StringBuilder();
    String normalizedName = customRealName != null ? customRealName.toLowerCase() : "";

    android.content.SharedPreferences paramsPrefs = getSharedPreferences("LlamaModelParams", MODE_PRIVATE);
    boolean disableThinking = paramsPrefs.getBoolean("param_disable_thinking", false);

    // Official hardcoded system prompt of Phi-4-reasoning (verbatim from HF template)
    final String PHI4_REASONING_DEFAULT_SYSTEM = "You are Phi, a language model trained by Microsoft to help users. Your role as an assistant involves thoroughly exploring questions through a systematic thinking process before providing the final precise and accurate solutions. This requires engaging in a comprehensive cycle of analysis, summarizing, exploration, reassessment, reflection, backtracing, and iteration to develop well-considered thinking process. Please structure your response into two main sections: Thought and Solution using the specified format:  {Thought section}  {Solution section}. In the Thought section, detail your reasoning process in steps. Each step should include detailed considerations such as analysing questions, summarizing relevant findings, brainstorming new ideas, verifying the accuracy of the current steps, refining any errors, and revisiting previous steps. In the Solution section, based on various attempts, explorations, and reflections from the Thought section, systematically present the final solution that you deem correct. The Solution section should be logical, accurate, and concise and detail necessary steps needed to reach the conclusion. Now, try to solve the following question through the above guidelines:";

    // 1. OFFICIAL SPARK X2.5 ARCHITECTURE (Exact Jinja Template)
    if ("custom".equals(selectedModel) && (normalizedName.contains("spark") || normalizedName.contains("spark2_5") || normalizedName.contains("spark-x"))) {
        final String START_TOKEN = "<｜start\u2581of\u2581sentence｜>";
        final String END_TOKEN   = "<｜end\u2581of\u2581sentence｜>";

        String sysPrompt = systemPrompt.isEmpty() ? "you are a helpful assistant." : systemPrompt;

        promptBuilder.append(START_TOKEN).append("<|System|>\n")
                     .append(sysPrompt)
                     .append(END_TOKEN);

        promptBuilder.append(START_TOKEN).append("<|User|>")
                     .append(userMsg)
                     .append(END_TOKEN);

        promptBuilder.append(START_TOKEN).append("<|Bot|>");

        if (disableThinking) {
            promptBuilder.append("</think>");
        } else {
            promptBuilder.append("<think>");
        }
    }
    // 2. LIQUID AI LFM3 & LFM2 ARCHITECTURE
    else if ("custom".equals(selectedModel) && (normalizedName.contains("lfm") || normalizedName.contains("liquid"))) {
        boolean isLfm2 = normalizedName.contains("lfm2") || normalizedName.contains("lfm-2");
        boolean isNonReasoningOmni = normalizedName.contains("omni");

        String formattedUserMsg = userMsg;
        if (!isLfm2 && formattedUserMsg.startsWith("<image>\n")) {
            formattedUserMsg = "<|vision_start|><|image|><|vision_end|>" + formattedUserMsg.substring(8);
        }

        promptBuilder.append("<|startoftext|>");

        if (!systemPrompt.isEmpty()) {
            promptBuilder.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n");
        } else {
            promptBuilder.append("<|im_start|>system\nYou are a helpful assistant trained by Liquid AI.<|im_end|>\n");
        }

        promptBuilder.append("<|im_start|>user\n").append(formattedUserMsg).append("<|im_end|>\n");
        promptBuilder.append("<|im_start|>assistant\n");

        if (!isLfm2) {
            if (disableThinking || isNonReasoningOmni) {
                promptBuilder.append("<think></think>\n");
            } else {
                promptBuilder.append("<think>\n");
            }
        }
    }
    // 3. MICROSOFT PHI FAMILY (verified against official HF tokenizer_config.json)
    //    MUST stay before the R1/"reason" branch: "phi-4-reasoning" contains "reason".
    else if (normalizedName.contains("phi-") || normalizedName.contains("phi1")
            || normalizedName.contains("phi2") || normalizedName.contains("phi3")
            || normalizedName.contains("phi4")) {

        final boolean isPhi4     = normalizedName.contains("phi-4") || normalizedName.contains("phi4");
        final boolean isPhi4Mini = isPhi4 && normalizedName.contains("mini");
        final boolean isPhi3     = normalizedName.contains("phi-3") || normalizedName.contains("phi3");
        final boolean isReason   = normalizedName.contains("reason");

        if (isPhi4 && !isPhi4Mini) {
            // Phi-4 / Phi-4-reasoning / plus: <|im_start|>role<|im_sep|>content<|im_end|>, NO newlines, NO BOS
            String sys = systemPrompt;
            if (sys.isEmpty() && isReason) sys = PHI4_REASONING_DEFAULT_SYSTEM; // delete this line for blank-when-empty
            if (!sys.isEmpty()) {
                promptBuilder.append("<|im_start|>system<|im_sep|>").append(sys).append("<|im_end|>");
            }
            promptBuilder.append("<|im_start|>user<|im_sep|>").append(userMsg).append("<|im_end|>");
            promptBuilder.append("<|im_start|>assistant<|im_sep|>");
            if (disableThinking && isReason) promptBuilder.append("\n\n"); // your switch, same prefill convention
        }
        else if (isPhi4Mini) {
            // Phi-4-mini-instruct / mini-reasoning: <|role|>content<|end|>, NO newlines, NO BOS
            if (isReason) {
                promptBuilder.append("<|system|>Your name is Phi, an AI math expert developed by Microsoft."); // delete for blank
                if (!systemPrompt.isEmpty()) promptBuilder.append(' ').append(systemPrompt);
                promptBuilder.append("<|end|>");
            } else if (!systemPrompt.isEmpty()) {
                promptBuilder.append("<|system|>").append(systemPrompt).append("<|end|>");
            }
            promptBuilder.append("<|user|>").append(userMsg).append("<|end|>");
            promptBuilder.append("<|assistant|>");
            if (disableThinking && isReason) promptBuilder.append("\n\n");
        }
        else if (isPhi3) {
            // Phi-3 / Phi-3.5: role tags WITH newlines + manual <s> BOS (HF add_bos_token=false)
            promptBuilder.append("<s>");
            if (!systemPrompt.isEmpty()) {
                promptBuilder.append("<|system|>\n").append(systemPrompt).append("<|end|>\n");
            }
            promptBuilder.append("<|user|>\n").append(userMsg).append("<|end|>\n");
            promptBuilder.append("<|assistant|>\n");
        }
        else {
            // Phi-1 / Phi-2: BASE models. No official chat template exists. Plain fallback, no fake tokens.
            if (!systemPrompt.isEmpty()) promptBuilder.append(systemPrompt).append("\n\n");
            promptBuilder.append(userMsg);
        }
    }
    // 4. DEEPSEEK R1 / REASONING DERIVATIVES
    else if (normalizedName.contains("r1") || normalizedName.contains("reason")) {
        if (!systemPrompt.isEmpty()) {
            promptBuilder.append("<｜begin of sentence｜><｜System｜>").append(systemPrompt);
        } else {
            promptBuilder.append("<｜begin of sentence｜>");
        }
        promptBuilder.append("<｜User｜>").append(userMsg).append("<｜Assistant｜>");

        if (disableThinking) {
            promptBuilder.append("<think>\n</think>\n");
        }
    }
    // 5. GEMMA 4 TEMPLATE STYLE
    else if ("custom".equals(selectedModel) && (normalizedName.contains("gemma-4") || normalizedName.contains("gemma4"))) {
        if (!systemPrompt.isEmpty()) {
            promptBuilder.append("<|turn|>system\n").append(systemPrompt).append("<|turn|>\n");
        }
        promptBuilder.append("<|turn|>user\n").append(userMsg).append("<|turn|>\n");
        promptBuilder.append("<|turn|>model\n");
    }
    // 6. GEMMA 1 / 2 / 3 TEMPLATE STYLE
    else if ("custom".equals(selectedModel) && normalizedName.contains("gemma")) {
        if (!systemPrompt.isEmpty()) {
            promptBuilder.append("<start_of_turn>system\n").append(systemPrompt).append("<end_of_turn>\n");
        }
        promptBuilder.append("<start_of_turn>user\n").append(userMsg).append("<end_of_turn>\n");
        promptBuilder.append("<start_of_turn>model\n");
    }
    // 7. LLAMA 3 / 3.1 / 3.2 TEMPLATE STYLE
    else if ("custom".equals(selectedModel) && (normalizedName.contains("llama-3") || normalizedName.contains("llama3"))) {
        promptBuilder.append("<|begin_of_text|>");
        if (!systemPrompt.isEmpty()) {
            promptBuilder.append("<|start_header_id|>system<|end_header_id|>\n\n").append(systemPrompt).append("<|eot_id|>");
        }
        promptBuilder.append("<|start_header_id|>user<|end_header_id|>\n\n").append(userMsg).append("<|eot_id|>");
        promptBuilder.append("<|start_header_id|>assistant<|end_header_id|>\n\n");
    }
    // 8. MINICPM 5 ARCHITECTURE (ChatML with Thinking Control)
    else if (normalizedName.contains("minicpm") || normalizedName.contains("cpm")) {
        if (!systemPrompt.isEmpty()) {
            promptBuilder.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n");
        }
        promptBuilder.append("<|im_start|>user\n").append(userMsg).append("<|im_end|>\n");
        promptBuilder.append("<|im_start|>assistant\n");

        if (disableThinking) {
            promptBuilder.append("<think>\n\n</think>\n\n");
        } else {
            promptBuilder.append("<think>\n");
        }
    }
    // 9. DEFAULT CHATML (Qwen, Venice, V3)
    else {
        if (!systemPrompt.isEmpty()) {
            promptBuilder.append("<|im_start|>system\n").append(systemPrompt).append("<|im_end|>\n");
        }
        promptBuilder.append("<|im_start|>user\n").append(userMsg).append("<|im_end|>\n");
        promptBuilder.append("<|im_start|>assistant\n");

        if (disableThinking && normalizedName.contains("qwen")) {
            promptBuilder.append("<think>\n\n</think>\n\n");
        }
    }

    return promptBuilder.toString();
  }


    private void loadSession(long sessionId) {
        this.activeLoadingSessionId = sessionId;

        if (chatViewModel != null) {
            chatViewModel.prepareForSessionSwitch();
        }

        if (recyclerView != null) {
            recyclerView.stopScroll();
        }

        // CLEAR ANY ACTIVE EDIT MODE PILL WHEN SWITCHING SESSIONS
        currentlyEditingMessage = null;
        if (editMessagePillContainer != null) {
            editMessagePillContainer.setVisibility(android.view.View.GONE);
        }

        this.currentSessionId = sessionId;

        //  HIGHLIGHT ACTIVE CHAT IN DRAWER
        if (historyAdapter != null) {
            historyAdapter.setSelectedSessionId(sessionId);
        }

        setWelcomeScreenVisible(false);
        updateToolbarState(false);
        
        progressBar.setVisibility(android.view.View.VISIBLE);
        
        new Thread(() -> {
            ChatSessionEntity session = chatDao.getSessionByIdSync(sessionId);
            
            if (activeLoadingSessionId != sessionId) return;

            if (session != null && session.modelName != null) {
                runOnUiThread(() -> {
                    if (activeLoadingSessionId != sessionId) return;

                    String dbIdentifier = session.modelName;
                    String prefModel = "venice";
                    String prefFilename = "";
                    String displayName = "XL Stealth-1";

                    if (dbIdentifier.startsWith("custom|")) {
                        String[] parts = dbIdentifier.split("\\|");
                        prefModel = "custom";
                        if (parts.length >= 2) prefFilename = parts[1];
                        if (parts.length >= 3) {
                            displayName = parts[2];
                        } else {
                            displayName = "Custom Model";
                        }
                    } else if ("custom".equals(dbIdentifier)) {
                        prefModel = "custom";
                        displayName = "Custom Model";
                    } else if ("v3".equals(dbIdentifier)) {
                        prefModel = "v3";
                        displayName = "XL-Qwen3.5 2B";
                    }

                    currentModel = displayName;
                    setStyledModelText(currentModel);
                    
                    getSharedPreferences("model_prefs", MODE_PRIVATE)
                            .edit()
                            .putString("selected_model", prefModel)
                            .putString("custom_model_filename", prefFilename)
                            .apply();
                });
            }
        }).start();

        chatViewModel.setSessionId(sessionId); 
        android.widget.Toast.makeText(this, "Loading Chat: " + sessionId, android.widget.Toast.LENGTH_SHORT).show();
    }



    private void setWelcomeScreenVisible(boolean visible) {
        if (visible) {
            welcomeScreen.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            
            // Show main bold title
            if (welcomeMainTitleTextView != null) {
                welcomeMainTitleTextView.setVisibility(View.VISIBLE);
            }
            
        } else {
            welcomeScreen.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void updateToolbarState(boolean isWelcomeMode) {
    editButton.setVisibility(isWelcomeMode ? View.VISIBLE : View.GONE);
    modelNameTextView.setVisibility(View.VISIBLE);
    try {
        setStyledModelText(currentModel);
    } catch (Exception ignored) { }
  }

    private void showPopupMenu() {
    PopupMenu popup = new PopupMenu(MainActivity.this, optionsButton);
    popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());
    popup.getMenu().removeItem(R.id.menu_temporary_chat);

    android.view.MenuItem stopLlmItem = popup.getMenu().findItem(R.id.menu_stop_llm);
    if (stopLlmItem != null) {
        boolean show = false;
        try {
            String selectedModel = getSharedPreferences("model_prefs", MODE_PRIVATE)
                    .getString("selected_model", "venice");
            boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel);
            show = isLocalModel && LlamaManager.isLoaded();
        } catch (Throwable ignored) { }
        stopLlmItem.setVisible(show);
    }

    popup.setOnMenuItemClickListener(item -> {
        int id = item.getItemId();
        if (id == R.id.menu_delete_chat) {
            if (currentSessionId != -1) showDeleteConfirmationDialog();
            else Toast.makeText(this, "No chat to delete.", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.menu_settings) {
            startActivity(new Intent(this, Settings.class));
            return true;
        } else if (id == R.id.menu_stop_llm) {
            showStopAiConfirmationDialog();
            return true;
        }
        return false;
    });

    popup.show();
  }

   private void showStopAiConfirmationDialog() {
    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
    android.view.View view = getLayoutInflater().inflate(R.layout.dialog_stop_ai, null);
    builder.setView(view);

    android.app.AlertDialog dialog = builder.create();
    
    if (dialog.getWindow() != null) {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    view.findViewById(R.id.btnStopAiCancel).setOnClickListener(v -> dialog.dismiss());

    view.findViewById(R.id.btnStopAiConfirm).setOnClickListener(v -> {
        dialog.dismiss(); 
        
        // 1. Immediately notify UI
        isWaitingForResponse = false;
        if (chatViewModel != null) {
            chatViewModel.setTypingState(false);
        }
        
        getSharedPreferences("LlamaModelParams", MODE_PRIVATE)
                .edit().putBoolean("was_manually_freed", true).apply();
                
        android.widget.Toast.makeText(this, "Unloading model safely...", android.widget.Toast.LENGTH_SHORT).show();

        // 2. Offload memory cleanup to a background thread
        new Thread(() -> {
            // The updated free() method now safely stops generation and clears memory 
            // without deadlocking the UI or the callbacks.
            LlamaManager.free(); 
            
            runOnUiThread(() -> {
                android.widget.Toast.makeText(MainActivity.this, "Model freed from memory", android.widget.Toast.LENGTH_SHORT).show();
                resetUIStateOnComplete();
            });
        }).start();
    });

    dialog.show();
  }



  private void showDeleteConfirmationDialog() {
    // 1. Inflate layout view
    android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_confirm, null);
    
    // 2. Build the Alert Dialog overlay container
    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
    builder.setView(dialogView);
    builder.setCancelable(true);
    
    androidx.appcompat.app.AlertDialog dialog = builder.create();
    
    // 3. Make dialog's window background completely transparent so our custom layout round corners show properly
    if (dialog.getWindow() != null) {
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
    }
    
    // 4. Initialize actions inside the custom view
    android.widget.Button btnCancel = dialogView.findViewById(R.id.btnCancel);
    android.widget.Button btnConfirmDelete = dialogView.findViewById(R.id.btnConfirmDelete);
    
    btnCancel.setOnClickListener(v -> dialog.dismiss());
    
    btnConfirmDelete.setOnClickListener(v -> {
        dialog.dismiss();
        deleteCurrentSession(); // Triggers your actual deletion logic 
        
        // Show a single toast verifying that the chat has been removed
        Toast.makeText(MainActivity.this, "Chat deleted", Toast.LENGTH_SHORT).show();
    });
    
    dialog.show();
  }

     public void dispatchMessageToAI(String message, Uri imageUri, boolean isRetry) {
    // 1. Pull the latest saved custom settings
    android.content.SharedPreferences promptPrefs = getSharedPreferences("SystemPromptPrefs", MODE_PRIVATE);
    String savedName = promptPrefs.getString("user_nickname", "").trim();
    String savedPrompt = promptPrefs.getString("custom_system_prompt", "").trim();

    // 2. Combine the Name and Custom Prompt dynamically with distinct role scoping
    StringBuilder finalSystemPrompt = new StringBuilder();
    
    if (!savedPrompt.isEmpty()) {
        finalSystemPrompt.append(savedPrompt).append("\n\n");
    }

    if (!savedName.isEmpty()) {
        //  Wrapping this in structured metadata markers forces the LLM to process 
        // it as environmental variable data about the client, not its identity blueprint.
        finalSystemPrompt.append("[ENVIRONMENTAL META-DATA]\n")
                         .append("- Current Interlocutor/User Name: ").append(savedName).append("\n")
                         .append("- Context: Address the interlocutor by this name when appropriate.\n")
                         .append("[END META-DATA]");
    }

    String promptPayload = finalSystemPrompt.toString().trim();
    boolean isOnline = isNetworkAvailable();

    // 3. Fire it to the ViewModel with the 5th parameter!
    chatViewModel.sendMessageToAI(message, imageUri, isRetry, isOnline, promptPayload);
  }

    private void deleteCurrentSession() {
        if (currentSessionId != -1) {
            final long sessionIdToDelete = currentSessionId;
            new Thread(() -> {
                // 1. Delete all messages associated with the session first
                // Assuming ChatDao.deleteMessagesForSession(long) exists
                chatDao.deleteMessagesForSession(sessionIdToDelete); 
                
                // 2. Delete the session entity itself
                // Assuming ChatDao.deleteSession(long) exists
                chatDao.deleteSession(sessionIdToDelete);
                
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Chat deleted.", Toast.LENGTH_SHORT).show();
                    resetToNewChat();
                });
            }).start();
        }
    }
    
    // ==================================================
    // EXISTING HELPER METHODS
    // ==================================================
    
    private void launchGalleryIntent() {
        galleryLauncher.launch(
            new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()
        );
    }

   private void displayImagePreview(Uri imageUri) {
    selectedImageUri = imageUri;
    imagePreviewContainer.setVisibility(View.VISIBLE);
    Glide.with(this).load(imageUri).into(imagePreview);
    updateSendButtonState();
  }

    
    

      private void removeImagePreview() {
    selectedImageUri = null;
    imagePreviewContainer.setVisibility(View.GONE);
    imagePreview.setImageDrawable(null);
    updateSendButtonState();
   }


   public interface ParameterCallback {
    void onSaved(String newValue);
  }

    
   private boolean isModelVisionCapable(String filename) {
    if (filename == null || filename.isEmpty()) return false;
    
    String normalized = filename.toLowerCase();
    
    // Look for common tags that indicate a vision-language model
    return normalized.contains("vl") || 
           normalized.contains("vision") || 
           normalized.contains("llava") || 
           normalized.contains("pixtral") || 
           normalized.contains("minicpm");
   }

    
    
    private void showAttachmentOptions() {
  android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
  android.content.SharedPreferences paramsPrefs = getSharedPreferences("LlamaModelParams", MODE_PRIVATE);
  
  String selectedModel = modelPrefs.getString("selected_model", "venice");
  String customFilename = modelPrefs.getString("custom_model_filename", "");
  String customRealName = modelPrefs.getString("custom_model_real_name", "");
  String customMmproj = modelPrefs.getString("custom_mmproj_filename", ""); 
  
  boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel);

  final boolean isVisionSupported;
  final boolean isAudioSupported;
  final boolean isThinkingModel;

     if (isLocalModel) {
      if ("custom".equals(selectedModel) && !customMmproj.isEmpty()) {
          isVisionSupported = true;
      } else {
          String textToCheck = "custom".equals(selectedModel) ? customRealName : "model.gguf";
          isVisionSupported = isModelVisionCapable(textToCheck);
      }

      if (LlamaManager.isLoaded()) {
          isAudioSupported = LlamaManager.isAudioSupported();
          // 🚀 Evaluates the GGUF tokenizer.chat_template directly
          isThinkingModel = LlamaManager.isThinkingModel();
      } else {
          String textToCheck = "custom".equals(selectedModel) ? customRealName : "model.gguf";
          isAudioSupported = isModelAudioCapable(textToCheck);
          isThinkingModel = isModelThinkingCapable(textToCheck);
      }
  } else {
      isVisionSupported = true;
      isAudioSupported = true;
      isThinkingModel = false;
  }


  com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = 
      new com.google.android.material.bottomsheet.BottomSheetDialog(this);
  View bottomSheetView = getLayoutInflater().inflate(R.layout.nav2_drawer, null);
  bottomSheetDialog.setContentView(bottomSheetView);

  // THE MAGIC FIX: Make the window transparent so the rounded corners show perfectly!
  if (bottomSheetDialog.getWindow() != null) {
      bottomSheetDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
  }

  bottomSheetView.findViewById(R.id.option_gallery).setOnClickListener(v -> {
      if (!isVisionSupported) {
          android.widget.Toast.makeText(this, "This model doesn't support vision.", android.widget.Toast.LENGTH_SHORT).show();
          return;
      }
      //  FIX: Online models bypass the local vision switch check
      if (isLocalModel && !paramsPrefs.getBoolean("param_video", true)) {
          android.widget.Toast.makeText(this, "Vision is toggled off! Enable the Vision switch below to attach images.", android.widget.Toast.LENGTH_LONG).show();
          return;
      }
      bottomSheetDialog.dismiss();
      launchGalleryIntent();
  });

  bottomSheetView.findViewById(R.id.option_camera_upload).setOnClickListener(v -> {
      if (!isVisionSupported) {
          android.widget.Toast.makeText(this, "This model doesn't support vision.", android.widget.Toast.LENGTH_SHORT).show();
          return;
      }
      // Online models bypass the local vision switch check
      if (isLocalModel && !paramsPrefs.getBoolean("param_video", true)) {
          android.widget.Toast.makeText(this, "Vision is toggled off! Enable the Vision switch below.", android.widget.Toast.LENGTH_LONG).show();
          return;
      }
      android.widget.Toast.makeText(this, "Coming soon!", android.widget.Toast.LENGTH_SHORT).show();
  });

  bottomSheetView.findViewById(R.id.option_files).setOnClickListener(v -> 
      android.widget.Toast.makeText(this, "Coming soon!", android.widget.Toast.LENGTH_SHORT).show());

  if (!isLocalModel) {
      View generationContainer = bottomSheetView.findViewById(R.id.generation_settings_container);
      if (generationContainer != null) generationContainer.setVisibility(View.GONE);
      bottomSheetDialog.show();
      return;
  }
  
  View generationContainer = bottomSheetView.findViewById(R.id.generation_settings_container);
  if (generationContainer != null) generationContainer.setVisibility(View.VISIBLE);
  
  android.widget.TextView tvTemp = bottomSheetView.findViewById(R.id.tv_val_temperature);
  android.widget.TextView tvTopK = bottomSheetView.findViewById(R.id.tv_val_top_k);
  android.widget.TextView tvTopP = bottomSheetView.findViewById(R.id.tv_val_top_p);
  android.widget.TextView tvRepeatPenalty = bottomSheetView.findViewById(R.id.tv_val_repeat_penalty);
  android.widget.TextView tvContextWindow = bottomSheetView.findViewById(R.id.tv_val_context_window);
  android.widget.TextView tvMaxTokens = bottomSheetView.findViewById(R.id.tv_val_max_tokens);

  tvTemp.setText(String.valueOf(paramsPrefs.getFloat("param_temperature", 0.7f)));
  tvTopK.setText(String.valueOf(paramsPrefs.getInt("param_top_k", 40)));
  tvTopP.setText(String.valueOf(paramsPrefs.getFloat("param_top_p", 0.9f)));
  tvRepeatPenalty.setText(String.valueOf(paramsPrefs.getFloat("param_repeat_penalty", 1.1f)));
  tvContextWindow.setText(String.valueOf(paramsPrefs.getInt("param_context_window", 4048)));
  tvMaxTokens.setText(String.valueOf(paramsPrefs.getInt("param_max_tokens", 2048)));

  bottomSheetView.findViewById(R.id.option_temperature).setOnClickListener(v -> {
      showEditParameterDialog("temperature", tvTemp.getText().toString(), false, newValue -> {
          try { 
              float val = Float.parseFloat(newValue); 
              paramsPrefs.edit().putFloat("param_temperature", val).apply(); 
              tvTemp.setText(String.valueOf(val));
          } catch (Exception e) {}
      });
  });

  bottomSheetView.findViewById(R.id.option_top_k).setOnClickListener(v -> {
      showEditParameterDialog("top_k", tvTopK.getText().toString(), true, newValue -> {
          try { 
              int val = Integer.parseInt(newValue); 
              paramsPrefs.edit().putInt("param_top_k", val).apply(); 
              tvTopK.setText(String.valueOf(val));
          } catch (Exception e) {}
      });
  });

  bottomSheetView.findViewById(R.id.option_top_p).setOnClickListener(v -> {
      showEditParameterDialog("top_p", tvTopP.getText().toString(), false, newValue -> {
          try { 
              float val = Float.parseFloat(newValue); 
              paramsPrefs.edit().putFloat("param_top_p", val).apply(); 
              tvTopP.setText(String.valueOf(val));
          } catch (Exception e) {}
      });
  });

  bottomSheetView.findViewById(R.id.option_repeat_penalty).setOnClickListener(v -> {
      showEditParameterDialog("repeat penalty", tvRepeatPenalty.getText().toString(), false, newValue -> {
          try { 
              float val = Float.parseFloat(newValue); 
              paramsPrefs.edit().putFloat("param_repeat_penalty", val).apply(); 
              tvRepeatPenalty.setText(String.valueOf(val));
          } catch (Exception e) {}
      });
  });
  
  bottomSheetView.findViewById(R.id.option_context_window).setOnClickListener(v -> {
      showEditParameterDialog("context window", tvContextWindow.getText().toString(), true, newValue -> {
          try { 
              int val = Integer.parseInt(newValue); 
              paramsPrefs.edit().putInt("param_context_window", val).apply(); 
              tvContextWindow.setText(String.valueOf(val));
              reloadLocalModel(); 
          } catch (Exception e) {}
      });
  });

  bottomSheetView.findViewById(R.id.option_max_tokens).setOnClickListener(v -> {
      showEditParameterDialog("max tokens", tvMaxTokens.getText().toString(), true, newValue -> {
          try { 
              int val = Integer.parseInt(newValue); 
              paramsPrefs.edit().putInt("param_max_tokens", val).apply(); 
              tvMaxTokens.setText(String.valueOf(val));
          } catch (Exception e) {}
      });
  });

  androidx.appcompat.widget.SwitchCompat switchAudio = bottomSheetView.findViewById(R.id.switch_audio);
  androidx.appcompat.widget.SwitchCompat switchVideo = bottomSheetView.findViewById(R.id.switch_video);
  androidx.appcompat.widget.SwitchCompat switchDisableThinking = bottomSheetView.findViewById(R.id.switch_disable_thinking);
  androidx.appcompat.widget.SwitchCompat switchMmap = bottomSheetView.findViewById(R.id.switch_mmap);

  if (isVisionSupported) {
      switchVideo.setEnabled(true);
      switchVideo.setChecked(paramsPrefs.getBoolean("param_video", true));
  } else {
      switchVideo.setEnabled(false);
      switchVideo.setChecked(false);
      paramsPrefs.edit().putBoolean("param_video", false).apply();
  }

  if (isAudioSupported) {
      switchAudio.setEnabled(true);
      switchAudio.setChecked(paramsPrefs.getBoolean("param_audio", true));
  } else {
      switchAudio.setEnabled(false);
      switchAudio.setChecked(false);
      paramsPrefs.edit().putBoolean("param_audio", false).apply();
  }

  if (!isThinkingModel) {
      View thinkingContainer = (View) switchDisableThinking.getParent();
      if (thinkingContainer != null) thinkingContainer.setVisibility(View.GONE);
  } else {
      View thinkingContainer = (View) switchDisableThinking.getParent();
      if (thinkingContainer != null) thinkingContainer.setVisibility(View.VISIBLE);
      switchDisableThinking.setChecked(paramsPrefs.getBoolean("param_disable_thinking", false));
  }

  switchMmap.setChecked(paramsPrefs.getBoolean("param_mmap", true));

  switchAudio.setOnCheckedChangeListener((buttonView, isChecked) -> 
      paramsPrefs.edit().putBoolean("param_audio", isChecked).apply());
  
  switchVideo.setOnCheckedChangeListener((buttonView, isChecked) -> {
      paramsPrefs.edit().putBoolean("param_video", isChecked).apply();
      reloadLocalModel(); 
  });

  switchDisableThinking.setOnCheckedChangeListener((buttonView, isChecked) -> 
      paramsPrefs.edit().putBoolean("param_disable_thinking", isChecked).apply());
  
  switchMmap.setOnCheckedChangeListener((buttonView, isChecked) -> {
      paramsPrefs.edit().putBoolean("param_mmap", isChecked).apply();
      reloadLocalModel();
  });

  bottomSheetDialog.show();
  }



 //  RELOAD METHOD: Reload the local model with new settings
  private void reloadLocalModel() {
    if (isWaitingForResponse) {
        android.widget.Toast.makeText(this, "Cannot reload while generating!", android.widget.Toast.LENGTH_SHORT).show();
        return;
    }
    
    new Thread(() -> {
        LlamaManager.free();
        
        android.content.SharedPreferences paramsPrefs = getSharedPreferences("LlamaModelParams", MODE_PRIVATE);
        paramsPrefs.edit().putBoolean("was_manually_freed", false).apply();
        LlamaManager.resetManuallyFreedFlag();
       
        android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        String selectedModel = modelPrefs.getString("selected_model", "venice");
        String customFilename = modelPrefs.getString("custom_model_filename", "");
        String customMmproj = modelPrefs.getString("custom_mmproj_filename", "");
        
        String targetFilename = "model.gguf"; 
        String targetMmproj = "";
        
        if ("custom".equals(selectedModel) && !customFilename.isEmpty()) {
            targetFilename = customFilename;
            //  MEMORY SAVER: Only pass the projector if the user's Vision switch is ON!
            if (paramsPrefs.getBoolean("param_video", true)) {
                targetMmproj = customMmproj;
            }
        }
        
        boolean useMmap = paramsPrefs.getBoolean("param_mmap", true);
        
        // 🚀 UPDATED: Pass both text and vision filenames
        boolean success = LlamaManager.loadModel(MainActivity.this, targetFilename, targetMmproj, useMmap);

        runOnUiThread(() -> {
            if (success) {
                android.widget.Toast.makeText(this, "Model reloaded with new settings!", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                android.widget.Toast.makeText(this, "Failed to reload model!", android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }).start();
  }

    
  private void showEditParameterDialog(String title, String currentValue, boolean isInteger, ParameterCallback callback) {
    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
    View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_parameter, null);
    builder.setView(dialogView);

    android.app.AlertDialog dialog = builder.create();
    if (dialog.getWindow() != null) {
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
    }

    android.widget.TextView tvTitle = dialogView.findViewById(R.id.dialog_title);
    android.widget.TextView tvMessage = dialogView.findViewById(R.id.dialog_message);
    com.google.android.material.textfield.TextInputEditText input = dialogView.findViewById(R.id.dialog_input);
    android.view.View btnCancel = dialogView.findViewById(R.id.btn_cancel);
    android.view.View btnSet = dialogView.findViewById(R.id.btn_set_value);
    tvTitle.setText("Set " + title);
    tvMessage.setText("Enter new value for " + title + ".");
    input.setText(currentValue);
    
    input.setInputType(isInteger ? 
            android.text.InputType.TYPE_CLASS_NUMBER : 
            (android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL));

    btnCancel.setOnClickListener(v -> dialog.dismiss());
    
    btnSet.setOnClickListener(v -> {
        String result = input.getText().toString().trim();
        if (!result.isEmpty()) {
            callback.onSaved(result);
        }
        dialog.dismiss();
    });

    dialog.show();
    
    input.requestFocus();
    if (dialog.getWindow() != null) {
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
  }
   
   private boolean isModelThinkingCapable(String filename) {
    if (filename == null || filename.isEmpty()) return false;
    
    String normalized = filename.toLowerCase();
    
    // Look for common tags that indicate an internal monologue / reasoning model
    return normalized.contains("thinking") || 
           normalized.contains("reason") || 
           normalized.contains("-r1"); // catches DeepSeek-R1 variants
   }
   
  
   private void checkAndSaveActiveModelCapabilities() {
    android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
    String selectedModel = modelPrefs.getString("selected_model", "venice");
    String customRealName = modelPrefs.getString("custom_model_real_name", "");

    boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel);
    boolean isThinkingCapable = false;
    boolean isVisionCapable = false; // Added vision capability check

    if (isLocalModel) {
        String stringToCheck = "custom".equals(selectedModel) ? customRealName : "model.gguf";
        isThinkingCapable = isModelThinkingCapable(stringToCheck);
        isVisionCapable = isModelVisionCapable(stringToCheck);
    }

    getSharedPreferences("LlamaModelParams", MODE_PRIVATE)
            .edit()
            .putBoolean("active_model_is_thinking", isThinkingCapable)
            .putBoolean("active_model_is_vision", isVisionCapable)
            .apply();
  }

    

  public void showFullScreenImage(Uri imageUri) {
    if (imageUri != null) {
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image_preview);
        dialog.setCancelable(true);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK));
        }

        ImageView previewImageView = dialog.findViewById(R.id.full_screen_preview);
        ImageButton closeButton = dialog.findViewById(R.id.close_button);
        
        Glide.with(this).load(imageUri).into(previewImageView);
        
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
  }

   private void previewImage() {
    if (selectedImageUri != null) {
        // Use a built-in fullscreen theme to strip out default dialog margins
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_image_preview);
        dialog.setCancelable(true);

        // Force the window to stretch to the absolute edges of the screen
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK));
        }

        ImageView previewImageView = dialog.findViewById(R.id.full_screen_preview);
        ImageButton closeButton = dialog.findViewById(R.id.close_button);
        
        Glide.with(this).load(selectedImageUri).into(previewImageView);
        
        closeButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
  }
 
  private String getTargetFilename() {
    android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
    String selectedModel = modelPrefs.getString("selected_model", "venice");
    String customFilename = modelPrefs.getString("custom_model_filename", "");
    
    if ("custom".equals(selectedModel) && !customFilename.isEmpty()) {
        return customFilename;
    }
    return "model.gguf"; // Default standard model
  }
   
 private boolean isModelAudioCapable(String filename) {
    if (filename == null || filename.isEmpty()) return false;
    String normalized = filename.toLowerCase();
    
    return normalized.contains("audio") || 
           normalized.contains("whisper");
 }
    
  // ==========================================
// 1. THE MAIN BOTTOM SHEET DIALOG
// ==========================================
    private void showModelSelectorDialog() {
    com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = 
            new com.google.android.material.bottomsheet.BottomSheetDialog(this);
    android.view.View view = getLayoutInflater().inflate(R.layout.bottom_sheet_model_selector, null);
    bottomSheetDialog.setContentView(view);

    // Bind Views
    android.widget.TextView tabDefault = view.findViewById(R.id.tabDefault);
    android.widget.TextView tabLocal = view.findViewById(R.id.tabLocal);
    android.widget.TextView tabCloud = view.findViewById(R.id.tabCloud);
    android.widget.LinearLayout modelsListContainer = view.findViewById(R.id.modelsListContainer);
    androidx.appcompat.widget.AppCompatButton btnAction = view.findViewById(R.id.btnAction);
    android.widget.ImageView btnClose = view.findViewById(R.id.btnCloseSheet);

    btnClose.setOnClickListener(v -> bottomSheetDialog.dismiss());

    // Get Saved State & Define Lock Parameters
    android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
    String selectedModel = modelPrefs.getString("selected_model", "venice");
    
    boolean isChatActive = chatAdapter != null && chatAdapter.getItemCount() > 0;
    boolean isCurrentModelOnline = !("v3".equals(selectedModel) || "custom".equals(selectedModel));

    // Tab Click Listener
    View.OnClickListener tabClickListener = v -> {
        // Reset all tabs
        tabDefault.setBackgroundResource(0);
        tabDefault.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)); // 🎯 Dynamic
        tabDefault.setTypeface(null, android.graphics.Typeface.NORMAL);
        
        tabLocal.setBackgroundResource(0);
        tabLocal.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)); // 🎯 Dynamic
        tabLocal.setTypeface(null, android.graphics.Typeface.NORMAL);
        
        tabCloud.setBackgroundResource(0);
        tabCloud.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)); // 🎯 Dynamic
        tabCloud.setTypeface(null, android.graphics.Typeface.NORMAL);

        // Highlight selected tab
        android.widget.TextView clickedTab = (android.widget.TextView) v;
        clickedTab.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)); // 🎯 Dynamic
        clickedTab.setTypeface(null, android.graphics.Typeface.BOLD);
        clickedTab.setBackgroundResource(R.drawable.bg_tab_selected);

        // Load content for the tab
        String tabName = "Default";
        if (v.getId() == R.id.tabLocal) tabName = "Local";
        else if (v.getId() == R.id.tabCloud) tabName = "Cloud";

        loadModelsForTab(tabName, modelsListContainer, btnAction, bottomSheetDialog, isChatActive, isCurrentModelOnline);
    };

    tabDefault.setOnClickListener(tabClickListener);
    tabLocal.setOnClickListener(tabClickListener);
    tabCloud.setOnClickListener(tabClickListener);

    // Initial Load (Default Tab)
    loadModelsForTab("Default", modelsListContainer, btnAction, bottomSheetDialog, isChatActive, isCurrentModelOnline);

    bottomSheetDialog.show();
  }

   private void loadModelsForTab(String tab, android.widget.LinearLayout container, androidx.appcompat.widget.AppCompatButton btnAction, 
                              com.google.android.material.bottomsheet.BottomSheetDialog dialog, boolean isChatActive, boolean isCurrentModelOnline) {
    container.removeAllViews();
    android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
    String selectedModel = modelPrefs.getString("selected_model", "venice");

    if (tab.equals("Default")) {
        btnAction.setVisibility(android.view.View.GONE);
        
        addDefaultModelItem(container, "XL Stealth-1", "Great for everyday task", "venice", selectedModel, dialog, isChatActive, isCurrentModelOnline);
        addDefaultModelItem(container, "XL Stealth-Build", "Different Endpoint of Stealth", "stealth_build", selectedModel, dialog, isChatActive, isCurrentModelOnline);
        addDefaultModelItem(container, "XL-Stealth-Large", "Efficient for complex tasks", "nvidia", selectedModel, dialog, isChatActive, isCurrentModelOnline);

    } else if (tab.equals("Local")) {
        btnAction.setVisibility(android.view.View.VISIBLE);
        btnAction.setText("Add your own AI");
        btnAction.setOnClickListener(v -> {
            startActivity(new android.content.Intent(MainActivity.this, ModelDownloadActivity.class));
            dialog.dismiss();
        });

        boolean hasDownloadedModels = false;

        boolean isV3Downloaded = modelPrefs.getBoolean("model_downloaded", false);
        if (isV3Downloaded) {
            addLocalModelItem(container, "XL-Qwen3.5 2B", "Ready for local inference", "v3", "", selectedModel, dialog, isChatActive);
            hasDownloadedModels = true;
        }

        android.content.SharedPreferences customPrefs = getSharedPreferences("custom_models", MODE_PRIVATE);
        int customCount = customPrefs.getInt("custom_model_count", 0);
        for (int i = 0; i < customCount; i++) {
            if (customPrefs.getBoolean("custom_model_" + i + "_downloaded", false)) {
                String name = customPrefs.getString("custom_model_" + i + "_name", "Custom Model");
                String filename = customPrefs.getString("custom_model_" + i + "_filename", "");
                addLocalModelItem(container, name, "Ready for local inference", "custom", filename, selectedModel, dialog, isChatActive);
                hasDownloadedModels = true;
            }
        }

        if (!hasDownloadedModels) {
            android.widget.TextView emptyTv = new android.widget.TextView(this);
            emptyTv.setText("No model downloaded at all.\nTap 'Add your own AI' below to get started.");
            emptyTv.setGravity(android.view.Gravity.CENTER);
            emptyTv.setTextSize(15f);
            emptyTv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary)); // 🎯 Dynamic
            emptyTv.setPadding(32, 100, 32, 100);
            container.addView(emptyTv);
        }

    } else if (tab.equals("Cloud")) {
        btnAction.setVisibility(android.view.View.VISIBLE);
        btnAction.setText("Add Provider (BYOK)");
        btnAction.setOnClickListener(v -> {
            startActivity(new android.content.Intent(MainActivity.this, BYOKActivity.class));
            dialog.dismiss();
        });

        java.util.Map<String, java.util.List<String[]>> groupedProviders = new java.util.HashMap<>();
        android.content.SharedPreferences prefs = getSharedPreferences("api_keys", MODE_PRIVATE);

        int count = prefs.getInt("key_count", 0);
        for (int i = 0; i < count; i++) {
            String key = prefs.getString("key_" + i, null);
            if (key == null || key.trim().isEmpty()) continue;
            
            String provider = com.xxq.xlai.ApiKeyDetector.detectProvider(key);
            if (provider == null || "Unknown Provider".equals(provider)) continue;

            String savedModels = prefs.getString("models_for_key_" + i, "");
            java.util.List<String> modelList = new java.util.ArrayList<>();
            if (!savedModels.trim().isEmpty()) {
                for (String part : savedModels.split(",")) {
                    if (!part.trim().isEmpty()) modelList.add(part.trim());
                }
            }
            if (modelList.isEmpty()) {
                if ("Google AI".equals(provider)) modelList.add("gemma-4-26b-a4b-it");
                else if ("OpenRouter".equals(provider)) modelList.add("nvidia/nemotron-3-ultra-550b-a55b:free");
                else modelList.add("default");
            }

            if (!groupedProviders.containsKey(provider)) groupedProviders.put(provider, new java.util.ArrayList<>());
            for (String model : modelList) {
                groupedProviders.get(provider).add(new String[]{model, "Standard"});
            }
        }

        String customJson = prefs.getString("custom_providers", "[]");
        try {
            org.json.JSONArray customArray = new org.json.JSONArray(customJson);
            for (int i = 0; i < customArray.length(); i++) {
                org.json.JSONObject obj = customArray.getJSONObject(i);
                String name = obj.optString("name", "Custom Server");
                String model = obj.optString("model", "default");
                
                if (!groupedProviders.containsKey(name)) groupedProviders.put(name, new java.util.ArrayList<>());
                groupedProviders.get(name).add(new String[]{model, "Custom"});
            }
        } catch (Exception e) { e.printStackTrace(); }

        for (String providerName : groupedProviders.keySet()) {
            android.widget.LinearLayout providerHeader = new android.widget.LinearLayout(this);
            providerHeader.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            providerHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
            providerHeader.setPadding(16, 32, 16, 32);
            providerHeader.setClickable(true);
            providerHeader.setFocusable(true);
            
            android.util.TypedValue outValue = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            providerHeader.setBackgroundResource(outValue.resourceId);

            android.widget.ImageView iconView = new android.widget.ImageView(this);
            iconView.setImageResource(getProviderIconForBottomSheet(providerName));
            android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(60, 60);
            iconParams.setMarginEnd(24);
            iconView.setLayoutParams(iconParams);

            android.widget.TextView providerText = new android.widget.TextView(this);
            providerText.setText(providerName);
            providerText.setTextSize(16f);
            providerText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)); // 🎯 Dynamic
            providerText.setTypeface(null, android.graphics.Typeface.BOLD);
            android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
            providerText.setLayoutParams(textParams);

            android.widget.ImageView arrowView = new android.widget.ImageView(this);
            arrowView.setImageResource(R.drawable.ic_chevron_right);
            arrowView.setColorFilter(androidx.core.content.ContextCompat.getColor(this, R.color.icon_secondary)); // 🎯 Dynamic
            android.widget.LinearLayout.LayoutParams arrowParams = new android.widget.LinearLayout.LayoutParams(48, 48);
            arrowView.setLayoutParams(arrowParams);

            providerHeader.addView(iconView);
            providerHeader.addView(providerText);
            providerHeader.addView(arrowView);
            container.addView(providerHeader);

            android.widget.LinearLayout modelsContainer = new android.widget.LinearLayout(this);
            modelsContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
            modelsContainer.setVisibility(android.view.View.GONE);
            modelsContainer.setPadding(80, 0, 0, 0); 
            container.addView(modelsContainer);

            java.util.List<String[]> models = groupedProviders.get(providerName);
            if (models != null) {
                for (String[] modelData : models) {
                   addCloudModelItem(modelsContainer, providerName, modelData[0], selectedModel, dialog, isChatActive, isCurrentModelOnline);

                }
            }

            providerHeader.setOnClickListener(v -> {
                if (modelsContainer.getVisibility() == android.view.View.VISIBLE) {
                    modelsContainer.setVisibility(android.view.View.GONE);
                    arrowView.setImageResource(R.drawable.ic_chevron_right);
                } else {
                    modelsContainer.setVisibility(android.view.View.VISIBLE);
                    arrowView.setImageResource(R.drawable.ic_expand_more);
                }
            });
        }
    }
  }

// ==========================================
// HELPER METHODS FOR ROWS & ICONS
// ==========================================

  private void addDefaultModelItem(android.widget.LinearLayout container, String name, String desc, String modelId, String currentSelectedModel, 
                          com.google.android.material.bottomsheet.BottomSheetDialog dialog, boolean isChatActive, boolean isCurrentModelOnline) {
    android.view.View itemView = getLayoutInflater().inflate(R.layout.item_model_selection, container, false);
    android.widget.TextView tvName = itemView.findViewById(R.id.tvModelName);
    android.widget.TextView tvDesc = itemView.findViewById(R.id.tvModelDesc);
    android.widget.ImageView ivCheck = itemView.findViewById(R.id.ivCheck);

    tvName.setText(name);
    tvDesc.setText(desc);
    ivCheck.setVisibility(modelId.equals(currentSelectedModel) ? android.view.View.VISIBLE : android.view.View.GONE);

    itemView.setOnClickListener(v -> {
        if (isChatActive && !isCurrentModelOnline) {
            android.widget.Toast.makeText(this, "Model locked. Start a new chat.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences("model_prefs", MODE_PRIVATE).edit().putString("selected_model", modelId).remove("custom_model_filename").apply();
        checkAndSaveActiveModelCapabilities();
        currentModel = name;
        setStyledModelText(currentModel);
        android.widget.Toast.makeText(this, name + " selected", android.widget.Toast.LENGTH_SHORT).show();
        dialog.dismiss();
    });
    container.addView(itemView);
  }

  private void addLocalModelItem(android.widget.LinearLayout container, String name, String desc, String modelId, String filename, String currentSelectedModel, 
                          com.google.android.material.bottomsheet.BottomSheetDialog dialog, boolean isChatActive) {
    android.view.View itemView = getLayoutInflater().inflate(R.layout.item_model_selection, container, false);
    android.widget.TextView tvName = itemView.findViewById(R.id.tvModelName);
    android.widget.TextView tvDesc = itemView.findViewById(R.id.tvModelDesc);
    android.widget.ImageView ivCheck = itemView.findViewById(R.id.ivCheck);

    tvName.setText(name);
    tvDesc.setText(desc);
    
    String currentFilename = getSharedPreferences("model_prefs", MODE_PRIVATE).getString("custom_model_filename", "");
    boolean isSelected = modelId.equals(currentSelectedModel) && (modelId.equals("v3") || filename.equals(currentFilename));
    ivCheck.setVisibility(isSelected ? android.view.View.VISIBLE : android.view.View.GONE);

    itemView.setOnClickListener(v -> {
        if (isChatActive) {
            android.widget.Toast.makeText(this, "Model locked. Start a new chat.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences("model_prefs", MODE_PRIVATE).edit()
                .putString("selected_model", modelId)
                .putString("custom_model_filename", filename)
                .putString("custom_model_real_name", name)
                .apply();
        checkAndSaveActiveModelCapabilities();
        currentModel = name;
        setStyledModelText(currentModel);
        launchLlamaInference(dialog);
    });
    container.addView(itemView);
  }

   private void addCloudModelItem(android.widget.LinearLayout container, String providerName, String modelName,
                               String currentSelectedModel,
                               com.google.android.material.bottomsheet.BottomSheetDialog dialog,
                               boolean isChatActive, boolean isCurrentModelOnline) {
    android.view.View itemView = getLayoutInflater().inflate(R.layout.item_model_selection, container, false);
    android.widget.TextView tvName = itemView.findViewById(R.id.tvModelName);
    android.widget.TextView tvDesc = itemView.findViewById(R.id.tvModelDesc);
    android.widget.ImageView ivCheck = itemView.findViewById(R.id.ivCheck);
    android.widget.ImageView ivIcon = itemView.findViewById(R.id.ivModelIcon);

    ivIcon.setVisibility(android.view.View.GONE);
    tvName.setText(modelName);
    tvDesc.setText("via " + providerName);

    String savedProvider = getSharedPreferences("model_prefs", MODE_PRIVATE).getString("selected_byok_provider", "");
    String savedModel = getSharedPreferences("model_prefs", MODE_PRIVATE).getString("selected_byok_model", "");
    ivCheck.setVisibility((providerName.equals(savedProvider) && modelName.equals(savedModel))
            ? android.view.View.VISIBLE : android.view.View.GONE);

    itemView.setOnClickListener(v -> {
        // A chat started on a local model is locked to it: no cloud swaps either.
        // Cloud-to-cloud swaps still work because isCurrentModelOnline is true there.
        if (isChatActive && !isCurrentModelOnline) {
            android.widget.Toast.makeText(this, "Model locked. Start a new chat.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        getSharedPreferences("model_prefs", MODE_PRIVATE).edit()
                .putString("selected_model", "byok")
                .putString("selected_byok_provider", providerName)
                .putString("selected_byok_model", modelName)
                .remove("custom_model_filename")
                .apply();
        checkAndSaveActiveModelCapabilities();
        currentModel = modelName;
        setStyledModelText(currentModel);
        android.widget.Toast.makeText(this, modelName + " selected", android.widget.Toast.LENGTH_SHORT).show();
        dialog.dismiss();
    });
    container.addView(itemView);
   }


  private int getProviderIconForBottomSheet(String provider) {
    if (provider == null) return R.drawable.ic_unknown;
    switch (provider) {
        case "OpenAI": return R.drawable.ic_openai;
        case "Anthropic": return R.drawable.ic_anthropic;
        case "Google AI": return R.drawable.ic_google;
        case "OpenRouter": return R.drawable.ic_openrouter;
        case "xAI": return R.drawable.ic_spcxai;
        case "DeepSeek": return R.drawable.ic_deepseek;
        case "Groq": return R.drawable.ic_groq;
        case "Mistral": return R.drawable.ic_mistral_ai;
        case "Nvidia": return R.drawable.ic_nvidia_logo;
        case "Together AI": return R.drawable.ic_together;
        default: return R.drawable.ic_unknown;
    }
  }
    
    
    
   @Override
   protected void onResume() {
    super.onResume();
    // Automatically check if the active model was deleted while we were away
    validateSelectedModel(); 
    checkAndSaveActiveModelCapabilities();
 }

  private void validateSelectedModel() {
    // If we are currently inside an active or past chat session, DO NOT auto-change the model!
    // The model is locked for this session, even if the file was deleted.
    if (currentSessionId != -1) {
        return; 
    }

    android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
    String selectedModel = modelPrefs.getString("selected_model", "venice");
    String customModelFilename = modelPrefs.getString("custom_model_filename", "");

    if ("custom".equals(selectedModel)) {
        // 1. Check if this specific custom model is still in our downloaded list
        android.content.SharedPreferences customPrefs = getSharedPreferences("custom_models", MODE_PRIVATE);
        int customCount = customPrefs.getInt("custom_model_count", 0);
        boolean stillExists = false;
        String currentName = "Custom Model";

        for (int i = 0; i < customCount; i++) {
            boolean isDownloaded = customPrefs.getBoolean("custom_model_" + i + "_downloaded", false);
            String filename = customPrefs.getString("custom_model_" + i + "_filename", "");
            
            if (isDownloaded && filename.equals(customModelFilename)) {
                stillExists = true;
                currentName = customPrefs.getString("custom_model_" + i + "_name", "Custom Model");
                break;
            }
        }

        if (!stillExists) {
            //  THE MODEL WAS DELETED! Fall back to Stealth.
            modelPrefs.edit()
                    .putString("selected_model", "venice")
                    .remove("custom_model_filename")
                    .apply();
            
            currentModel = "XL Stealth-1";
            setStyledModelText(currentModel);
        } else {
            //  It still exists, just ensure the heading is displaying it
            currentModel = currentName;
            setStyledModelText(currentModel);
        }
        
    } else if ("v3".equals(selectedModel)) {
        // 2. Check if the standard V3 model was deleted
        boolean isV3Downloaded = modelPrefs.getBoolean("model_downloaded", false);
        if (!isV3Downloaded) {
             modelPrefs.edit().putString("selected_model", "venice").apply();
             currentModel = "XL Stealth-1";
             setStyledModelText(currentModel);
        } else {
             currentModel = "XL-Qwen3.5 2B";
             setStyledModelText(currentModel);
        }
        
    } else {
        // 3. Venice/Stealth is selected
        currentModel = "XL Stealth-1";
        setStyledModelText(currentModel);
    }
  }

          private void launchLlamaInference(com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog) {
        if (isModelLoading) {
            android.widget.Toast.makeText(this, "Model is already loading...", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        isModelLoading = true;
        android.widget.Toast.makeText(this, "Loading model into memory...", android.widget.Toast.LENGTH_SHORT).show();
        
        if (bottomSheetDialog != null) {
            bottomSheetDialog.dismiss();
        }

        android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        android.content.SharedPreferences paramsPrefs = getSharedPreferences("LlamaModelParams", MODE_PRIVATE);
        
        String selectedModel = modelPrefs.getString("selected_model", "venice");
        String customFilename = modelPrefs.getString("custom_model_filename", "");
        String customMmproj = modelPrefs.getString("custom_mmproj_filename", "");
        
        String targetFilename = "model.gguf"; 
        String targetMmproj = "";
        
        //  THE BYPASS GUARD: If we came from the download screen, USE THE INTENT!
        Intent currentIntent = getIntent();
        if (currentIntent != null && currentIntent.hasExtra("INTENT_GGUF_FILE")) {
            targetFilename = currentIntent.getStringExtra("INTENT_GGUF_FILE");
            targetMmproj = currentIntent.getStringExtra("INTENT_MMPROJ_FILE");
            
            // Consume the intent so regular bottom sheet launches don't accidentally read it again
            currentIntent.removeExtra("INTENT_GGUF_FILE");
            currentIntent.removeExtra("INTENT_MMPROJ_FILE");
        } 
        // Otherwise, fall back to standard SharedPreferences for Bottom Sheet dialogs
        else if ("custom".equals(selectedModel) && !customFilename.isEmpty()) {
            targetFilename = customFilename;
            if (paramsPrefs.getBoolean("param_video", true)) {
                targetMmproj = customMmproj;
            }
        }

        final String finalTargetFilename = targetFilename;
        final String finalTargetMmproj = targetMmproj;

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            new Thread(() -> {
                long startTime = System.currentTimeMillis();

                if (LlamaManager.isLoaded()) {
                    try { LlamaManager.free(); } catch (Exception e) {} 
                }

                paramsPrefs.edit().putBoolean("was_manually_freed", false).apply();
                LlamaManager.resetManuallyFreedFlag();
                
                boolean useMmap = paramsPrefs.getBoolean("param_mmap", true);
                
                boolean loaded = LlamaManager.loadModel(MainActivity.this, finalTargetFilename, finalTargetMmproj, useMmap);
                long seconds = (System.currentTimeMillis() - startTime) / 1000;

                runOnUiThread(() -> {
                    isModelLoading = false; 
                    if (loaded) {
                        android.widget.Toast.makeText(MainActivity.this, "Model ready for inference (" + seconds + "s)", android.widget.Toast.LENGTH_LONG).show();
                    } else {
                        android.widget.Toast.makeText(MainActivity.this, "Failed to load model. Device may be low on memory.", android.widget.Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        }, 500); 
    }

    
   @Override
    public void onCopy(ChatMessageEntity message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("AI Message", message.getMessage());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onRegenerate(ChatMessageEntity message) {
        android.content.SharedPreferences modelPrefs = getSharedPreferences("model_prefs", MODE_PRIVATE);
        String selectedModel = modelPrefs.getString("selected_model", "venice");
        boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel);
        boolean isOnline = isNetworkAvailable();
        
        chatViewModel.regenerateMessage(message, null, isOnline);
    }
    
    @Override
    public void onShare(ChatMessageEntity message) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, message.getMessage());
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }
    
    @Override
    public void onPlayTts(ChatMessageEntity message) {
        chatViewModel.playTts(message);
    }
    
    @Override
    public void onStopTts() {
        chatViewModel.stopTts();
    }

    
       @Override
    public void onAnimationFinished() {
        // Restores the send button only when the fake-typing effect completely finishes
        if (chatViewModel != null) {
            chatViewModel.setTypingState(false);
        }
    }

   @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    
    // Save to the Bundle (standard way)
    outState.putLong("CURRENT_SESSION_ID", currentSessionId);
    
    // BACKUP: Save to SharedPreferences just in case the Bundle dies
    getSharedPreferences("app_state", MODE_PRIVATE).edit()
        .putLong("last_active_session_id", currentSessionId).apply();
   }
    
   @Override
   protected void onDestroy() {
    // Shut down the adapter before the screen dies to prevent theme-switch crashes
    if (chatAdapter != null) {
        chatAdapter.clearAllAnimations();
    }
    if (historyAdapter != null) {
        // If history adapter has any handlers, clear them too
    }
    super.onDestroy();
   }
    
    
  public static class ChatSpacerDecoration extends RecyclerView.ItemDecoration {
    private boolean isActive = false;

    public void setActive(boolean active) {
        this.isActive = active;
    }

       public boolean isActive() { return isActive; } 
        
    @Override
    public void getItemOffsets(@androidx.annotation.NonNull android.graphics.Rect outRect, 
                               @androidx.annotation.NonNull View view, 
                               @androidx.annotation.NonNull RecyclerView parent, 
                               @androidx.annotation.NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        int itemCount = state.getItemCount();
        
        // Apply the dynamic cushion ONLY to the last AI message during streaming
        if (isActive && position == itemCount - 1) {
            View userView = parent.getLayoutManager().findViewByPosition(position - 1);
            if (userView != null) {
                int contentHeight = userView.getHeight() + view.getHeight();
                int rvHeight = parent.getHeight() - parent.getPaddingTop() - parent.getPaddingBottom();
                
                //  THE DYNAMIC SHRINKING SPACER
                // Calculates exactly how much space is needed to reach the bottom.
                // As the AI view (view.getHeight()) grows, this value naturally shrinks to 0!
                int dynamicSpacer = rvHeight - contentHeight;
                outRect.bottom = Math.max(0, dynamicSpacer);
            } else {
                outRect.bottom = 0;
            }
        } else {
            outRect.bottom = 0;
        }
    }
   }


   
}