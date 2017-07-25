package com.cadre.ocr.Core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.ImageView;

import com.cadre.ocr.AsyncResponse;
import com.cadre.ocr.Core.TessTool.TessAsyncEngine;
import com.cadre.ocr.MainActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by Daniel Merrill, modified from version by Fadi
 */
public class CameraEngine {

    static final String TAG = CameraEngine.class.getName();
    Camera.Parameters params;
    MainActivity mActivity;
    MainActivity mainActivity;
    boolean on;
    Camera camera;
    SurfaceHolder surfaceHolder;
    private byte[] yuv;
    private Camera.Size previewSize;
    private int bmpQuality;
    private int width;
    private int height;
    private boolean cameraConfigured = false;
    private AsyncResponse mDelegate;
    private TessAsyncEngine testEngine;

    private ImageView imagePreview;
    private Bitmap previewBmp;

    private Rect captureRect;
    private Rect focusBox;

    private boolean ratioSet;

    Camera.AutoFocusCallback autoFocusCallback = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
        Log.i(TAG, "Focusing...");
        }
    };

    public boolean isOn() {
        return on;
    }

    public void setBox(Rect box) {
        Log.i(TAG, "setBox(), " + box.top + " " + box.right + " " + box.bottom + " " + box.left);
        if (box != null) {
            focusBox = box;
        }
    }

    public void setBmpQuality(int quality) {
        bmpQuality = quality;
    }

    public Rect calcBox() {
        // Previewsize assumes landscape, so the math gets a little wonky
        // rect takes                 (left, top, right, bottom)
        // but that translates to our (top, right, bottom, left)
        int screenWidth = previewSize.height;
        int screenHeight = previewSize.width;

        int width = screenWidth * 6 / 7;
        int height = screenHeight / 9;

        int top = (screenWidth - width) / 2;
        int left = (screenWidth - width) / 2;;
        int right = left + width;
        int bottom = top + height;

        Log.i(TAG, "calcBox(): left=" + left + ", top=" + top + ", right=" + right + ", bottom=" + bottom);


        return new Rect(top, left, bottom, right);
    }


    public void setImagePreview(ImageView imagePreview) {
        this.imagePreview = imagePreview;
    }


    private CameraEngine(SurfaceHolder surfaceHolder, MainActivity mActivity){
        this.mActivity = mActivity;
        this.surfaceHolder = surfaceHolder;
        bmpQuality = 95;
        mainActivity = mActivity;
        ratioSet = false;

    }

    static public CameraEngine New(SurfaceHolder surfaceHolder, MainActivity mActivity){
        Log.d(TAG, "Creating camera engine");
        return  new CameraEngine(surfaceHolder, mActivity);
    }



    public void start() {

        Log.d(TAG, "Entered CameraEngine - start()");

        this.camera = CameraUtils.getCamera();
        params = camera.getParameters();

        // Maybe in the future change this to more dynamically match phone models
        if (params.isZoomSupported()) {
            params.setZoom(0);
        }

        if (!cameraConfigured) {

            Log.i(TAG, "Raw width = " + width + ", height = " + height);
            previewSize = getBestPreviewSize(width, height, params);
            Log.i(TAG, "width=" + previewSize.width + ", height= " + previewSize.height);
            Log.i(TAG, "Max focus areas: " + params.getMaxNumFocusAreas());

            // Figure out if we have continuous focus
            boolean hasFocusModeAuto = false;
            boolean hasFocusModeContinuousPicture = false;
            List<String> supportedFocusModes = params.getSupportedFocusModes();
            for (String s : supportedFocusModes) {
                Log.i(TAG, "Has focus mode: " + s);
                if (s.equals("auto")) {
                    hasFocusModeAuto = true;
                } else if (s.equals("continuous-picture")) {
                    hasFocusModeContinuousPicture = true;
                }
            }

            if (hasFocusModeContinuousPicture) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (hasFocusModeAuto) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            // Camera focus zones are supposed to take a Rect(L, T, R, B) from (-1000, -1000) top left to (1000, 1000) lower right
            // Doesn't seem to work though, oh well
            /*
            focusBox = new Rect(-750, -1000, 750, -500);
            List<Camera.Area> focusAreas = new ArrayList<>();
            focusAreas.add(new Camera.Area(focusBox, 1000));
            params.setFocusAreas(focusAreas);
            */

            if (previewSize != null) {
                params.setPreviewSize(previewSize.width,
                        previewSize.height);
                camera.setParameters(params);
                cameraConfigured = true;
            }
        } else {
            camera.setParameters(params);
        }


        if (this.camera == null)
            return;



        Log.d(TAG, "Got camera hardware");
        Log.i(TAG, "Preview size: " + previewSize.width + " x " + previewSize.height);

        try {

            this.camera.setPreviewDisplay(this.surfaceHolder);
            Log.i(TAG, "Surface width:" + surfaceHolder.getSurfaceFrame().width() + "height: " + surfaceHolder.getSurfaceFrame().height());
            this.camera.setDisplayOrientation(90);
            this.camera.startPreview();

            // Update data each time we get a preview frame
            yuv = new byte[getBufferSize()];

            // Initialize a box to use for capture
            captureRect = calcBox();
            Log.i(TAG, "captureRect: " + captureRect.width() + " " + captureRect.height());

            camera.addCallbackBuffer(yuv);
            camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {

                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {

                        try {
                            //camera.addCallbackBuffer(yuv);

                            previewBmp = null;
                            previewBmp = getBitmapImageFromYUV(data, captureRect, previewSize.width, previewSize.height, bmpQuality);
                            previewBmp = rotateBitmap(previewBmp, 90);
                            if (!ratioSet) {
                                int previewHeight = (previewSize.width > previewSize.height) ? previewSize.width : previewSize.height; // I get width and height mixed up, so I'm going to decide the longer side is the "height"
                                int previewWidth = (previewSize.width > previewSize.height) ? previewSize.height : previewSize.width;
                                mActivity.setPreviewSize(previewWidth, previewHeight);
                                ratioSet = true;
                            }

                            /*TextRegionDetector mDetector = new TextRegionDetector(mainActivity);
                            mDetector.processMat(data,System.currentTimeMillis());*/

                            mActivity.getWordBmpFromFullImage(previewBmp,data);
                            camera.addCallbackBuffer(data);


                        } catch (Exception e) {
                            Log.i(TAG, "onPreviewFrame() error: " + e.toString());
                        }
                    }
            });


            on = true;

            Log.d(TAG, "CameraEngine preview started");

        } catch (IOException e) {
            Log.e(TAG, "Error in setPreviewDisplay");
        }
    }

    public void stop(){

        if(camera != null){
            camera.release();
            camera = null;
            cameraConfigured = false;
        }

        on = false;

        Log.d(TAG, "CameraEngine Stopped");
    }


    private int getBufferSize() {
        int pixelformat = ImageFormat.getBitsPerPixel(camera.getParameters()
                .getPreviewFormat());
        int bufSize = (previewSize.width * previewSize.height * pixelformat) / 8;
        Log.i(TAG, "getBufferSize(): pixelformat = " + pixelformat + ", Previewsize Width = " + previewSize.width + ", height = " + previewSize.height + ", buffer size is " + bufSize);
        return bufSize;
    }
    /*
    * Iterates through the camera sizes and chooses the one that is smaller than the preview width and height
    *
    */

    private Camera.Size getBestPreviewSize(int width, int height, Camera.Parameters parameters) {
        Camera.Size result = null;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            if ((size.width <= width) && (size.height <= height)) {
                Log.i(TAG, "Testing " + size.width + " x " + size.height);
                if (result == null) {
                    // sets the result to the first option we find
                    result = size;
                } else {
                    // if we find another, calculate the area of both and choose the biggest
                    int resultArea = result.width * result.height;
                    int newArea = size.width * size.height;
                    if (newArea > resultArea) {
                        result = size;
                    }
                }

            }
        }
        Log.i(TAG, "Best preview size is width = " + result.width + ", height = " + result.height);
        return result;
    }

    public void setWidth(int width) {
        Log.i(TAG, "setWidth() to " + width);
        this.width = width;
    }

    public void setHeight(int height) {
        Log.i(TAG, "setHeight() to " + height);
        this.height = height;
    }

    public static Bitmap getBitmapImageFromYUV(byte[] data, Rect captureRect, int imageWidth, int imageHeight, int quality) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Create a YuvImage of the entire screen
        YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, imageWidth , imageHeight, null);

        // Capture just the rectangle we want to analyze
        yuvimage.compressToJpeg(captureRect, quality, baos);
        BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
        bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.RGB_565;

        byte[] jdata = baos.toByteArray();
        yuvimage = null;
        baos = null;
        return BitmapFactory.decodeByteArray(jdata, 0, jdata.length, bitmapFatoryOptions);
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle)
    {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }


}


