package com.xxq.xlai;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import java.util.Collections;
import java.util.List;

public class SourceBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String ARG_SOURCES = "sources_list";
    private final List<SourceResult> sources;

    /**
     * Recommended constructor pattern for passing dynamic data to a Fragment.
     * @param sources The list of search results to display.
     */
    public SourceBottomSheetFragment(List<SourceResult> sources) {
        // Pass data through a field for convenience (not standard Bundle method, but common for non-recreatable dialogs)
        this.sources = sources != null ? sources : Collections.emptyList();
    }

    // Standard empty constructor (required for Fragment system)
    public SourceBottomSheetFragment() {
        this.sources = Collections.emptyList();
    }
    
    // NOTE: For strict Android best practices (handling process death), 
    // you should serialize SourceResult and pass it via a Bundle in a static newInstance() method. 
    // We are using the direct constructor for simplicity based on your current project structure.

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout you provided
        return inflater.inflate(R.layout.sources_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Setup RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.sourcesRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        SourceAdapter adapter = new SourceAdapter(sources);
        recyclerView.setAdapter(adapter);

        // 2. Handle Close Button
        ImageButton closeButton = view.findViewById(R.id.closeButton);
        closeButton.setOnClickListener(v -> dismiss());
    }
    
    /**
     * Overriding getTheme() to apply a custom style for rounded corners (if needed)
     * You would define R.style.BottomSheetDialogTheme in your styles.xml
     */
    /*
    @Override
    public int getTheme() {
        return R.style.BottomSheetDialogTheme;
    }
    */
}
