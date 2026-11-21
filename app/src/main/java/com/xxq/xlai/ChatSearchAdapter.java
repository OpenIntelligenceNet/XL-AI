package com.xxq.xlai;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatSearchAdapter extends RecyclerView.Adapter<ChatSearchAdapter.SearchViewHolder> {

    public interface OnSearchResultClickListener {
        void onResultClick(ChatSearchResult result);
    }

    private List<ChatSearchResult> results = new ArrayList<>();
    private String currentQuery = "";
    private final OnSearchResultClickListener listener;

    public ChatSearchAdapter(OnSearchResultClickListener listener) {
        this.listener = listener;
    }

    public void setResults(List<ChatSearchResult> newResults, String query) {
        this.results = newResults != null ? newResults : new ArrayList<>();
        this.currentQuery = query != null ? query.trim() : "";
        notifyDataSetChanged();
    }

   @NonNull
   @Override
  public SearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    android.content.Context ctx = parent.getContext();
    int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, ctx.getResources().getDisplayMetrics());
    int padV = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14f, ctx.getResources().getDisplayMetrics());

    LinearLayout root = new LinearLayout(ctx);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setLayoutParams(new RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    root.setPadding(padH, padV, padH, padV);

    TypedValue outValue = new TypedValue();
    ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
    root.setBackgroundResource(outValue.resourceId);

    // 1. Main Session Heading
    TextView headingView = new TextView(ctx);
    headingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
    // Changed from Color.BLACK to our dynamic theme color
    headingView.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_primary)); 
    headingView.setTypeface(null, Typeface.BOLD);
    headingView.setMaxLines(1);
    headingView.setEllipsize(TextUtils.TruncateAt.END);

    // 2. Actual Prompt / Message Preview
    TextView promptView = new TextView(ctx);
    promptView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
    // Changed from #666666 to our dynamic theme color
    promptView.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.text_secondary)); 
    promptView.setMaxLines(2);
    promptView.setEllipsize(TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams promptParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    promptParams.topMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, ctx.getResources().getDisplayMetrics());
    promptView.setLayoutParams(promptParams);

    root.addView(headingView);
    root.addView(promptView);

    return new SearchViewHolder(root, headingView, promptView);
  }
    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        ChatSearchResult item = results.get(position);
        holder.headingText.setText(highlightKeyword(item.title, currentQuery));

        String cleanSnippet = item.matchedPrompt != null ? item.matchedPrompt.replace("\n", " ").trim() : "";
        holder.promptText.setText(highlightKeyword(cleanSnippet, currentQuery));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onResultClick(item);
            }
        });
    }

    private CharSequence highlightKeyword(String text, String query) {
        if (text == null || query.isEmpty()) return text != null ? text : "";
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int startIdx = lowerText.indexOf(lowerQuery);
        if (startIdx == -1) return text;

        SpannableString spannable = new SpannableString(text);
        spannable.setSpan(new StyleSpan(Typeface.BOLD), startIdx, startIdx + query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannable.setSpan(new ForegroundColorSpan(Color.BLACK), startIdx, startIdx + query.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static class SearchViewHolder extends RecyclerView.ViewHolder {
        TextView headingText;
        TextView promptText;

        SearchViewHolder(@NonNull View itemView, TextView headingText, TextView promptText) {
            super(itemView);
            this.headingText = headingText;
            this.promptText = promptText;
        }
    }
}
