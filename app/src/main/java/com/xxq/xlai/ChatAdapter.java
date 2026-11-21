package com.xxq.xlai;

import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import android.widget.Button;
import java.util.ArrayList;
import com.bumptech.glide.Glide;
import java.util.HashSet;
import java.util.Set;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import io.noties.markwon.ext.tasklist.TaskListPlugin; 
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.core.spans.LastLineSpacingSpan;
import org.commonmark.node.ListItem;
import io.noties.markwon.core.MarkwonTheme; // Required for the theme builder
import io.noties.markwon.MarkwonConfiguration; 
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.html.HtmlPlugin;
import android.widget.HorizontalScrollView;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static class CustomBlockSpacingPlugin extends AbstractMarkwonPlugin {
        private final int blockBottomMarginPx;

        public CustomBlockSpacingPlugin(int blockBottomMarginPx) {
            this.blockBottomMarginPx = blockBottomMarginPx;
        }

        @Override 
        public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
            // CRITICAL FIX: Explicitly set the block margin to guarantee spacing.
            // This is the correct, theme-level way to control space between blocks/list items.
            builder.blockMargin(blockBottomMarginPx);
        }
    }
    
    private List<ChatMessageEntity> messages;
    private final Markwon markwon;
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    private final Map<ChatMessageEntity, Runnable> runningAnimations = new HashMap<>();
    private final Set<Long> animatedTimestamps = new HashSet<>();
    private List<Long> messagesToAnimateTimestamps;
    private boolean hasError = false;
    private Runnable retryCallback;
    private static final int CHUNK_DELAY_MS = 40;
    private static final int WORDS_PER_CHUNK = 3;
    private Long currentlyPlayingMessageId = null;
    private ChatActionListener chatActionListener;
    private static final int TABLE_MAX_CELL_DP = 300;   // cells wrap after this width
    private static final int TABLE_MIN_CELL_DP = 48;    // tiny cells ("1", checkbox) never get thinner than this
     
    
    
       public ChatAdapter(Context context, List<ChatMessageEntity> messages) {
    this.messages = messages;
    
     final float density = context.getResources().getDisplayMetrics().density;
   final int NO_BACKGROUND = 0x00FFFFFF;
   io.noties.markwon.ext.tables.TableTheme tableTheme = new io.noties.markwon.ext.tables.TableTheme.Builder()
        .tableBorderColor(androidx.core.content.ContextCompat.getColor(context, R.color.table_border))
        .tableBorderWidth(Math.max(1, Math.round(1f * density)))      // 1dp
        .tableCellPadding(Math.round(8f * density))                  // 12dp
        .tableHeaderRowBackgroundColor(NO_BACKGROUND)
        .tableOddRowBackgroundColor(NO_BACKGROUND)
        .tableEvenRowBackgroundColor(NO_BACKGROUND)
        .build();


    // 2. Calculate the spacing in pixels (12dp for list items, 16dp for blocks)
    final int listSpacingPx = (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 12f, context.getResources().getDisplayMetrics());
    
    final int blockSpacingPx = (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 16f, context.getResources().getDisplayMetrics());

    // 3. Build Markwon
    this.markwon = Markwon.builder(context)
            .usePlugin(HtmlPlugin.create())
            .usePlugin(io.noties.markwon.ext.tables.TablePlugin.create(tableTheme))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(new AbstractMarkwonPlugin() {
                @Override
                public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                    // Adds margin before block elements (lists, blockquotes)
                    builder.blockMargin(blockSpacingPx); 
                }

                @Override
                public void configureSpansFactory(@NonNull MarkwonSpansFactory.Builder builder) {
                    // FIX: Use addFactory to APPEND to the default bullet spans
                    builder.addFactory(ListItem.class, (configuration, props) -> {
                        // Return an array containing BOTH the default spans and our spacing span
                        return new Object[]{ LastLineSpacingSpan.create(listSpacingPx) };
                    });
                }
            })
            .build();
       }
    
       // Add this to ChatAdapter.java so we can access it in the background!
    public io.noties.markwon.Markwon getMarkwon() {
        return markwon;
    }

    
       public void setMessages(List<ChatMessageEntity> newMessages, List<Long> messagesToAnimateTimestamps) {
    if (newMessages == null) return;

    int oldSize = this.messages.size();
    int newSize = newMessages.size();

    this.messages.clear();
    this.messages.addAll(newMessages);

    this.messagesToAnimateTimestamps = (messagesToAnimateTimestamps != null)
            ? new ArrayList<>(messagesToAnimateTimestamps) : null;

    if (oldSize == 0) {
        notifyDataSetChanged();
    } else if (newSize == oldSize + 1) {
       // notifyItemChanged(oldSize - 1, "REGEN_VISIBILITY"); // old last loses its button
        notifyItemInserted(newSize - 1);
    } else if (newSize == oldSize) {
        notifyItemChanged(newSize - 1);
    } else if (newSize > oldSize) {
        notifyItemChanged(oldSize - 1, "REGEN_VISIBILITY");
        notifyItemRangeInserted(oldSize, newSize - oldSize);
    } else {
        notifyDataSetChanged();
    }
  }
    
    @Override
    public int getItemViewType(int position) {
        // 1. Safety check: Always guard against an empty list
        if (messages == null || position >= messages.size()) {
            return ChatMessage.TYPE_AI; // Return a default fallback
        }
        
        // 2. Get the specific message
        ChatMessageEntity message = messages.get(position);
        
        // 3. Simply return the type stored in the entity
        // Since you injected the error card into the list as a 
        // ChatMessageEntity with type 99, this will return TYPE_ERROR.
        return message.getType(); 
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatMessage.TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.chat_item_user, parent, false);
            return new UserImageViewHolder(view); 
        } else if (viewType == ChatMessage.TYPE_AI) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.chat_item_ai, parent, false);
            return new AIViewHolder(view);
        } else if (viewType == ChatMessage.TYPE_ERROR) { // Our new ID 99
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_error, parent, false);
            return new ErrorViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.chat_item_typing, parent, false);
            return new TypingViewHolder(view);
        }
    }

    public List<ChatMessageEntity> getCurrentList() {
        return messages;
    }
   
       //  INSTANT UI PRUNING FOR EDIT MESSAGE
    public void removeMessagesFromTimestamp(long cutoffTimestamp) {
        if (messages == null || messages.isEmpty()) return;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessageEntity msg = messages.get(i);
            if (msg != null && msg.getTimestamp() >= cutoffTimestamp) {
                messages.remove(i);
            }
        }
        notifyDataSetChanged();
    }

    
       //  DADDY'S SCROLL FIX: Updates the data without freezing the screen!
    public void silentUpdate(List<ChatMessageEntity> newMessages) {
        if (newMessages == null) return;
        this.messages.clear();
        this.messages.addAll(newMessages);
        // CRITICAL: We intentionally DO NOT call notifyDataSetChanged() here!
        // This allows the user to scroll up and down freely with zero lag.
    }

    
    public static class UserImageViewHolder extends UserViewHolder {
        ImageView userImage;
        UserImageViewHolder(View itemView) {
            super(itemView);
            userImage = itemView.findViewById(R.id.userImage);
        }
    }
        
    public interface ChatActionListener {
        void onCopy(ChatMessageEntity message);          
        void onRegenerate(ChatMessageEntity message);
        void onShare(ChatMessageEntity message);         
        void onPlayTts(ChatMessageEntity message);
        void onStopTts();
        void onAnimationFinished();
        void onEditUserMessage(ChatMessageEntity message);
    }
       
  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holderRaw, int position) {
    if (messages == null || position >= messages.size()) return;
    ChatMessageEntity message = messages.get(position);
    if (message == null) return;

    if (holderRaw instanceof ErrorViewHolder) {
        ErrorViewHolder errorHolder = (ErrorViewHolder) holderRaw;
        
        String currentError = message.getMessage();
        if (currentError != null) {
            if ("You stopped this response".equals(currentError)) {
                if (errorHolder.errorTitleView != null) errorHolder.errorTitleView.setText("Generation Stopped");
                if (errorHolder.errorTextView != null) errorHolder.errorTextView.setText(currentError);
            } else {
                if (errorHolder.errorTitleView != null) errorHolder.errorTitleView.setText("Connection Issue");
                if (errorHolder.errorTextView != null) errorHolder.errorTextView.setText(currentError);
            }
        }
        
        if (errorHolder.retryButton != null) {
            errorHolder.retryButton.setOnClickListener(v -> {
                PinkSprinkleEffect.trigger(v);
                android.view.animation.Animation shakeUp = android.view.animation.AnimationUtils.loadAnimation(
                    v.getContext(), 
                    R.anim.retry_shake_up
                );
                errorHolder.itemView.startAnimation(shakeUp);
                if (retryCallback != null) {
                    retryCallback.run();
                }
            });
            
            errorHolder.retryButton.setOnTouchListener((view, motionEvent) -> {
                switch (motionEvent.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        view.setScaleX(0.95f);
                        view.setScaleY(0.95f);
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        view.setScaleX(1.0f);
                        view.setScaleY(1.0f);
                        break;
                }
                return false;
            });
        }
        return;
    }

            if (holderRaw instanceof UserImageViewHolder) {
        UserImageViewHolder userImageHolder = (UserImageViewHolder) holderRaw;
        Context context = userImageHolder.itemView.getContext(); 
    
        String savedImagePath = message.getImageUri();

        if (savedImagePath != null && !savedImagePath.isEmpty()) {
            userImageHolder.userImage.setVisibility(View.VISIBLE);
            
            Glide.with(context)
                    .load(savedImagePath)
                    .placeholder(R.drawable.loading_placeholder)
                    .error(R.drawable.image_load_error)
                    .into(userImageHolder.userImage);

            userImageHolder.userImage.setOnClickListener(v -> {
                if (context instanceof MainActivity) {
                   Uri safeFileUri = Uri.parse(savedImagePath);
                    ((MainActivity) context).showFullScreenImage(safeFileUri);
                }
            });
        } else {
            userImageHolder.userImage.setVisibility(View.GONE);
            userImageHolder.userImage.setOnClickListener(null);
        }

        if (message.getMessage() != null && !message.getMessage().isEmpty()) {
             userImageHolder.userMessage.setText(message.getMessage());
             userImageHolder.userMessage.setVisibility(View.VISIBLE);
        } else {
             userImageHolder.userMessage.setVisibility(View.GONE);
        }

       
            
                                       //  COMPACT & ELEGANT FLOATING ACTION MENU (Hugs content tightly)
      View.OnLongClickListener professionalMenuListener = anchorView -> {
       anchorView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

        android.content.SharedPreferences modelPrefs = context.getSharedPreferences("model_prefs", Context.MODE_PRIVATE);
        String selectedModel = modelPrefs.getString("selected_model", "venice");
        boolean isLocalModel = "v3".equals(selectedModel) || "custom".equals(selectedModel) || message.isOfflineModel();

         android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
          int padH = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 16f, dm);
          int padV = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 11f, dm);
           int iconSize = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 18f, dm);
          int iconMargin = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 12f, dm);
         int popupWidth = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 172f, dm);

         android.widget.LinearLayout menuCard = new android.widget.LinearLayout(context);
         menuCard.setOrientation(android.widget.LinearLayout.VERTICAL);

          android.graphics.drawable.GradientDrawable cardBg = new android.graphics.drawable.GradientDrawable();
    
           //  Dynamic Popup Background
           cardBg.setColor(androidx.core.content.ContextCompat.getColor(context, R.color.dialog_bg)); 
    
           cardBg.setCornerRadius(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f, dm));
           cardBg.setStroke(
        (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 1f, dm),
        // Dynamic Stroke
        androidx.core.content.ContextCompat.getColor(context, R.color.divider_color)
        );
          menuCard.setBackground(cardBg);

          final android.widget.PopupWindow popupWindow = new android.widget.PopupWindow(
            menuCard,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        );
         popupWindow.setElevation(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 10f, dm));
         popupWindow.setOutsideTouchable(true);

           android.util.TypedValue rippleVal = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, rippleVal, true);

        // 1. "Edit Message" Row (Online models only)
      if (!isLocalModel) {
        android.widget.LinearLayout editRow = new android.widget.LinearLayout(context);
        editRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        editRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        editRow.setPadding(padH, padV, padH, padV);
        editRow.setBackgroundResource(rippleVal.resourceId);

        ImageView editIcon = new ImageView(context);
        editIcon.setImageResource(R.drawable.ic_edit);
        // Dynamic Icon Tint
        editIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary));
        android.widget.LinearLayout.LayoutParams editIconParams = new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
        editIconParams.setMarginEnd(iconMargin);
        editIcon.setLayoutParams(editIconParams);

        TextView editLabel = new TextView(context);
        editLabel.setText("Edit Message");
        editLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f);
        // Dynamic Text Color
        editLabel.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary));
        editLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        editLabel.setSingleLine(true);

        editRow.addView(editIcon);
        editRow.addView(editLabel);
        editRow.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (chatActionListener != null) {
                chatActionListener.onEditUserMessage(message);
            }
        });
        menuCard.addView(editRow);

        View divider = new View(context);
        divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        // Dynamic Divider Color
        divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.divider_color));
        menuCard.addView(divider);
      }

           // 2. "Copy Text" Row
            android.widget.LinearLayout copyRow = new android.widget.LinearLayout(context);
           copyRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            copyRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            copyRow.setPadding(padH, padV, padH, padV);
             copyRow.setBackgroundResource(rippleVal.resourceId);

          ImageView copyIcon = new ImageView(context);
           int copyResId = context.getResources().getIdentifier("ic_copy", "drawable", context.getPackageName());
             if (copyResId == 0) copyResId = android.R.drawable.ic_menu_agenda;
             copyIcon.setImageResource(copyResId);
             // Dynamic Icon Tint
              copyIcon.setColorFilter(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary));
              android.widget.LinearLayout.LayoutParams copyIconParams = new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
              copyIconParams.setMarginEnd(iconMargin);
               copyIcon.setLayoutParams(copyIconParams);

            TextView copyLabel = new TextView(context);
            copyLabel.setText("Copy Text");
             copyLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f);
         //  Dynamic Text Color
           copyLabel.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary));
            copyLabel.setTypeface(null, android.graphics.Typeface.BOLD);
           copyLabel.setSingleLine(true);

            copyRow.addView(copyIcon);
             copyRow.addView(copyLabel);
         copyRow.setOnClickListener(v -> {
        popupWindow.dismiss();
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && message.getMessage() != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("User Message", message.getMessage()));
            android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
        }
        });
        menuCard.addView(copyRow);

    // Align the right edge of the compact popup with the right edge of the user bubble
        int xOffset = anchorView.getWidth() - popupWidth;
        int yOffset = (int) android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 6f, dm);
        popupWindow.showAsDropDown(anchorView, xOffset, yOffset);
        return true;
     };

      userImageHolder.userMessage.setOnLongClickListener(professionalMenuListener);
      userImageHolder.itemView.setOnLongClickListener(professionalMenuListener);

    }  else if (holderRaw instanceof AIViewHolder) {  
    AIViewHolder aiHolder = (AIViewHolder) holderRaw;  
    aiHolder.boundMessage = message;  
    Context context = aiHolder.itemView.getContext();  

    Runnable existing = runningAnimations.remove(message);  
    if (existing != null) {  
        handler.removeCallbacks(existing);  
    }  

    boolean alreadyAnimated = animatedTimestamps.contains(message.getTimestamp());  
    boolean isOffline = message.isOfflineModel();  
      
    boolean shouldAnimate = (messagesToAnimateTimestamps != null &&  
                             messagesToAnimateTimestamps.contains(message.getTimestamp()) &&  
                             !alreadyAnimated &&  
                             !isOffline);  

    //   Raw text only. No healMarkdown here!
    final String finalContent = message.getMessage();  
      
    if (shouldAnimate) {  
        aiHolder.aiMessageContainer.removeAllViews();  
        TextView typingTextView = (TextView) android.view.LayoutInflater.from(context)  
                .inflate(R.layout.item_chat_text_block, aiHolder.aiMessageContainer, false);  
        aiHolder.aiMessageContainer.addView(typingTextView);  

        if (aiHolder.aiActionsLayout != null) {  
            aiHolder.aiActionsLayout.setVisibility(View.GONE);  
        }  
        startTypingAnimation(aiHolder, message, typingTextView);   
        animatedTimestamps.add(message.getTimestamp());  
    } else {  
        renderBlocks(aiHolder.aiMessageContainer, finalContent);  
    }  

      if (aiHolder.aiActionsLayout != null) {  
        boolean isStreaming = message.isStreaming();  
        if (message.getMessage() == null || message.getMessage().trim().isEmpty() || isStreaming || shouldAnimate) {  
            aiHolder.aiActionsLayout.setVisibility(View.GONE);  
        } else {  
            aiHolder.aiActionsLayout.setVisibility(View.VISIBLE);  
        }  
    
      }

       final String fullCopyText = buildCleanCopyText(message.getMessage() == null ? "" : message.getMessage());

           if (aiHolder.btnCopy != null)
             { aiHolder.btnCopy.setOnClickListener(v -> {
               android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
               if (clipboard != null) {
                   clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI Message", fullCopyText));
                    android.widget.Toast.makeText(context, "Full response copied", android.widget.Toast.LENGTH_SHORT).show();
                }
           });
         }

            if (aiHolder.btnShare != null) {
               aiHolder.btnShare.setOnClickListener(v -> {
               android.content.Intent sendIntent = new android.content.Intent();
               sendIntent.setAction(android.content.Intent.ACTION_SEND);
               sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, fullCopyText);
               sendIntent.setType("text/plain");
               context.startActivity(android.content.Intent.createChooser(sendIntent, "Share response via"));
            });
          }

        int currentPos = holderRaw.getAbsoluteAdapterPosition();
