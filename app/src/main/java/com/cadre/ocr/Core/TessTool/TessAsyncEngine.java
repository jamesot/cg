package com.cadre.ocr.Core.TessTool;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;

import com.cadre.ocr.AsyncResponse;
import com.cadre.ocr.AsyncTypes;
import com.cadre.ocr.Core.Imaging.Tools;

/**
 * Created by Fadi on 6/11/2014.
 */
public class TessAsyncEngine extends AsyncTask<Object, Void, String> {

    static final String TAG = "DBG_" + TessAsyncEngine.class.getName();

    private Bitmap bmp;
    private Activity context;
    private AsyncResponse delegate = null;
    private int engineIndex;
    private TessEngine tessEngine;


    @Override
    protected String doInBackground(Object... params) {
        try {
            if(params.length < 5) {
                Log.e(TAG, "Error passing parameter to execute - missing params");
                return null;
            }

            if(!(params[0] instanceof Activity) || !(params[1] instanceof Bitmap) || !(params[2] instanceof AsyncResponse) || !(params[4] instanceof TessEngine)) {
                Log.e(TAG, "Error passing parameter to execute(context, bitmap)");
                return null;
            }

            context = (Activity)params[0];
            bmp = (Bitmap)params[1];
            delegate = (AsyncResponse) params[2];
            engineIndex = (int) params[3];
            tessEngine = (TessEngine)params[4];

            if(context == null || bmp == null || delegate == null || tessEngine == null) {
                Log.e(TAG, "Error passed null parameter to execute(context, bitmap)");
                return null;
            }

            int rotate = 0;

          //  if(params.length == 3 && params[2]!= null && params[2] instanceof Integer){
          //      rotate = (Integer) params[2];
           // }

            if(rotate >= -180 && rotate <= 180 && rotate != 0)
            {
                bmp = Tools.preRotateBitmap(bmp, rotate);
                Log.d(TAG, "Rotated OCR bitmap " + rotate + " degrees");
            }

            //TessEngine tessEngine =  TessEngine.Generate(context);

            bmp = bmp.copy(Bitmap.Config.ARGB_8888, true);

            String result = tessEngine.detectText(bmp);

            Log.d(TAG, result);

            return result;

        } catch (Exception ex) {
            Log.d(TAG, "Error: " + ex + "\n" + ex.getMessage());
        }

        return null;
    }

    @Override
    protected void onPostExecute(String s) {

        if(s == null || bmp == null || context == null)
            return;

        delegate.processFinish(s, AsyncTypes.Type.WORD);
        delegate.releaseTask(engineIndex, System.currentTimeMillis());
        super.onPostExecute(s);
    }
}
