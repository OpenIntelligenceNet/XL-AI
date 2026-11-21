package com.xxq.xlai;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.HistoryViewHolder> {

    // List of sessions from the DB
    private List<ChatSessionEntity> sessions = new ArrayList<>();
    
    // Listener interface to handle clicks
    private final OnSessionClickListener listener;

    //  Track the currently active chat session ID (-1 = Brand New Chat)
    private long selectedSessionId = -1;

    // Constructor
    public ChatHistoryAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    // Interface for the click callback
    public interface OnSessionClickListener {
        void onSessionClick(ChatSessionEntity session);
    }

    // Method to update data from MainActivity
    public void setSessions(List<ChatSessionEntity> newSessions) {
        this.sessions = newSessions != null ? newSessions : new ArrayList<>();
        notifyDataSetChanged();
    }

    //  Update the highlighted session dynamically
    public void setSelectedSessionId(long sessionId) {
        if (this.selectedSessionId != sessionId) {
            this.selectedSessionId = sessionId;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_history, parent, false);
        return new HistoryViewHolder(view);
    }

   @Override
  public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
    ChatSessionEntity currentSession = sessions.get(position);
    
    // Set the title text
    holder.titleText.setText(currentSession.title);

    //  DYNAMIC GREY SELECTION BOX LOGIC
    boolean isSelected = (currentSession.sessionId == selectedSessionId);

    if (isSelected) {
        // Build a smooth rounded pill background dynamically
        GradientDrawable selectedBg = new GradientDrawable();
        selectedBg.setShape(GradientDrawable.RECTANGLE);
        
        
        selectedBg.setColor(androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), R.color.chat_item_selected_bg));
        
        float radiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 12f, holder.itemView.getResources().getDisplayMetrics()
        );
        selectedBg.setCornerRadius(radiusPx);
        
        holder.itemView.setBackground(selectedBg);
        holder.titleText.setTypeface(null, Typeface.BOLD);
    } else {
        // Restore standard clickable ripple background for unselected items
        TypedValue outValue = new TypedValue();
        holder.itemView.getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, outValue, true
        );
        holder.itemView.setBackgroundResource(outValue.resourceId);
        holder.titleText.setTypeface(null, Typeface.NORMAL);
    }

    // Handle the click
    holder.itemView.setOnClickListener(v -> {
        if (listener != null) {
            listener.onSessionClick(currentSession);
        }
    });
  }

    @Override
    public int getItemCount() {
        return sessions != null ? sessions.size() : 0;
    }

    // ViewHolder Class
    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.text_chat_title);
        }
    }
}