if (currentPos == RecyclerView.NO_POSITION) currentPos = position;

boolean isLast = (currentPos == messages.size() - 1);

if (aiHolder.btnRegenerate != null) {
    boolean show = !isOffline && isLast && !message.isStreaming();
    aiHolder.btnRegenerate.setVisibility(show ? View.VISIBLE : View.GONE);

    aiHolder.btnRegenerate.setOnClickListener(v -> {
        int pos = aiHolder.getAbsoluteAdapterPosition();
        if (pos == RecyclerView.NO_POSITION) return;

        // Re-check live, not from the bind-time value
        boolean stillLast = (pos == messages.size() - 1);
        if (!isOffline && stillLast && !message.isStreaming()
                && chatActionListener != null) {
            chatActionListener.onRegenerate(message);
        }
    });
}
            
            

        if (aiHolder.btnTtsPlayPause != null) {
            boolean isThisMessagePlaying = (currentlyPlayingMessageId != null && currentlyPlayingMessageId.equals(message.getTimestamp()));
            
            if (isThisMessagePlaying) {
                aiHolder.btnTtsPlayPause.setImageResource(R.drawable.ic_pause);
            } else {
                aiHolder.btnTtsPlayPause.setImageResource(R.drawable.ic_play);
            }

            aiHolder.btnTtsPlayPause.setOnClickListener(v -> {
                if (chatActionListener != null) {
                    if (currentlyPlayingMessageId != null && currentlyPlayingMessageId.equals(message.getTimestamp())) {
                        chatActionListener.onStopTts();
                        // 🛑 REMOVED notifyDataSetChanged(). The Observer handles it safely now!
                    } else {
                        chatActionListener.onPlayTts(message);
                        // 🛑 REMOVED notifyDataSetChanged().
                    }
                }
            });
        }


    } else if (holderRaw instanceof TypingViewHolder) {
        TypingViewHolder typingHolder = (TypingViewHolder) holderRaw;
        startDotAnimation(typingHolder);
    }
  }

          // High-Performance Partial UI Binding
   @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holderRaw, int position, @NonNull List<Object> payloads) {

    // NEW: cheap rebind, only refreshes the Regenerate button
    if (!payloads.isEmpty() && "REGEN_VISIBILITY".equals(payloads.get(0))) {
        if (holderRaw instanceof AIViewHolder && position < messages.size()) {
            AIViewHolder aiHolder = (AIViewHolder) holderRaw;
            ChatMessageEntity message = messages.get(position);
            boolean isLast = (position == messages.size() - 1);
            boolean show = !message.isOfflineModel() && isLast && !message.isStreaming();
            if (aiHolder.btnRegenerate != null) {
                aiHolder.btnRegenerate.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        }
        return; // skip the heavy bind
    }

    if (!payloads.isEmpty() && "STREAMING_UPDATE".equals(payloads.get(0))) {
        if (holderRaw instanceof AIViewHolder) {
            AIViewHolder aiHolder = (AIViewHolder) holderRaw;
            if (aiHolder.aiActionsLayout != null) {
                aiHolder.aiActionsLayout.setVisibility(View.GONE);
            }
            ChatMessageEntity message = messages.get(position);
            String content = message.getMessage();
            if (content != null) {
                smartStreamRender(aiHolder.aiMessageContainer, content);
            }
        }
        return;
    }

    super.onBindViewHolder(holderRaw, position, payloads);
  }

    // Add this inside ChatAdapter.java
    public void applyMarkdown(TextView textView, String text) {
        markwon.setMarkdown(textView, text);
    }

   // OPTIMIZED STREAMING UPDATER
  public void updateLastMessage(String newText) {
    if (messages == null || messages.isEmpty()) return;
    
    int lastIndex = messages.size() - 1;
    ChatMessageEntity lastMessage = messages.get(lastIndex);
    
    // Check if anything actually changed before forcing a layout pass
    if (newText != null && newText.equals(lastMessage.getMessage())) {
        return; 
    }
    
    lastMessage.setMessage(newText);
    
    // Notify with payload so it updates the backing data model smoothly
    notifyItemChanged(lastIndex, "STREAMING_UPDATE");
  }

   public String buildCleanCopyText(String raw) {
    StringBuilder sb = new StringBuilder();
    for (String[] u : buildUnits(raw, false)) {
        sb.append(u[1]).append("\n\n");
    }
    return sb.toString().trim();
   }
  
    public static class ExtraLineHeightSpan implements android.text.style.LineHeightSpan {
    private final int extra;
    public ExtraLineHeightSpan(int extra) { this.extra = extra; }
    @Override
    public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v,
                             android.graphics.Paint.FontMetricsInt fm) {
        fm.descent += extra;
        fm.bottom += extra;
    }
  }
    
    
    
    public void setCurrentlyPlayingMessageId(Long id) {
        this.currentlyPlayingMessageId = id;
    }

    public void setChatActionListener(ChatActionListener listener) {
        this.chatActionListener = listener;
    }
    
    public void clearAnimationState() {
        this.animatedTimestamps.clear();
    }
 
   // DADDY'S FIX: Update the backing list WITHOUT notifying RecyclerView
    public void updateDataSilently(List<ChatMessageEntity> newMessages) {
         this.messages = new ArrayList<>(newMessages);
    // NO notifyDataSetChanged()! We call notifyItemInserted manually from Activity.
    }
    
    public void setErrorState(boolean hasError) {
        if (this.hasError != hasError) {
            this.hasError = hasError;
            notifyDataSetChanged();
        }
    }

    // New method to listen for retry clicks
    public void setOnRetryCallback(Runnable retryCallback) {
        this.retryCallback = retryCallback;
    }
  
    private void startDotAnimation(TypingViewHolder holder) {
        animateDot(holder.dot1, 0);
        animateDot(holder.dot2, 200);
        animateDot(holder.dot3, 400);
    }

    private void animateDot(View dot, int delay) {
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

      public static class Segment {
      public static final int TEXT = 0, CODE = 1;
      public final int type; public final String language; public final String body;
      Segment(int t, String l, String b) { type = t; language = l; body = b; }
    }

   public static List<Segment> parseSegments(String text) {
    List<Segment> out = new ArrayList<>();
    if (text == null || text.isEmpty()) return out;
    StringBuilder textBuf = new StringBuilder(), codeBuf = null;
    String lang = "";
    for (String line : text.split("\n", -1)) {
        String t = line.trim();
        boolean fence = t.startsWith("```") || t.startsWith("~~~");
        if (codeBuf == null) {
            if (fence) {
                if (textBuf.toString().trim().length() > 0)
                    out.add(new Segment(Segment.TEXT, null, textBuf.toString().trim()));
                textBuf.setLength(0);
                codeBuf = new StringBuilder();
                lang = t.substring(3).trim();
            } else textBuf.append(line).append("\n");
        } else {
            if (fence) { out.add(new Segment(Segment.CODE, lang, codeBuf.toString())); codeBuf = null; lang = ""; }
            else codeBuf.append(line).append("\n");
        }
    }
    if (codeBuf != null) out.add(new Segment(Segment.CODE, lang, codeBuf.toString()));
    if (textBuf.toString().trim().length() > 0) out.add(new Segment(Segment.TEXT, null, textBuf.toString().trim()));
    return out;
   }

   public List<Segment> buildSegments(String raw, boolean streaming) {
    List<Segment> out = new ArrayList<>();
    for (Segment s : parseSegments(raw)) {
        if (s.type == Segment.TEXT) {
            String healed = healMarkdown(s.body, streaming); 
            if (!healed.trim().isEmpty()) out.add(new Segment(Segment.TEXT, null, healed));
        } else out.add(s);
    }
    return out;
   }

   private boolean looksLikeTable(String block) {
    String[] ls = block.split("\n", 3);
    if (ls.length < 2) return false;
    String sep = ls[1].trim();
    return sep.matches("^[\\|\\s:\\-]+$") && sep.contains("-");
   }

   public List<String[]> buildUnits(String raw, boolean streaming) {
   raw = balanceItalics(raw);
    List<String[]> units = new ArrayList<>();
    StringBuilder textBuffer = new StringBuilder();

    for (Segment s : buildSegments(raw, streaming)) {
        if (s.type == Segment.CODE) {
            // Flush any pending text before adding code
            if (textBuffer.length() > 0) {
                units.add(new String[]{"TEXT", textBuffer.toString().trim(), null});
                textBuffer.setLength(0);
            }
            units.add(new String[]{"CODE", s.body, s.language});
        } else {
            // Split by paragraphs, but check if it's a table
            String[] paragraphs = s.body.split("\n\n");
            for (String b : paragraphs) {
                if (!b.trim().isEmpty()) {
                    if (looksLikeTable(b)) {
                        // Flush pending text before table
                        if (textBuffer.length() > 0) {
                            units.add(new String[]{"TEXT", textBuffer.toString().trim(), null});
                            textBuffer.setLength(0);
                        }
                        units.add(new String[]{"TABLE", b, null});
                    } else {
                        
                        if (textBuffer.length() > 0) textBuffer.append("\n\n");
                        textBuffer.append(b);
                    }
                }
            }
        }
    }
    
    // Flush any remaining text
    if (textBuffer.length() > 0) {
        units.add(new String[]{"TEXT", textBuffer.toString().trim(), null});
    }
    
    return units;
  }

   private void setSelectable(TextView tv, boolean selectable) {
    if (tv == null) return;
    tv.setTextIsSelectable(selectable);
    tv.setFocusable(selectable);
    tv.setFocusableInTouchMode(selectable);
    if (!selectable) {
        tv.setMovementMethod(null);
        tv.setLongClickable(false);
    }
   }

    
   private boolean bindTableUnit(View child, String body, boolean selectable) {
    if (!(child instanceof android.widget.HorizontalScrollView)) return false;

    java.util.List<java.util.List<String>> rows = parseMarkdownTable(body);
    if (rows.isEmpty() || rows.get(0).isEmpty()) return false;   // fall back to the old renderer

    android.widget.HorizontalScrollView scroll = (android.widget.HorizontalScrollView) child;
    Context ctx = scroll.getContext();
    float d = ctx.getResources().getDisplayMetrics().density;

    // Keep the original TextView (hidden) so any code that reads its id/tag still works.
    TextView holder = (TextView) scroll.getTag(R.id.tableInnerTextView);
    if (holder == null) holder = scroll.findViewById(R.id.tableInnerTextView);
    if (holder != null) {
        if (holder.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) holder.getParent()).removeView(holder);
        }
        holder.setTag(body);
        holder.setVisibility(View.GONE);
        scroll.setTag(R.id.tableInnerTextView, holder);
    }
    scroll.removeAllViews();

    LinearLayout wrapper = new LinearLayout(ctx);
    wrapper.setOrientation(LinearLayout.VERTICAL);
    if (holder != null) {
        wrapper.addView(holder, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // The table draws its own grid lines, so cells need no borders at all.
    android.widget.TableLayout table = new GridTableLayout(
            ctx,
            androidx.core.content.ContextCompat.getColor(ctx, R.color.table_border),
            Math.max(1f, d));                                   // 1dp line

    int padH = Math.round(12 * d);
    int padV = Math.round(10 * d);
    int maxCellPx = Math.round(TABLE_MAX_CELL_DP * d);
    int minCellPx = Math.round(TABLE_MIN_CELL_DP * d);

    for (int r = 0; r < rows.size(); r++) {
        android.widget.TableRow tableRow = new android.widget.TableRow(ctx);
        for (String text : rows.get(r)) {
            TextView cell = new TextView(ctx);
            cell.setTextAppearance(R.style.SafeTextAppearance);
            cell.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f);
            cell.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary));
            cell.setLetterSpacing(0.02f);
            cell.setLineSpacing(0f, 1.2f);
            cell.setIncludeFontPadding(false);
            cell.setPadding(padH, padV, padH, padV);
            cell.setMaxWidth(maxCellPx);
            cell.setMinWidth(minCellPx);
            cell.setGravity(android.view.Gravity.CENTER_VERTICAL);   // text sits in the middle of the row

            markwon.setMarkdown(cell, text);
            if (r == 0) cell.setTypeface(null, android.graphics.Typeface.BOLD);   // header row
            setSelectable(cell, selectable);

            // MATCH_PARENT height = the cell fills the whole row, however tall the row is
            tableRow.addView(cell, new android.widget.TableRow.LayoutParams(
                    android.widget.TableRow.LayoutParams.WRAP_CONTENT,
                    android.widget.TableRow.LayoutParams.MATCH_PARENT));
        }
        table.addView(tableRow, new android.widget.TableLayout.LayoutParams(
                android.widget.TableLayout.LayoutParams.WRAP_CONTENT,
                android.widget.TableLayout.LayoutParams.WRAP_CONTENT));
    }

    wrapper.addView(table, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    scroll.addView(wrapper, new android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
    return true;
  }

      
    
    
   private static class GridTableLayout extends android.widget.TableLayout {
    
     private final android.graphics.Paint linePaint = new android.graphics.Paint();
    GridTableLayout(Context context, int lineColor, float lineWidthPx) {
        super(context);
        linePaint.setColor(lineColor);
        linePaint.setStrokeWidth(lineWidthPx);
        linePaint.setStyle(android.graphics.Paint.Style.STROKE);
    }

    @Override
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        super.dispatchDraw(canvas);

        final float half = linePaint.getStrokeWidth() / 2f;
        final float w = getWidth();
        final float h = getHeight();
        final int rowCount = getChildCount();
        if (rowCount == 0) return;

        // outer border
        canvas.drawRect(half, half, w - half, h - half, linePaint);

        // a horizontal line under every row except the last (the outer border covers that one)
        for (int r = 0; r < rowCount - 1; r++) {
            float y = getChildAt(r).getBottom();
            canvas.drawLine(0, y, w, y, linePaint);
        }

        // vertical lines at the column boundaries (TableLayout aligns columns, so row 0 is enough)
        View first = getChildAt(0);
        if (first instanceof android.widget.TableRow) {
            android.widget.TableRow row = (android.widget.TableRow) first;
            for (int c = 0; c < row.getChildCount() - 1; c++) {
                float x = row.getLeft() + row.getChildAt(c).getRight();
                canvas.drawLine(x, 0, x, h, linePaint);
            }
        }
    }
 }

