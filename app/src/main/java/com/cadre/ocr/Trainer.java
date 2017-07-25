package com.cadre.ocr;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

import com.cadre.eneo.R;


import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.core.Mat.zeros;
import static org.opencv.imgproc.Imgproc.resize;

public class Trainer extends Activity implements CvCameraViewListener2 {
    private static final String TAG = "OCVDetect::Activity";

    private static final int VIEW_MODE_RGBA = 0;
    private static final int VIEW_MODE_GRAY = 1;
    private static final int VIEW_MODE_CANNY = 2;
    private static final int VIEW_MODE_FEATURES = 5;

    private int mViewMode = VIEW_MODE_CANNY;
    ;
    private Mat mRgba, mRgbaT, mRgbaF;
    private Mat mIntermediateMat;
    private Mat mGray;

    private MenuItem mItemPreviewRGBA;
    private MenuItem mItemPreviewGray;
    private MenuItem mItemPreviewCanny;
    private MenuItem mItemPreviewFeatures;
    private String image_name;
    private CameraBridgeViewBase mOpenCvCameraView;
    private Bitmap bitmap;

    private Uri uri;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("mixed_sample");

//                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

//    public Trainer() {
//        Log.i(TAG, "Instantiated new " + this.getClass());
//    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.e(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String filepath = getIntent().getStringExtra("filepath");
        Log.e("path", filepath);
        File file = new File(filepath);

        uri = getIntent().getData();
        InputStream image_stream = null;
        try {
            image_stream = getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(image_stream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            MyShortcuts.showToast("did not find the image", getBaseContext());
        }


//        Utils.bitmapToMat(bitmap, mRgba);
        setContentView(R.layout.activity_main);
        mRgba = Imgcodecs.imread(filepath);

       /* mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.tutorial2_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);*/

       /* Button btn = (Button) findViewById(R.id.capture);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                image_name = String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "meter");
                write(image_name + ".png", mRgba);
                Mat mat = new Mat();
//                MyShortcuts.showToast(Environment.getExternalStorageDirectory().getAbsolutePath() + "/eneo/"+"test.jpg",getBaseContext());
                Mat m = Imgcodecs.imread(Environment.getExternalStorageDirectory().getAbsolutePath() + "/eneo/" + "test.png");
                Imgproc.Canny(m, mRgba, 80, 100);
                write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "canny") + ".png", mRgba);
                Process(mRgba.getNativeObjAddr(), mat.getNativeObjAddr());
                write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "processed") + ".png", mat);
            }
        });*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemPreviewRGBA = menu.add("Preview RGBA");
        mItemPreviewGray = menu.add("Preview GRAY");
        mItemPreviewCanny = menu.add("Canny");
        mItemPreviewFeatures = menu.add("Find features");
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.e(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);

        mRgbaT = new Mat(width, width, CvType.CV_8UC4);
        mRgbaF = new Mat(height, width, CvType.CV_8UC4);
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mIntermediateMat.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        final int viewMode = mViewMode;
        switch (viewMode) {
            case VIEW_MODE_GRAY:
                // input frame has gray scale format
                Imgproc.cvtColor(inputFrame.gray(), mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
                break;
            case VIEW_MODE_RGBA:
                // input frame has RBGA format
                mRgba = inputFrame.rgba();
                break;
            case VIEW_MODE_CANNY:
                // input frame has gray scale format
                mRgba = inputFrame.rgba();
               /* Core.transpose(mRgba, mRgbaT);
                Imgproc.resize(mRgbaT, mRgbaF, mRgbaF.size(), 0, 0, 0);
                Core.flip(mRgbaF, mRgba, 1);*/
                Imgproc.Canny(inputFrame.gray(), mRgba, 80, 100);
//                Imgproc.cvtColor(mIntermediateMat, mRgba, Imgproc.COLOR_GRAY2RGBA, 4);


                Core.transpose(mRgba, mRgbaT);
                resize(mRgbaT, mRgbaF, mRgbaF.size(), 0, 0, 0);
                Core.flip(mRgbaF, mRgba, 1);
                break;
            case VIEW_MODE_FEATURES:
                // input frame has RGBA format
                mRgba = inputFrame.rgba();
                mGray = inputFrame.gray();
                FindFeatures(mGray.getNativeObjAddr(), mRgba.getNativeObjAddr());
                break;
        }

