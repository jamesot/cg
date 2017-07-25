package com.cadre.ocr.Core.TessTool;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

/**
 * Created by Fadi on 6/11/2014.
 */
public class TessEngine {

    static final String TAG = "DBG_" + TessEngine.class.getName();

    private Context context;

    private TessEngine(Context context){
        this.context = context;
    }

    public static TessEngine Generate(Context context) {
        return new TessEngine(context);
    }

    public String detectText(Bitmap bitmap) {
        Log.d(TAG, "Initialization of TessBaseApi");
        TessDataManager.initTessTrainedData(context);
        TessBaseAPI tessBaseAPI = new TessBaseAPI();
        String path = TessDataManager.getTesseractFolder();
        Log.d(TAG, "Tess folder: " + path);

        tessBaseAPI.init(path, "eng");
        tessBaseAPI.setDebug(false);

        //get letters only
        //tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
        //tessBaseAPI.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-[]}{" +
        //        ";:'\"\\|~`,./<>?");
        tessBaseAPI.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_WORD);
        Log.d(TAG, "Ended initialization of TessEngine");
        Log.d(TAG, "Running inspection on bitmap");
        tessBaseAPI.setImage(bitmap);
        String inspection = tessBaseAPI.getUTF8Text();
        Log.d(TAG, "Got data: " + inspection);
        tessBaseAPI.end();
        System.gc();
        return inspection;
    }

}