/** Turns a markdown table into rows of cell strings (header is row 0, separator row dropped). */
  private java.util.List<java.util.List<String>> parseMarkdownTable(String body) {
    java.util.List<java.util.List<String>> rows = new java.util.ArrayList<>();
    int maxCols = 0;

    for (String line : body.split("\n")) {
        String row = line.trim();
        if (row.isEmpty() || !row.contains("|")) continue;
        if (row.matches("^\\|?[\\s:|-]+\\|?$")) continue;          // the |---|---| line

        row = row.replace("\\|", "\u0001");                         // protect escaped pipes
        if (row.startsWith("|")) row = row.substring(1);
        if (row.endsWith("|")) row = row.substring(0, row.length() - 1);

        java.util.List<String> cells = new java.util.ArrayList<>();
        for (String part : row.split("\\|", -1)) {
            cells.add(part.replace("\u0001", "|").trim());
        }
        maxCols = Math.max(maxCols, cells.size());
        rows.add(cells);
    }
    for (java.util.List<String> r : rows) {
        while (r.size() < maxCols) r.add("");
    }
    return rows;
  }

/**
 * Fixes stray "*" caused by italics that open in one paragraph and close in a later one.
 * Markdown can't italicise across a blank line, so we close it at the end of each paragraph
 * and reopen it at the start of the next. Code blocks and tables are left alone.
 */
   private static String balanceItalics(String text) {
    if (text == null || text.indexOf('*') < 0) return text;

    String[] lines = text.split("\n", -1);
    StringBuilder out = new StringBuilder();
    java.util.List<String> para = new java.util.ArrayList<>();
    boolean[] italicOpen = {false};
    boolean inFence = false;

    for (String line : lines) {
        if (line.trim().startsWith("```")) {
            flushParagraph(para, out, italicOpen);
            inFence = !inFence;
            out.append(line).append("\n");
        } else if (inFence) {
            out.append(line).append("\n");
        } else if (line.trim().isEmpty()) {
            flushParagraph(para, out, italicOpen);
            out.append("\n");
        } else {
            para.add(line);
        }
    }
    flushParagraph(para, out, italicOpen);

    String result = out.toString();
    if (result.endsWith("\n") && !text.endsWith("\n")) {
        result = result.substring(0, result.length() - 1);
    }
    return result;
  }

   
  private static void flushParagraph(java.util.List<String> para, StringBuilder out, boolean[] italicOpen) {
    if (para.isEmpty()) return;
    String p = android.text.TextUtils.join("\n", para);
    para.clear();

    boolean special = p.trim().startsWith("|")
            || p.matches("(?s)^\\s*(#{1,6}\\s|>|[-+]\\s|\\d+[.)]\\s).*");   // table, heading, quote, list
    if (!special) {
        int markers = countItalicMarkers(p);
        boolean odd = (markers % 2 == 1);
        boolean startsStar = p.startsWith("*");
        boolean endsStar = p.endsWith("*");

        if (italicOpen[0]) {
            if (odd) {
                // this paragraph holds the closing marker: reopen at the start so it can close
                if (!startsStar) p = "*" + p;
                italicOpen[0] = false;
            } else if (!startsStar && !endsStar) {
                // paragraph sits fully inside the italic span
                p = "*" + p + "*";
            }
        } else if (odd) {
            if (endsStar && !p.endsWith("**")) {
                // lone "*" at the end with no opener anywhere: it's a stray, drop it
                p = p.substring(0, p.length() - 1);
            } else if (!endsStar) {
                // italics opened here and never closed: close now, reopen in the next paragraph
                p = p + "*";
                italicOpen[0] = true;
            }
        }
    }
    out.append(p).append("\n");
  }

   
   private static int countItalicMarkers(String p) {
    String s = p.replaceAll("`[^`]*`", "")                 // inline code
                .replaceAll("(?m)^\\s*[*+-]\\s+", "")      // list bullets
                .replace("\\*", "");                       // escaped asterisks

    int markers = 0;
    int run = 0;
    for (int i = 0; i <= s.length(); i++) {
        if (i < s.length() && s.charAt(i) == '*') {
            run++;
        } else {
            if (run % 2 == 1) markers++;
            run = 0;
        }
    }
    return markers;
  }
    
    
    
    
    
                 private void startTypingAnimation(AIViewHolder holder, ChatMessageEntity message, final TextView targetTextView) {
    Runnable prev = runningAnimations.remove(message);
    if (prev != null) handler.removeCallbacks(prev);

    final WeakReference<AIViewHolder> holderRef = new WeakReference<>(holder);

    String rawText = message.getMessage() != null ? message.getMessage() : "";
    //  Fix stray "*" (italics that open in one paragraph and close in a later one), then split into blocks
    final List<String[]> units = buildUnits(balanceItalics(rawText), true);

    targetTextView.setVisibility(View.GONE);

    AIViewHolder currentHolder = holderRef.get();
    if (currentHolder == null || currentHolder.aiMessageContainer == null) return;

    currentHolder.aiMessageContainer.removeAllViews();
    currentHolder.aiMessageContainer.setVisibility(View.VISIBLE);

    final int CHARS_PER_CHUNK = 15;

    Runnable runnable = new Runnable() {
        int unitIndex = 0;
        int charIndex = 0;
        View currentChild = null;
        TextView currentTv = null;

        @Override
        public void run() {
            AIViewHolder holder = holderRef.get();
            if (holder == null || holder.boundMessage != message) {
                runningAnimations.remove(message);
                return;
            }

            if (unitIndex >= units.size()) {
                runningAnimations.remove(message);
                // 🏆 Swap per-block typing views for the merged, fully-selectable final layout

                if (chatActionListener != null) chatActionListener.onAnimationFinished();

                if (holder.aiActionsLayout != null) {
                    holder.aiActionsLayout.setAlpha(0f);
                    holder.aiActionsLayout.setVisibility(View.VISIBLE);
                    holder.aiActionsLayout.animate().alpha(1f).setDuration(300).start();

                    boolean isOffline = message.isOfflineModel();
                    int lastAiPosition = -1;
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        if (messages.get(i).getType() == ChatMessage.TYPE_AI) {
                            lastAiPosition = i; break;
                        }
                    }
                    if (holder.btnRegenerate != null) {
                        holder.btnRegenerate.setVisibility(!isOffline && messages.indexOf(message) == lastAiPosition ? View.VISIBLE : View.GONE);
                    }
                }
                return;
            }

            String[] u = units.get(unitIndex);
            String currentBlockText = u[1];
            boolean isCodeOrTable = u[0].equals("CODE") || u[0].equals("TABLE");

            if (currentChild == null) {
                currentChild = inflateUnit(holder.aiMessageContainer, u[0], u[2]);
                holder.aiMessageContainer.addView(currentChild);
                currentTv = u[0].equals("CODE") ? currentChild.findViewById(R.id.codeTextView)
                          : u[0].equals("TABLE") ? currentChild.findViewById(R.id.tableInnerTextView)
                          : (TextView) currentChild;
            }

            // THE CODE & TABLE FAST-FORWARD
            if (isCodeOrTable) {
                bindUnit(currentChild, u[0], currentBlockText, u[2], false);
                unitIndex++; charIndex = 0; currentChild = null; currentTv = null;
                handler.post(this);
                return;
            }

            // STANDARD TEXT ANIMATION
            charIndex += CHARS_PER_CHUNK;
            if (charIndex > currentBlockText.length()) charIndex = currentBlockText.length();

            markwon.setMarkdown(currentTv, currentBlockText.substring(0, charIndex));

            if (charIndex < currentBlockText.length()) {
                handler.postDelayed(this, CHUNK_DELAY_MS);
            } else {
                setSelectable(currentTv, true);
                unitIndex++; charIndex = 0; currentChild = null; currentTv = null;
                handler.postDelayed(this, CHUNK_DELAY_MS);
            }
        }
    };

    runningAnimations.put(message, runnable);
    handler.post(runnable);
  }


    @Override
    public int getItemCount() {
        // CRITICAL: Ensure messages is not null
        return (messages != null ? messages.size() : 0) + (hasError ? 1 : 0);
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView userMessage;
        UserViewHolder(View itemView) {
            super(itemView);
            userMessage = itemView.findViewById(R.id.userMessage);
        }
    }
    
     static class AIViewHolder extends RecyclerView.ViewHolder {
        //  Replaced TextView with LinearLayout
        public LinearLayout aiMessageContainer; 
        public LinearLayout aiActionsLayout;
        public ImageView btnRegenerate;
        public ImageButton btnCopy, btnShare, btnTtsPlayPause;
        public ChatMessageEntity boundMessage;

        public AIViewHolder(View itemView) {
            super(itemView);
            //  Map to the new container
            aiMessageContainer = itemView.findViewById(R.id.aiMessageContainer); 
            aiActionsLayout = itemView.findViewById(R.id.aiActionsLayout);
            btnRegenerate = itemView.findViewById(R.id.btnRegenerate);
            btnCopy = itemView.findViewById(R.id.btnCopy);
            btnShare = itemView.findViewById(R.id.btnShare);
            btnTtsPlayPause = itemView.findViewById(R.id.btnTtsPlayPause);
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

    // Fully Updated ErrorViewHolder
    static class ErrorViewHolder extends RecyclerView.ViewHolder {
        TextView errorTitleView; 
        TextView errorTextView;  
        Button retryButton;
        
        ErrorViewHolder(View itemView) {
            super(itemView);
            
            // 1. Bind your new text views to the XML IDs we just fixed
            errorTitleView = itemView.findViewById(R.id.errorTitle); 
            errorTextView = itemView.findViewById(R.id.errorText);
            
            // 2. Keep your exact existing button ID so your retry logic stays perfectly intact
            retryButton = itemView.findViewById(R.id.adapterRetryButton); 
        }
    }
 

          
            // THE STREAMING ENGINE: Updates UI without destroying the layout height!
    

     public String healMarkdown(String text, boolean isStreaming) {
    if (text == null || text.isEmpty()) return "";
    
    //  FIX: Ensure bullets are preceded by double newlines for markdown parsing
    text = text.replaceAll("(?m)^([ \\t]*)([*\\-])([ \\t]+)", "\n\n$1$2$3");
    text = text.replaceAll("\\n{3,}", "\n\n");
    
    // ... (keep your existing table fixing logic below) ...
    String[] lines = text.split("\n", -1);
    StringBuilder sb = new StringBuilder();
    boolean inTable = false;
    boolean hasSeparator = false;
    
    for (int i = 0; i < lines.length; i++) {
        String line = lines[i].trim();
        boolean isTableRow = line.startsWith("|") || (line.length() > 2 && line.contains("|") && !line.startsWith("#") && !line.startsWith("*"));
        
        if (isTableRow) {
            if (!inTable) {
                inTable = true;
                hasSeparator = false;
                sb.append("\n\n"); // Force double newline BEFORE table
            }
            
            sb.append(line).append("\n");
            
            if (line.replaceAll("[\\s|:-]", "").isEmpty()) {
                hasSeparator = true;
            }
            
            if (inTable && !hasSeparator && i + 1 < lines.length) {
                int nextIdx = i + 1;
                while (nextIdx < lines.length && lines[nextIdx].trim().isEmpty()) {
                    nextIdx++;
                }
                if (nextIdx < lines.length) {
                    String nextLine = lines[nextIdx].trim();
                    if (nextLine.contains("|") && !nextLine.replaceAll("[\\s|:-]", "").isEmpty()) {
                        int pipes = line.length() - line.replace("|", "").length();
                        StringBuilder sep = new StringBuilder();
                        sep.append("|");
                        for (int p = 0; p < Math.max(1, pipes); p++) {
                            sep.append("---|");
                        }
                        sb.append(sep.toString()).append("\n");
                        hasSeparator = true; 
                    }
                }
            }
            while (i + 1 < lines.length && lines[i+1].trim().isEmpty()) {
                i++;
            }
        } else {
            if (inTable) {
                sb.append("\n\n"); // Force double newline AFTER table
                inTable = false;
            }
            sb.append(lines[i]).append("\n"); 
        }
    }
    
    String result = sb.toString().trim();
    result = result.replaceAll("\\n{3,}", "\n\n");
    
    return result;
  }

    private int computeTableWidthPx(String body, TextView tv) {
    final float d = tv.getResources().getDisplayMetrics().density;
    final android.text.TextPaint paint = tv.getPaint();

    float widestCell = 0f;
    int columns = 1;

    for (String line : body.split("\n")) {
        String row = line.trim();
        if (!row.contains("|")) continue;
        if (row.matches("^\\|?[\\s:|-]+\\|?$")) continue;   // skip the |---|---| separator row

        if (row.startsWith("|")) row = row.substring(1);
        if (row.endsWith("|")) row = row.substring(0, row.length() - 1);

        String[] cells = row.split("\\|", -1);
        columns = Math.max(columns, cells.length);

        for (String cell : cells) {
            String plain = cell.trim().replaceAll("[*_`~]", "");   // drop markdown symbols
            widestCell = Math.max(widestCell, paint.measureText(plain));
        }
    }

    // text + 10% for bold/italic + cell padding (12dp x2) + border
    float perColumn = widestCell * 1.1f + 20f * d;
    perColumn = Math.max(100f * d, Math.min(perColumn, 300f * d));   // clamp 100dp..420dp

    int tableWidth = Math.round(perColumn * columns);
    int screenContent = tv.getResources().getDisplayMetrics().widthPixels - Math.round(32 * d);
    return Math.max(screenContent, tableWidth);
   }




         // THE PERMANENT SPACING FIX
    // THE INDESTRUCTIBLE BRUTE-FORCE SPACING
   private View inflateUnit(LinearLayout container, String kind, String lang) {
    int layout;
    if (kind.equals("CODE")) layout = R.layout.chat_item_code; // Your new UI
    else if (kind.equals("TABLE")) layout = R.layout.item_chat_table_block;
    else layout = R.layout.item_chat_text_block;

    View child = LayoutInflater.from(container.getContext()).inflate(layout, container, false);
    
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    p.bottomMargin = (int) android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP, 16f, container.getResources().getDisplayMetrics());
    child.setLayoutParams(p);
    child.setTag(kind); 

    if (kind.equals("CODE")) {
        TextView langView = child.findViewById(R.id.codeLanguageTextView);
       if (langView != null) langView.setText(lang == null || lang.isEmpty() ? "code" : lang.toLowerCase());
    } 
        
    return child;
  }

   private void bindUnit(View child, String kind, String body, String lang, boolean selectable) {
     
        if (kind.equals("CODE")) {
        TextView codeView = child.findViewById(R.id.codeTextView);
        String clean = body.endsWith("\n") ? body.substring(0, body.length() - 1) : body;
        codeView.setText(clean);
        codeView.setTag(clean);

        //  LONG-PRESS = select & copy the EXACT string you want.
        // Gated so live streaming doesn't cause cursor/scroll jumps.
        codeView.setTextIsSelectable(selectable);
        codeView.setFocusable(selectable);
        codeView.setFocusableInTouchMode(selectable);
        if (!selectable) codeView.setMovementMethod(null);

        View copyBtn = child.findViewById(R.id.copyButton);
        if (copyBtn != null) {
            copyBtn.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager)
                        v.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("code", clean));
                android.widget.Toast.makeText(v.getContext(), "Code copied", android.widget.Toast.LENGTH_SHORT).show();
            });
        }

    } else if (kind.equals("TABLE") && bindTableUnit(child, body, selectable)) {
        // Dynamic table: built from real views, each column sizes to its own content.
        // Nothing else to do here.

    } else {
        // Normal text blocks (and the fallback if a table could not be parsed)
        TextView tv = kind.equals("TABLE")
                ? child.findViewById(R.id.tableInnerTextView) : (TextView) child;
        tv.setTag(body);

        if (kind.equals("TABLE")) {
            tv.setMinimumWidth(computeTableWidthPx(body, tv));
        }

        markwon.setMarkdown(tv, body);
        float sp = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, 1f,
                tv.getResources().getDisplayMetrics());
        tv.setLineSpacing(sp, 1.25f); tv.setIncludeFontPadding(false); tv.setLetterSpacing(0.02f);
        // RESURRECT THE DEAD SPACING: inflate every blank line between blocks

        setSelectable(tv, selectable);
    }
  }

   private void applyBlockSpacing(TextView tv, int extraPx) {
    CharSequence cs = tv.getText();
    Spannable sp = (cs instanceof Spannable) ? (Spannable) cs : new SpannableStringBuilder(cs);
    String s = sp.toString();
    int idx = 0;
    while ((idx = s.indexOf("\n\n", idx)) != -1) {
        int blankStart = idx + 1;
        int blankEnd = idx + 2;
        if (blankEnd <= s.length()) {
            sp.setSpan(new ExtraLineHeightSpan(extraPx), blankStart, blankEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        idx = blankEnd;
    }
    if (sp != cs) tv.setText(sp);
   }
    
    
    private List<String[]> buildRenderUnits(String raw, boolean merge) {
    List<String[]> units = buildUnits(raw, false);
    if (!merge) return units;
    List<String[]> out = new ArrayList<>();
    StringBuilder run = null;
    for (String[] u : units) {
        if (u[0].equals("TEXT")) {
            if (run == null) run = new StringBuilder(u[1]);
            else run.append("\n\n").append(u[1]);
        } else {
            if (run != null) { out.add(new String[]{"TEXT", run.toString(), null}); run = null; }
            out.add(u);
        }
    }
    if (run != null) out.add(new String[]{"TEXT", run.toString(), null});
    return out;
   }

   public void renderBlocks(LinearLayout container, String text) {
    container.removeAllViews();
    for (String[] u : buildUnits(text, false)) {
        View child = inflateUnit(container, u[0], u[2]);
        bindUnit(child, u[0], u[1], u[2], true);
        container.addView(child);
    }
   }

    public void smartStreamRender(LinearLayout container, String text) {
    container.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
   List<String[]> units = buildUnits(balanceItalics(text), true);
    
    while (container.getChildCount() > units.size())
        container.removeViewAt(container.getChildCount() - 1);
        
      for (int i = 0; i < units.size(); i++) {
        String[] u = units.get(i);
        View child = container.getChildAt(i);
        
        if (child == null || !u[0].equals(child.getTag())) {
            if (child != null) container.removeViewAt(i);
            child = inflateUnit(container, u[0], u[2]);
            container.addView(child, i);
        }
        
        TextView probe = u[0].equals("CODE") ? child.findViewById(R.id.codeTextView)
                : u[0].equals("TABLE") ? child.findViewById(R.id.tableInnerTextView)
                : (TextView) child;
                
        if (!u[1].equals(probe.getTag())) bindUnit(child, u[0], u[1], u[2], false);
      }
   }


    public void finalizeStreamedMessage(LinearLayout container) {
    if (container == null) return;
    // smartStreamRender() set FOCUS_BLOCK_DESCENDANTS, which stops long-press selection. Undo it.
    container.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    makeTextSelectable(container);
   }

   private void makeTextSelectable(View v) {
    if (v == null || v.getVisibility() == View.GONE) return;   // skips the hidden table holder too

    int id = v.getId();
    if (id == R.id.copyButton || id == R.id.codeLanguageTextView) return;

    if (v instanceof TextView) {
        setSelectable((TextView) v, true);
    } else if (v instanceof ViewGroup) {
        ViewGroup group = (ViewGroup) v;
        for (int i = 0; i < group.getChildCount(); i++) {
            makeTextSelectable(group.getChildAt(i));
        }
    }
  }

    
    
    

       //  Clearing all animations,so it will not rerun again when we will switch themes
  public void clearAllAnimations() {
    if (handler != null) {
        handler.removeCallbacksAndMessages(null);
    }
    if (runningAnimations != null) {
        runningAnimations.clear();
    }
   }
    


}