        return mRgba;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);

        if (item == mItemPreviewRGBA) {
            mViewMode = VIEW_MODE_RGBA;
        } else if (item == mItemPreviewGray) {
            mViewMode = VIEW_MODE_GRAY;
        } else if (item == mItemPreviewCanny) {
            mViewMode = VIEW_MODE_CANNY;
        } else if (item == mItemPreviewFeatures) {
            mViewMode = VIEW_MODE_FEATURES;
        }

        return true;
    }


    public static void write(String name, Mat image) {
// TODO Auto-generated method stub

        File path = new File(Environment.getExternalStorageDirectory() + "/eneo/");
        path.mkdirs();
        File file = new File(path, name);

        String downloadsDirectoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + File.separator + "Eneo";

        File eneoDir = new File(downloadsDirectoryPath);
        if (!eneoDir.exists()) {
            eneoDir.mkdir();
        }

        Log.e("Download", downloadsDirectoryPath);

        File saveFile = new File(eneoDir, name);

        String filename = file.toString();


        Imgcodecs.imwrite(saveFile.toString(), image);
        /*try {
            File savefile = new File(fpath, "/" + fname);
            FileOutputStream fout = null;
            try {
                fout = new FileOutputStream(savefile, mode);
            } catch (FileNotFoundException e2) {
                // TODO Auto-generated catch block
                e2.printStackTrace();
            }
            PrintWriter pr = new PrintWriter(fout);
            for (int i = 0; i < image.width(); i++) {
                for (int j = 0; j < image.height(); j++) {
                    pr.print(image + ",");
                    //pr.print("\n");
                }
            }

            pr.close();
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }

    public native void FindFeatures(long matAddrGr, long matAddrRgba);

    public native void Process(long image, long processed);

    public static void find(byte[] square, Mat mat3) {


        Mat mat = Imgcodecs.imdecode(new MatOfByte(square), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
        Log.e("mat size", mat.size() + "");
        Mat last = new Mat();
        Mat m = Imgcodecs.imread(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "test1.png");

//        Imgproc.Canny(mat, mat2, 80, 100);
//        write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "canny") + ".png", mRgba);
//        Detect(mat.getNativeObjAddr(),mat3.getNativeObjAddr());


//        kirti(mat.getNativeObjAddr(), mat3.getNativeObjAddr(), last.getNativeObjAddr());

        write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "processed_boxesk") + ".png", mat3);
        write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "processed_lastk") + ".png", last);

    }


    public static void TrainApp(ArrayList<Mat> digits, ArrayList<Integer> values) {


        Mat mat3 = new Mat();
//        int i = 0;
        Log.e("size",digits.size()+"");

        for (int j = 0; j <digits.size()-1; j++) {
            Mat mat =digits.get(j);
            Mat last = new Mat();
            Long.parseLong(values.get(j) + "");
            Train(mat.getNativeObjAddr(), last.getNativeObjAddr(), Long.parseLong(values.get(j) + ""));

            write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "processed_boxesk_" + values.get(j)) + ".png", mat);
            write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "processed_lastk_" + values.get(j)) + ".png", last);


        }


        /*for (Mat mat : digits) {

            *//*Mat mat = Imgcodecs.imdecode(new MatOfByte(square), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
            Log.e("mat size", mat.size() + "");
            Mat m = Imgcodecs.imread(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "test1.png");*//*
//        Imgproc.Canny(mat, mat2, 80, 100);
//        write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "canny") + ".png", mRgba);
//        Detect(mat.getNativeObjAddr(),mat3.getNativeObjAddr());


//            TODO Pass integer and mat address to Native code for training and saving file to
            if (i < digits.size() - 1||digits.size()>2) {
                Long.parseLong(values.get(i) + "");
                Train(mat.getNativeObjAddr(), last.getNativeObjAddr(), Long.parseLong(values.get(i) + ""));

                write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "processed_boxesk_" + values.get(i)) + ".png", mat);
                write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "processed_lastk_" + values.get(i)) + ".png", last);
                i++;
            }
        }*/

    }

    public static native void Detect(long mat, long done);

    public static native void kirti(long mat, long done, long last, long digit);

    public static native void Train(long mat, long done, long digit);


    //    ArrayList<MatOfPoint> contours;


    //    RNG rng(12345);
    public static void processMat(byte[] input, long timestamp) {
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        MatOfPoint2f matOfPoint2f = new MatOfPoint2f();
        ByteBuffer outputBuffer;
        Point[] rect_points;
        Point[] textLines;
        int thresh = 100;
        int max_thresh = 255;
        Mat yuvMat, rgbMat, hsvMat, filterMat, resultMat, heirarchyMat = new Mat(), hierachymat2 = new Mat(), mat_gray = new Mat(), threshold_output = new Mat();
        Bitmap outputCacheBitmap;

//        public void processMat(byte[] input, long timestamp, ProcessingOutputView processingOutputView) {

        contours.clear();
        Mat _img = Imgcodecs.imdecode(new MatOfByte(input), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
        ;
        Mat mat = Imgcodecs.imdecode(new MatOfByte(input), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);


//        getImageData(input, yuvMat, rgbMat);
//        Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV);
        Log.e("Sec Channels", mat.channels() + "");
//        Imgproc.cvtColor(mat,mat, Imgproc.COLOR_GRAY2BGR);
        Imgproc.cvtColor(mat, mat_gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.blur(mat_gray, mat_gray, new Size(3, 3));
        Imgproc.threshold(mat_gray, threshold_output, thresh, 255, Imgproc.THRESH_BINARY);

//        Core.inRange(hsvMat, lowHSV, highHSV, filterMat);

//        Imgproc.findContours(filterMat, contours, heirarchyMat, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        Imgproc.findContours(threshold_output, contours, heirarchyMat, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE,
                new Point(0, 0));

        List<MatOfPoint> contours_poly;
//        contours_poly.ensureCapacity(contours.size());
        ArrayList<Rect> boundRect = new ArrayList<>(contours.size());

        ArrayList<ContourWithData> allContoursWithData = new ArrayList<>();
        ArrayList<ContourWithData> validContoursWithData = new ArrayList<>();


        if (contours.size() < 2)
            System.out.println("Not enough contours found");
        else {

            int i = 0, t = 0, m = 0;
            /*for (MatOfPoint mop : contours) {
            contours_poly.set(m,null);
                m++;
            }*/
            contours_poly = contours;
            for (MatOfPoint mop : contours) {
//                contours_poly.set(i,contours.get(i));
                mop.convertTo(matOfPoint2f, CvType.CV_32FC2);
                Rect rec = Imgproc.boundingRect(mop);
                ContourWithData contourWithData = new ContourWithData();

//                if (Imgproc.contourArea(contours.get(i)) > 100 && Imgproc.contourArea(contours.get(i)) < 10000) {


                contourWithData.ptContour = contours.get(i);

                MatOfPoint2f approxf1 = new MatOfPoint2f();
                MatOfPoint2f approxf2 = new MatOfPoint2f();
                MatOfPoint approxContour = new MatOfPoint();

                //Convert contours(i) from MatOfPoint to MatOfPoint2f
                contours.get(i).convertTo(approxf1, CvType.CV_32FC2);

//                    TODO size of approxf2 is zero here
//                    contours_poly.get(i).convertTo(approxf2, CvType.CV_32FC2);

                Imgproc.approxPolyDP(approxf1, approxf2, 3, true);


//                    Returning to its initial stateCallback
                approxf2.convertTo(approxContour, CvType.CV_32S);
                contours_poly.set(i, approxContour);
                Rect cp = Imgproc.boundingRect(contours_poly.get(i));

//                    boundRect.set(i, cp);
                boundRect.add(cp);

                contourWithData.boundingRect = cp;
                contourWithData.fltArea = (float) Imgproc.contourArea(contourWithData.ptContour);
//                    contourWithData.fltArea = cv::contourArea(contourWithData.ptContour);
                allContoursWithData.add(contourWithData);
                Log.e("Contour size", Imgproc.contourArea(contours.get(i)) + "");
                t++;


                /*} else {
                    Log.e("Contour size ignored", Imgproc.contourArea(contours.get(i)) + "");
                    contours_poly.set(i,new MatOfPoint());
//                    51348

                }*/

                i++;

               /* for(int i=0; i< contours.size();i++){
                    System.out.println(Imgproc.contourArea(contours.get(i)));
                    if (Imgproc.contourArea(contours.get(i)) > 50 ){
                        Rect rect = Imgproc.boundingRect(contours.get(i));
                        System.out.println(rect.height);
                        if (rect.height > 28){
                            //System.out.println(rect.x +","+rect.y+","+rect.height+","+rect.width);
//                        Core.rectangle(image, new Point(rect.x,rect.y), new Point(rect.x+rect.width,rect.y+rect.height),new Scalar(0,0,255));
//                        Core.rectangle(image, new Point(rect.x,rect.height), new Point(rect.y,rect.width),new Scalar(0,0,255));
                        }
                    }
                }
            *//*
            *
            *
            *
            *
            * *//*
                mop.convertTo(matOfPoint2f, CvType.CV_32FC2);
                RotatedRect rec = Imgproc.minAreaRect(matOfPoint2f);

//                rec.points(rect_points);

                double tempArea = rec.size.area();

                if (maxRect1 == null || tempArea > maxRect1.size.area()) {
                    maxRect2 = maxRect1;
                    maxRect1 = rec;
                } else if (maxRect2 == null || tempArea > maxRect2.size.area()) {
                    maxRect2 = rec;
                }*/

            }

            Log.e("all", allContoursWithData.size() + "");

            for (int j = 0; j < allContoursWithData.size(); j++) {
                if (allContoursWithData.get(j).checkIfContourIsValid()) {
                    validContoursWithData.add(allContoursWithData.get(j));
                }
            }

            /*sort(validContoursWithData.get(0), validContoursWithData.get(validContoursWithData.size()-1),
                    ContourWithData.sortByBoundingRectXPosition());*/

            int j;
            boolean flag = true;
            ContourWithData temp;

            while (flag) {
                flag = false;

                Log.e("valid", validContoursWithData.size() + "");

                for (j = 0; j < validContoursWithData.size() - 1; j++) {

                    if (validContoursWithData.get(j).boundingRect.x < validContoursWithData.get(j + 1).boundingRect.x) {
                        Collections.swap(validContoursWithData, j, j + 1);
                        /*temp =validContoursWithData.get(j);
                        validContoursWithData.set(j,validContoursWithData.get(j+1));
                        validContoursWithData.set(j+1,temp);*/
                    }
                }
            }

            Mat drawing = zeros(threshold_output.size(), CV_8UC3);
            ArrayList<android.graphics.Rect> rectangles = new ArrayList<android.graphics.Rect>();
            for (int k = 0; k < validContoursWithData.size(); k++) {

//                Scalar color = Scalar(rng.uniform(0, 255), rng.uniform(0, 255), rng.uniform(0, 255));
                Imgproc.drawContours(drawing, contours_poly, k, new Scalar(0, 255, 0), 1, 8, hierachymat2, 0, new Point());
//        rectangle(_img, boundRect[i].tl(), boundRect[i].br(), color, 4, 8, 0);

               /* Imgproc.rectangle(_img,
                        validContoursWithData.get(k).boundingRect,
                        new Scalar(0, 255, 0),
                2);*/

                Rect rect = validContoursWithData.get(k).boundingRect;

                /*CustomView customView = new CustomView(context);
                customView.GetRect(rect);*/

                Imgproc.rectangle(_img, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 255, 0));

//                android.graphics.Rect rectToRet = cvRectToPaddedAndroidRect(rect, 5, _img);
//                TestCrop.updateTextRect(rectToRet);

                android.graphics.Rect rectToRet = cvRectToPaddedAndroidRect(rect, 0, _img);
                rectangles.add(rectToRet);

               /* CustomView customView = new CustomView(context);
                customView.GetRect(rect);*/

               /* Mat matROI = threshold_output(validContoursWithData.get(k).boundingRect);

                Mat matROIResized;

                resize(matROI, matROIResized, new Size(20, 30));

                Mat matROIFloat;
                matROIResized.convertTo(matROIFloat, CV_32FC1);*/

//                Mat matROIFlattenedFloat = matROIFloat.reshape(1, 1);

               /* Mat matCurrentChar(0, 0, CV_32F);

                kNearest->findNearest(matROIFlattenedFloat, 1, matCurrentChar);

                float fltCurrentChar = (float) matCurrentChar.at<float>(0, 0);

                strFinalString = strFinalString + char(int(fltCurrentChar));*/
            }

//            TestCrop.updateArrayTextBox(rectangles);

//            write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "java") + ".png", _img);

//            TODO Writing my image to SD CARD here
            write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "Defind") + ".png", _img);


            //region buffer
            //todo: test to see how long this process takes, and if it should be moved to another thread
