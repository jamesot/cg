package com.cadre.ocr;

import org.json.JSONArray;

/**
 * Created by danielmerrill on 5/29/15.
 *
 * Defines an interface for asynctasks to return data to clients
 */
public interface AsyncResponse {
    void processFinish(String output, AsyncTypes.Type type);
    void setListAdapter(JSONArray jsonArray);
    void releaseTask(int thisTaskId, long timestamp);
    void setSearchedWord(String word);
}
