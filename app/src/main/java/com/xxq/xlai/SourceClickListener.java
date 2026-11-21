package com.xxq.xlai;

import java.util.List;

/**
 * Interface for communicating a click event from the RecyclerView Adapter (NewChatAdapter)
 * back to the Activity (NewChatActivity) to display the source links.
 */
public interface SourceClickListener {
    
    /**
     * Called when the "Sources" text is clicked on an AI message.
     * @param sources The list of search results to be displayed in the Bottom Sheet.
     */
    void onSourceClicked(List<SourceResult> sources); 
}
