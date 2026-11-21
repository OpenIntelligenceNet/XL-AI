package com.xxq.xlai;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.List;

public class SourceAdapter extends RecyclerView.Adapter<SourceAdapter.SourceViewHolder> {

    private final List<SourceResult> sources;
    
    // Define the base URL for fetching favicons
    private static final String FAVICON_BASE_URL = "https://www.google.com/s2/favicons?domain=";

    public SourceAdapter(List<SourceResult> sources) {
        this.sources = sources;
    }

    @NonNull
    @Override
    public SourceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_source_result, parent, false);
        return new SourceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SourceViewHolder holder, int position) {
        SourceResult source = sources.get(position);

        // 1. Set the source data
        holder.sourceDomain.setText(source.getDomain());
        holder.sourceTitle.setText(source.getTitle());
        holder.sourceSnippet.setText(source.getSnippet());
        holder.sourceNumberBadge.setText(String.valueOf(position + 1));

        // 2. Load the favicon URL dynamically based on the domain
        String domain = source.getDomain();
        String faviconUrl = FAVICON_BASE_URL + domain + "&sz=64"; // Request a 64x64 size icon

        Glide.with(holder.itemView.getContext())
             .load(faviconUrl)
             // Use the existing placeholder as a fallback if the favicon can't be loaded (e.g., 404 error)
             .placeholder(R.drawable.ic_default_news_icon)
             .error(R.drawable.ic_default_news_icon) 
             .into(holder.sourceIcon);
        
        // 3. Handle the click: Open the URL in a browser
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(source.getUrl()));
            holder.itemView.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return sources.size();
    }

    static class SourceViewHolder extends RecyclerView.ViewHolder {
        final ImageView sourceIcon;
        final TextView sourceDomain;
        final TextView sourceTitle;
        final TextView sourceSnippet;
        final TextView sourceNumberBadge;

        SourceViewHolder(@NonNull View itemView) {
            super(itemView);
            sourceIcon = itemView.findViewById(R.id.sourceIcon);
            sourceDomain = itemView.findViewById(R.id.sourceDomain);
            sourceTitle = itemView.findViewById(R.id.sourceTitle);
            sourceSnippet = itemView.findViewById(R.id.sourceSnippet);
            sourceNumberBadge = itemView.findViewById(R.id.sourceNumberBadge);
        }
    }
}
