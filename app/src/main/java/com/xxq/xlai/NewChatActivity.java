package com.xxq.xlai;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler; 
import android.os.Looper; 
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager; 
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.Animatable;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull; 
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

// ⬅️ CRITICAL CHANGE: Implement the SourceClickListener interface
public class NewChatActivity extends AppCompatActivity implements SourceClickListener {

    private NewChatViewModel chatViewModel;
    private NewChatAdapter chatAdapter;
    private List<NewChatMessageEntity> currentMessages;

    private EditText inputMessage; // Your main EditText
    private ImageButton sendMessageButton;
    private ImageButton attachButton;
    private ImageButton removeImageButton;
    private ImageButton backButton;
    private Toolbar toolbar;
    
    // ⬅️ Mapped fields based on activity_new_chat.xml
    private EditText chatInputEditText; 
    private FrameLayout searchPillWrapper; // Mapped to R.id.search_pill_wrapper
    private boolean isSearchActive = false;
    
    // Declaring UI elements as fields
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private LinearLayout inputLayout; 
    
    // ⬅️ NEW: Scroll-to-Bottom Button
    private ImageButton scrollButton; 
    private final Handler scrollHandler = new Handler(Looper.getMainLooper()); 
    private Runnable hideScrollButtonRunnable; 

    private FrameLayout imagePreviewContainer;
    private ImageView imagePreview;
    private Uri selectedImageUri;

    private ActivityResultLauncher<String> galleryLauncher; 

    // Constants for drawables
    private final int DRAWABLE_SEND = R.drawable.ic_send_arrow;
    private final int DRAWABLE_LOADING = R.drawable.ic_square_loading;

