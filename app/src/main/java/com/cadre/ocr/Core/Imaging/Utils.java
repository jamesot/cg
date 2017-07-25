package com.cadre.ocr.Core.Imaging;

/**
 * Created by danielmerrill on 5/4/15.
 */
import java.io.ByteArrayOutputStream;

import android.graphics.Bitmap;
        import android.graphics.BitmapFactory;
        import android.graphics.ImageFormat;
        import android.graphics.Rect;
        import android.graphics.YuvImage;

public class Utils {



    public static Bitmap getBitmapImageFromYUV(byte[] data, int left, int top, int width, int height) {
        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        yuvimage.compressToJpeg(new Rect(left, top, width, height), 80, baos);
        byte[] jdata = baos.toByteArray();
        BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
        bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
        return bmp;
    }

}