//            outputBuffer.clear();
//            outputBuffer.putLong(System.currentTimeMillis() - timestamp);
            long timePeriod = System.currentTimeMillis() - timestamp;
            long diffSec = timePeriod / 1000;
            long min = diffSec / 60;
            long sec = diffSec % 60;
            System.out.println("The time spent is " + min + " minutes and " + sec + " seconds.");


            //endregion

        }

//        Utils.matToBitmap(filterMat, outputCacheBitmap);


//        processingOutputView.setBitmap(outputCacheBitmap);
    }


    public static class ContourWithData {
        //TODO detecting using KNN
        int MIN_CONTOUR_AREA = 4000;
        int RESIZED_IMAGE_WIDTH = 20;
        int RESIZED_IMAGE_HEIGHT = 30;
        MatOfPoint ptContour;
        Rect boundingRect;
        float fltArea;


        private boolean checkIfContourIsValid() {
            if (fltArea < MIN_CONTOUR_AREA) return false;
            return true;
        }

    }


    private static android.graphics.Rect cvRectToPaddedAndroidRect(Rect rectToConvert, int padding, Mat img) {
        android.graphics.Rect newRect = new android.graphics.Rect();
        int l = rectToConvert.x;
        int t = rectToConvert.y;
        int r = rectToConvert.x + rectToConvert.width;
        int b = rectToConvert.y + rectToConvert.height;

        newRect.left = rectToConvert.x;
        newRect.top = rectToConvert.y;

        newRect.right = (r + padding < img.width()) ? r + padding : img.width();
        newRect.bottom = (b + padding < img.height()) ? b + padding : img.height();

        return newRect;
    }


}
