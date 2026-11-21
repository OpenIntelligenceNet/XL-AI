package com.xxq.xlai;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString; // <-- NEW Import
import android.text.style.UnderlineSpan; // <-- NEW Import
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;

public class NewChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<NewChatMessageEntity> messages;
    private final Markwon markwon;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Map key is the entity to manage animations specific to that message
    private final Map<NewChatMessageEntity, Runnable> runningAnimations = new HashMap<>(); 

    private List<Long> messagesToAnimateTimestamps;

    private static final int CHUNK_DELAY_MS = 40; 
    private static final int WORDS_PER_CHUNK = 3; 

    private final SourceClickListener sourceClickListener; // <-- NEW Field

    public NewChatAdapter(Context context, List<NewChatMessageEntity> messages, SourceClickListener listener) { // <-- NEW Parameter
        this.messages = messages;
        this.sourceClickListener = listener; // <-- NEW Initialization
        this.markwon = Markwon.builder(context)
                .usePlugin(HtmlPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .build();
    }
    
    public void setMessages(List<NewChatMessageEntity> newMessages, List<Long> messagesToAnimateTimestamps) {
        // Stop all current animations when new data arrives
        runningAnimations.values().forEach(handler::removeCallbacks);
        runningAnimations.clear();
        
        this.messagesToAnimateTimestamps = messagesToAnimateTimestamps;
        this.messages.clear();
        this.messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == NewChatMessage.TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.new_chat_item_user, parent, false);
            return new UserImageViewHolder(view);
        } else if (viewType == NewChatMessage.TYPE_AI) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.new_chat_item_ai, parent, false);
            return new AIViewHolder(view);
        } else if (viewType == NewChatMessage.TYPE_TYPING) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.chat_item_typing, parent, false);
            return new TypingViewHolder(view);
        } else {
             throw new IllegalArgumentException("Unknown view type: " + viewType);
        }
    }

    
    public static class UserImageViewHolder extends UserViewHolder {
        ImageView userImage;
        UserImageViewHolder(View itemView) {
            super(itemView);
            userImage = itemView.findViewById(R.id.userImage);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holderRaw, int position) {
        NewChatMessageEntity message = messages.get(position);

        if (holderRaw instanceof UserImageViewHolder) {
            UserImageViewHolder userImageHolder = (UserImageViewHolder) holderRaw;
            
            if (message.getImageUri() != null) {
                userImageHolder.userImage.setVisibility(View.VISIBLE);
                Glide.with(userImageHolder.itemView.getContext())
                        .load(Uri.parse(message.getImageUri()))
                        .placeholder(R.drawable.loading_placeholder)
                        .error(R.drawable.image_load_error)
                        .into(userImageHolder.userImage);
            } else {
                userImageHolder.userImage.setVisibility(View.GONE);
            }
            
            if (message.getMessage() != null && !message.getMessage().isEmpty()) {
                userImageHolder.userMessage.setText(message.getMessage());
                userImageHolder.userMessage.setVisibility(View.VISIBLE);
            } else {
                userImageHolder.userMessage.setVisibility(View.GONE);
            }
        } else if (holderRaw instanceof AIViewHolder) {
            AIViewHolder aiHolder = (AIViewHolder) holderRaw;
            aiHolder.boundMessage = message; 

            // Stop any running animation for this item before binding
            Runnable existing = runningAnimations.remove(message);
            if (existing != null) {
                handler.removeCallbacks(existing);
            }

            // Check if this message needs animation
            if (messagesToAnimateTimestamps != null && messagesToAnimateTimestamps.contains(message.getTimestamp())) {
                aiHolder.aiMessage.setText("");
                startTypingAnimation(aiHolder, message); 
                messagesToAnimateTimestamps.remove(message.getTimestamp());
                
                // Hide sources button while animating
                aiHolder.aiSourcesButton.setVisibility(View.GONE); // <-- HIDE DURING ANIMATION
                aiHolder.aiSourcesButton.setOnClickListener(null);
            } else {
                // Message is already loaded or animation is complete
                markwon.setMarkdown(aiHolder.aiMessage, message.getMessage());
                aiHolder.aiMessage.setAlpha(1f); // Ensure visibility
                
                // --- SOURCES BUTTON LOGIC START ---
                List<SourceResult> sources = message.getSources();
                if (sources != null && !sources.isEmpty()) {
                    // 1. Make the button visible
                    aiHolder.aiSourcesButton.setVisibility(View.VISIBLE);
                    
                    // 2. Add the underline effect using SpannableString
                    SpannableString content = new SpannableString(aiHolder.aiSourcesButton.getText());
                    content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
                    aiHolder.aiSourcesButton.setText(content);

                    // 3. Handle the click event
                    aiHolder.aiSourcesButton.setOnClickListener(v -> {
                        if (sourceClickListener != null) {
                            sourceClickListener.onSourceClicked(sources);
                        }
                    });
                } else {
                    // Hide the button for regular non-search responses
                    aiHolder.aiSourcesButton.setVisibility(View.GONE);
                    aiHolder.aiSourcesButton.setOnClickListener(null); 
                }
                // --- SOURCES BUTTON LOGIC END ---
            }
        } else if (holderRaw instanceof TypingViewHolder) {
            TypingViewHolder typingHolder = (TypingViewHolder) holderRaw;
            startDotAnimation(typingHolder);
        }
    }

    private void startDotAnimation(TypingViewHolder holder) {
        // ... (Dot animation logic remains unchanged) ...
        animateDot(holder.dot1, 0);
        animateDot(holder.dot2, 200);
        animateDot(holder.dot3, 400);
    }

    private void animateDot(View dot, int delay) {
        // ... (Dot animation logic remains unchanged) ...
        dot.setScaleX(1f);
        dot.setScaleY(1f);
        dot.setAlpha(0.3f);
        dot.animate()
                .scaleX(1.4f)
                .scaleY(1.4f)
                .alpha(1f)
                .setStartDelay(delay)
                .setDuration(300)
                .withEndAction(() -> {
                    dot.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(0.3f)
                            .setDuration(300)
                            .setStartDelay(0)
                            .withEndAction(() -> animateDot(dot, 0))
                            .start();
                })
                .start();
    }

    /**
     * Corrected animation to reveal text without the blinking alpha fade.
     */
    private void startTypingAnimation(AIViewHolder holder, NewChatMessageEntity message) {
        Runnable prev = runningAnimations.remove(message);
        if (prev != null) handler.removeCallbacks(prev);

        final WeakReference<AIViewHolder> holderRef = new WeakReference<>(holder);
        final String fullText = message.getMessage() != null ? message.getMessage() : "";
        final String[] words = fullText.split("\\s+");
        final StringBuilder currentText = new StringBuilder();

        Runnable runnable = new Runnable() {
            int index = 0;

            @Override
            public void run() {
                AIViewHolder currentHolder = holderRef.get();
                // Check if the holder is valid and still bound to this message
                if (currentHolder == null || currentHolder.boundMessage != message) {
                    runningAnimations.remove(message);
                    return;
                }

                int end = Math.min(index + WORDS_PER_CHUNK, words.length);
                for (int i = index; i < end; i++) {
                    // Ensure proper spacing when appending words
                    if (currentText.length() > 0 && !currentText.toString().endsWith(" ")) currentText.append(" ");
                    currentText.append(words[i]);
                }
                index = end;
                
                // FIX: Set text directly. Removed the continuous alpha fading.
                markwon.setMarkdown(currentHolder.aiMessage, currentText.toString());
                
                if (index < words.length) {
                    handler.postDelayed(this, CHUNK_DELAY_MS);
                } else {
                    // Animation finished
                    runningAnimations.remove(message); 
                    // Final set to ensure Markwon renders any trailing elements/formatting
                    markwon.setMarkdown(currentHolder.aiMessage, fullText); 
                    
                    // --- SHOW SOURCES BUTTON AFTER ANIMATION ---
                    List<SourceResult> sources = message.getSources();
                    if (sources != null && !sources.isEmpty()) {
                        // Show the button
                        currentHolder.aiSourcesButton.setVisibility(View.VISIBLE);
                        
                        // Add underline
                        SpannableString content = new SpannableString(currentHolder.aiSourcesButton.getText());
                        content.setSpan(new UnderlineSpan(), 0, content.length(), 0);
                        currentHolder.aiSourcesButton.setText(content);

                        // Set click listener
                        currentHolder.aiSourcesButton.setOnClickListener(v -> {
                            if (sourceClickListener != null) {
                                sourceClickListener.onSourceClicked(sources);
                            }
                        });
                    }
                }
            }
        };

        runningAnimations.put(message, runnable);
        handler.post(runnable);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView userMessage;
        UserViewHolder(View itemView) {
            super(itemView);
            userMessage = itemView.findViewById(R.id.userMessage);
        }
    }

    static class AIViewHolder extends RecyclerView.ViewHolder {
        TextView aiMessage;
        TextView aiSourcesButton; // <-- NEW: Sources button
        NewChatMessageEntity boundMessage; 
        
        AIViewHolder(View itemView) {
            super(itemView);
            aiMessage = itemView.findViewById(R.id.aiMessage);
            aiSourcesButton = itemView.findViewById(R.id.aiSourcesButton); // <-- NEW: Find the view
            boundMessage = null;
        }
    }

    static class TypingViewHolder extends RecyclerView.ViewHolder {
        View dot1, dot2, dot3;
        TypingViewHolder(View itemView) {
            super(itemView);
            dot1 = itemView.findViewById(R.id.dot1);
            dot2 = itemView.findViewById(R.id.dot2);
            dot3 = itemView.findViewById(R.id.dot3);
        }
    }
}