    // State variable to track if AI is actively typing/animating
    private boolean isAITypingOrAnimating = false; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_chat); 

        // 1. Initialize Views
        toolbar = findViewById(R.id.toolbar);
        backButton = findViewById(R.id.back_button);
        recyclerView = findViewById(R.id.recyclerView);
        inputMessage = findViewById(R.id.inputMessage); // The main EditText
        sendMessageButton = findViewById(R.id.send_message_button);
        attachButton = findViewById(R.id.attach_button);
        
        imagePreviewContainer = findViewById(R.id.image_preview_container); 
        imagePreview = findViewById(R.id.image_preview);
        removeImageButton = findViewById(R.id.remove_image_button);
        progressBar = findViewById(R.id.progressBar);
        
        inputLayout = findViewById(R.id.input_container); 
        
        // ⬅️ NEW: Initialize the Scroll-to-Bottom button
        scrollButton = findViewById(R.id.scroll_to_bottom_button); 

        // ⬅️ MAPPED: Initialize Search UI fields
        chatInputEditText = inputMessage; // Re-use inputMessage for clarity
        // Map the new FrameLayout for the search pill
        searchPillWrapper = findViewById(R.id.search_pill_wrapper); 
        
        // 2. Setup Toolbar and Back Button
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        backButton.setOnClickListener(v -> finish()); 

        // 3. Setup RecyclerView and ViewModel
        currentMessages = new ArrayList<>();
        
        // ⬅️ CRITICAL CHANGE: Pass 'this' (the SourceClickListener) to the adapter constructor
        chatAdapter = new NewChatAdapter(this, currentMessages, this); 
        
        // ➡️ Layout Manager: Use Reverse Layout to pin the newest message to the top
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Pushes the stack to the top of the viewport
        layoutManager.setReverseLayout(true); // Newest messages displayed first (at the top)
        recyclerView.setLayoutManager(layoutManager);
        
        recyclerView.setAdapter(chatAdapter);

        // Initialize ViewModel
        NewChatDao newChatDao = AppDatabase.getDatabase(this).newChatDao();
        chatViewModel = new ViewModelProvider(this, new NewChatViewModelFactory(getApplication(), newChatDao)).get(NewChatViewModel.class);
        
        // ⬅️ NEW: Setup Scroll Listener for Scroll Button Visibility
        setupScrollToBottomButton(layoutManager);
        
        // ⬅️ NEW: Set click listener for Scroll Button
        scrollButton.setOnClickListener(v -> {
            recyclerView.smoothScrollToPosition(0); // Scroll to the newest item (position 0 in reverse layout)
            scrollButton.setVisibility(View.GONE); // Hide button immediately after clicking
        });

        
        // 4. Setup Observers
        
        // OBSERVER 1: Loading State and Visibility Control (Initial History Load)
        chatViewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                if (currentMessages.isEmpty()) {
                    progressBar.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    if (inputLayout != null) inputLayout.setVisibility(View.GONE);
                }
            } else {
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                if (inputLayout != null) inputLayout.setVisibility(View.VISIBLE);
            }
            
            if (!isLoading) {
                 // Ensure the icon reverts to SEND once the network request is complete and animation is stopped
                 if (sendMessageButton.getDrawable() instanceof Animatable) {
                    ((Animatable) sendMessageButton.getDrawable()).stop();
                 }
                 sendMessageButton.setImageResource(DRAWABLE_SEND);
            }
        });

        // OBSERVER 2: Chat Messages and Display
        chatViewModel.getChatDisplayList().observe(this, messages -> {
            if (messages != null) {
                
                // ➡️ CRITICAL FIX: Reversing the list before passing it to the adapter
                // This ensures that when the reverseLayout is active, scrolling down
                // reveals older messages, which are now at the higher adapter indices.
                List<NewChatMessageEntity> reversedMessages = new ArrayList<>(messages);
                Collections.reverse(reversedMessages);
                
                chatAdapter.setMessages(reversedMessages, chatViewModel.getMessagesToAnimate());
                
                // Relying on setStackFromEnd(true) for pinning. No explicit scroll needed.
            }
        });
        
        // OBSERVER 3: Send Button Icon Change (FALLBACK for DRAWABLE_LOADING vs DRAWABLE_SEND)
        chatViewModel.getIsLoading().observe(this, isLoading -> {
            if (!isLoading) {
                // Stop animation and revert icon when loading is complete
                if (sendMessageButton.getDrawable() instanceof Animatable) {
                    ((Animatable) sendMessageButton.getDrawable()).stop(); 
                }
                sendMessageButton.setImageResource(DRAWABLE_SEND);
            }
        });
        
        // OBSERVER 4: Button Clickability & Color (Controls isEnabled and Background Color)
        chatViewModel.getIsAITyping().observe(this, isAITyping -> {
            isAITypingOrAnimating = isAITyping;
            updateSendButtonState();
        });


        // 5. Setup Gallery Launcher (Image Picker)
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        displayImagePreview(selectedImageUri);
                    } else {
                        Log.d("PhotoPicker", "No media selected");
                    }
                }
        );

        // 6. Setup Input Field and Send Button Logic
        updateSendButtonState(); // Initial state setup

        // Enable/Disable and color Send Button based on text input
        inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        sendMessageButton.setOnClickListener(v -> {
            String userMsg = inputMessage.getText().toString().trim();
            
            if (!isAITypingOrAnimating) {
                 if (!userMsg.isEmpty() || selectedImageUri != null) {
                    
                    // ⬅️ NEW: Hide the keyboard and clear focus
                    hideKeyboard();
                    
                    // Show the loading icon instantly
                    sendMessageButton.setImageResource(DRAWABLE_LOADING);
                    // START THE ANIMATION
                    if (sendMessageButton.getDrawable() instanceof Animatable) {
                        ((Animatable) sendMessageButton.getDrawable()).start();
                    }
                    
                    // ⬅️ CRITICAL: Check search state before sending
                    chatViewModel.sendMessageToAI(userMsg, selectedImageUri, isSearchActive);
                    
                    // Reset the search state after sending the message
                    setSearchActive(false); 
                    
                    inputMessage.setText("");
                    removeImagePreview();
                }
            }
        });

        // 7. Setup Attachment and Image Preview Logic
        attachButton.setOnClickListener(v -> showAttachmentOptions());
        removeImageButton.setOnClickListener(v -> removeImagePreview());
        imagePreview.setOnClickListener(v -> previewImage());
    }
    
    // ... (rest of the helper methods and interface implementation) ...

    
    // ⬅️ CRITICAL ADDITION: Implementation of the SourceClickListener interface method
    @Override
    public void onSourceClicked(List<SourceResult> sources) {
        // Instantiate the BottomSheetFragment, passing the list of sources
        SourceBottomSheetFragment bottomSheet = new SourceBottomSheetFragment(sources);
        
        // Show the dialog using the Fragment Manager
        bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
    }
    
    // --- Helper Methods ---
    
    // ⬅️ NEW METHOD: Setup Scroll Button logic
    private void setupScrollToBottomButton(LinearLayoutManager layoutManager) {
        
        // Define the Runnable to hide the button after a delay
        hideScrollButtonRunnable = () -> {
            // Re-check if we are at the bottom before hiding.
            if (layoutManager.findFirstVisibleItemPosition() != 0) {
                 scrollButton.setVisibility(View.GONE);
            }
        };

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                
                // If scrolling stops
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                    
                    // Check if we are at the bottom (position 0 in reverse layout)
                    if (firstVisibleItemPosition == 0) {
                        // We are at the bottom, hide the button immediately.
                        scrollButton.setVisibility(View.GONE);
                        scrollHandler.removeCallbacks(hideScrollButtonRunnable); // Cancel any pending hide action
                    } else {
                        // We stopped scrolling and are NOT at the bottom.
                        // Wait a moment, then hide it, giving the user a chance to tap it.
                        scrollHandler.postDelayed(hideScrollButtonRunnable, 1000); // 1 second delay
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    // User starts dragging, cancel any pending hide action
                    scrollHandler.removeCallbacks(hideScrollButtonRunnable);
                }
            }
            
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // dy > 0 means scrolling towards older items (upwards on screen in reverseLayout)
                // dy < 0 means scrolling towards newer items (downwards on screen in reverseLayout)

                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                
                // Show button logic: only when not at the very newest item
                if (firstVisibleItemPosition > 0) {
                    scrollButton.setVisibility(View.VISIBLE);
                }
                
                // Hide button logic: when scrolling down (dy < 0) and we reach or pass the newest item
                if (dy < 0 && firstVisibleItemPosition == 0) {
                    scrollButton.setVisibility(View.GONE);
                    scrollHandler.removeCallbacks(hideScrollButtonRunnable);
                }
            }
        });
        
        // Initial check: if the list is short, hide the button
        if (layoutManager.findFirstVisibleItemPosition() == 0 && layoutManager.findLastVisibleItemPosition() == chatAdapter.getItemCount() - 1) {
            scrollButton.setVisibility(View.GONE);
        }
    }
    
    // ⬅️ NEW METHOD: Hides the Soft Keyboard
    private void hideKeyboard() {
        // Clear focus from the EditText
        inputMessage.clearFocus();
        
        // Get the InputMethodManager
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        
        // Hide the soft keyboard
        if (imm != null) {
            imm.hideSoftInputFromWindow(inputMessage.getWindowToken(), 0);
        }
    }


    // NEW METHOD: Consolidates all button state logic
    private void updateSendButtonState() {
        boolean isInputEmpty = inputMessage.getText().toString().trim().isEmpty();
        
        // The button is ENABLED if:
        // 1. AI is NOT typing/animating (isAITypingOrAnimating is false)
        // 2. AND there is text OR an image.
        boolean shouldBeEnabled = (!isAITypingOrAnimating && 
                                   (!isInputEmpty || selectedImageUri != null));
                                   
        sendMessageButton.setEnabled(shouldBeEnabled);

        // Set color based on clickability state
        int color;
        if (isAITypingOrAnimating) {
            // Disabled state while AI is active (as per request)
            color = Color.parseColor("#f8f8f8"); 
        } else if (shouldBeEnabled) {
            // Enabled state
            color = Color.parseColor("#000000"); 
        } else {
            // Disabled state when input is empty
            color = Color.parseColor("#f8f8f8"); 
        }
        
        sendMessageButton.setBackgroundTintList(ColorStateList.valueOf(color));
    }


    private void launchGalleryIntent() {
        galleryLauncher.launch("image/*"); 
    }

    private void displayImagePreview(Uri imageUri) {
        selectedImageUri = imageUri;
        if (imagePreviewContainer != null) {
             imagePreviewContainer.setVisibility(View.VISIBLE);
        }
        if (imagePreview != null) {
             Glide.with(this).load(imageUri).into(imagePreview);
        }
        updateSendButtonState(); // Update state when image is added
    }

    private void removeImagePreview() {
        selectedImageUri = null;
        if (imagePreviewContainer != null) {
            imagePreviewContainer.setVisibility(View.GONE);
        }
        if (imagePreview != null) {
             imagePreview.setImageDrawable(null);
        }
        updateSendButtonState(); // Update state when image is removed
    }

    private void showAttachmentOptions() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        
        View bottomSheetView = getLayoutInflater().inflate(R.layout.nav3_drawer, null);
        bottomSheetDialog.setContentView(bottomSheetView);

        LinearLayout optionCameraUpload = bottomSheetView.findViewById(R.id.option_camera_upload);
        LinearLayout optionGallery = bottomSheetView.findViewById(R.id.option_gallery);
        
        // ⬅️ GET NEW WEB SEARCH OPTION
        LinearLayout optionWebSearch = bottomSheetView.findViewById(R.id.option_web_search);
        
        LinearLayout optionFiles = bottomSheetView.findViewById(R.id.option_files);

        // ⬅️ CLICK LISTENER FOR WEB SEARCH
        optionWebSearch.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            setSearchActive(true); // Activate the search UI
        });

        optionGallery.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            launchGalleryIntent();
        });

        optionCameraUpload.setOnClickListener(v -> {
            Toast.makeText(this, "Camera coming soon!", Toast.LENGTH_SHORT).show();
        });

        optionFiles.setOnClickListener(v -> {
            Toast.makeText(this, "File picker coming soon!", Toast.LENGTH_SHORT).show();
        });

        bottomSheetDialog.show();
    }
    
    // ⬅️ NEW UI STATE MANAGEMENT METHOD
    public void setSearchActive(boolean active) {
        if (active == isSearchActive) return; // No change needed

        isSearchActive = active;
        
        if (active) {
            // 1. Clear and inflate the search pill into its wrapper
            searchPillWrapper.removeAllViews();
            // R.layout.search_active_pill needs to be created based on the previous response
            View searchPill = getLayoutInflater().inflate(R.layout.search_active_pill, searchPillWrapper, false);
            searchPillWrapper.addView(searchPill);

            // 2. Set the placeholder text for the main input
            chatInputEditText.setHint("Search the web");
            
            // 3. Make the pill and its close button interactive
            ImageView closeButton = searchPill.findViewById(R.id.search_pill_close);
            closeButton.setOnClickListener(v -> setSearchActive(false));
            // Show the pill wrapper
            searchPillWrapper.setVisibility(View.VISIBLE);
            
        } else {
            // Revert to normal chat mode
            searchPillWrapper.removeAllViews();
            searchPillWrapper.setVisibility(View.GONE);
            chatInputEditText.setHint("Write your Message..."); // Revert to your default hint
        }
        // Ensure the send button state is correctly calculated after changing the hint/pill visibility
        updateSendButtonState();
    }


    private void previewImage() {
        if (selectedImageUri != null) {
            final android.app.Dialog dialog = new android.app.Dialog(this);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            // Ensure R.layout.dialog_image_preview exists and is correct.
            dialog.setContentView(R.layout.dialog_image_preview);
            dialog.setCancelable(true);

            ImageView previewImageView = dialog.findViewById(R.id.full_screen_preview);
            ImageButton closeButton = dialog.findViewById(R.id.close_button);

            com.bumptech.glide.Glide.with(this).load(selectedImageUri).into(previewImageView);

            closeButton.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
        }
    }
}