package com.cadre.ocr;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Environment;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.Utils;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.core.Mat.zeros;

/**
 * Created by danielmerrill on 7/24/15.
 */
public class TextRegionDetector {
    private Rect centerWordRect;
    private android.graphics.Rect rectToReturn;
    private int kernelSize;
    private int dilateIndex;
    private String TAG = "TextRegionDetector";
    private final Boolean OPEN = false;
    private final Boolean CLOSE = true;
    private int canvasH;
    private int canvasW;
    private Point canvasCenter;
    private Mat word;
    private int padding; // Set rectangle padding
    private Point rectOpen;
    private Point rectClose;
    private Rect thisContour;
    private int x;
    private int y;
    private int w;
    private int h;
    private Point wordCenter = new Point(0, 0);
    private int thisDistance;
    private int bestDistance;
    private MainActivity mainActivity;
    private boolean paused, lock;
    private int prevDistance;


    public TextRegionDetector(MainActivity mainActivity) {
        kernelSize = 3;
        dilateIndex = 5;
        padding = 5;
        rectToReturn = new android.graphics.Rect();
        paused = false;
        lock = false;
        this.mainActivity = mainActivity;
    }

    public void setDilate(int dilate) {
        dilateIndex = dilate;
    }

    public void setKernalSize(int kernel) {
        kernelSize = kernel;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean getPaused() {
        return this.paused;
    }

    public void setLocked(boolean lock) {
        this.lock = lock;
    }

    public boolean getLocked() {
        return this.lock;
    }

    public Bitmap process(Bitmap input) {
        //Log.i(TAG, "process()");
        Mat rgbaImage = new Mat();
        Utils.bitmapToMat(input, rgbaImage);
//        write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "before") + ".png", rgbaImage);


        if (canvasCenter == null) {
            canvasW = rgbaImage.width();
            canvasH = rgbaImage.height();
            canvasCenter = new Point(canvasW / 2, canvasH / 2);
        }

        //Mat processed = new Mat(rgbaImage, new Rect(roiLMargin, roiTopMargin, roiRMargin, roiBottomMargin)); // set ROI
        Mat processed = new Mat();
        Mat kernel;
        Mat heirarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();


        Imgproc.cvtColor(rgbaImage, rgbaImage, Imgproc.COLOR_BGR2GRAY); // greyscale the entire image
        Imgproc.GaussianBlur(rgbaImage, processed, new Size(5, 5), 0);// blur width more than height b/c we want to blend letters horizontally but not across line breaks
        Imgproc.adaptiveThreshold(processed, processed, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2); // threshold

        kernel = Imgproc.getStructuringElement(Imgproc.MORPH_CROSS, new Size(kernelSize, kernelSize));
        Imgproc.dilate(processed, processed, kernel, new Point(), dilateIndex);
        Imgproc.findContours(processed, contours, heirarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE); // See above


        for (MatOfPoint contour : contours) {
            thisContour = Imgproc.boundingRect(contour);
            x = thisContour.x;
            y = thisContour.y;
            w = thisContour.width;
            h = thisContour.height;

            // Discard areas too large
            if (h > canvasH - 5 && w > canvasW - 10) continue;

            // Discard areas too small
            if (h < 40 || w < 40) continue;

            // Calculate this word's center and find its distance from canvas center
            wordCenter.x = x + (w / 2);
            wordCenter.y = y + (h / 2);
            thisDistance = getDistance(wordCenter, canvasCenter);

            // Load a default center word if first time through
            if (centerWordRect == null) {
                setCenterWord(thisContour, rgbaImage);
                bestDistance = thisDistance;
            }

            // Test to see if this word is closest to the center of the canvas
            if (thisDistance < bestDistance) {
                bestDistance = thisDistance;
                setCenterWord(thisContour, rgbaImage);
            }

            // TODO (Commented out) Draw grey bounding box with padding around all words
            rectOpen = pad(new Point(x, y), rgbaImage, padding, OPEN);
            rectClose = pad(new Point(x + w, y + h), rgbaImage, padding, CLOSE);
            Imgproc.rectangle(rgbaImage, rectOpen, rectClose, new Scalar(0, 255, 0), 2);
//            Imgproc.rectangle(_img, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height),
//            new Scalar(0, 255, 0));


        }
//        write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "after") + ".png", rgbaImage);


        if (centerWordRect != null && !paused) {
            // Cut out word
            rectOpen = pad(new Point(centerWordRect.x, centerWordRect.y), rgbaImage, padding, OPEN);
            rectClose = pad(new Point(centerWordRect.x + centerWordRect.width, centerWordRect.y + centerWordRect.height), rgbaImage, padding, CLOSE);

            Rect currentRect = new Rect(rectOpen, rectClose);


            // don't process the image if the values are outside of the distance tolerated (should play around with this number)

            float differenceTolerated = .1f;
            int currentDistance = bestDistance;
            float movementAmount = (float) Math.abs(currentDistance - prevDistance) / (float) canvasW;

            //Log.i(TAG, "movementAmt =" + movementAmount);

            centerWordRect = null;
            prevDistance = bestDistance;

            if (movementAmount < differenceTolerated) {
                // process image and return it to main activity
                word = new Mat(rgbaImage, currentRect);
                //Imgproc.adaptiveThreshold(word, word, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2);

                // Create a bitmap of just the word
                Bitmap bmp = Bitmap.createBitmap(word.cols(), word.rows(), Bitmap.Config.ARGB_8888);

                Utils.matToBitmap(word, bmp);

//                mainActivity.updateTextRect(rectToReturn);
                return bmp;
            } else {
                mainActivity.updateTextRect(null);
                return null;
            }


        } else {
            mainActivity.updateTextRect(null);
            return null;
        }
    }


//    TODO Process Mat EneoCV

    public void processMat(byte[] input, long timestamp) {

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        MatOfPoint2f matOfPoint2f = new MatOfPoint2f();
        ByteBuffer outputBuffer;
        Point[] rect_points;
        Point[] textLines;
        Bitmap bitmap;
//        int thresh = 100;
        int thresh = 100;
        int max_thresh = 255;
        Mat yuvMat, rgbMat, hsvMat, filterMat, resultMat, heirarchyMat = new Mat(), hierachymat2 = new Mat(), mat_gray = new Mat(), threshold_output = new Mat();
        Bitmap outputCacheBitmap;


        contours.clear();
        Mat _img = Imgcodecs.imdecode(new MatOfByte(input), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
        ;
        Mat mat = Imgcodecs.imdecode(new MatOfByte(input), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);

        if (canvasCenter == null) {
            canvasW = mat.width();
            canvasH = mat.height();
            canvasCenter = new Point(canvasW / 2, canvasH / 2);
        }


//        Log.e("Sec Channels", mat.channels() + "");
//        Imgproc.cvtColor(mat,mat, Imgproc.COLOR_GRAY2BGR);
        Imgproc.cvtColor(mat, mat_gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.blur(mat_gray, mat_gray, new Size(3, 3));
        Imgproc.threshold(mat_gray, threshold_output, thresh, 255, Imgproc.THRESH_BINARY);
//        write("threshold"+System.currentTimeMillis()+".png",mat_gray);
//        write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "MG") + ".png", mat_gray);
//        write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "T") + ".png", threshold_output);


        Imgproc.findContours(threshold_output, contours, heirarchyMat, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE,
                new Point(0, 0));

        List<MatOfPoint> contours_poly;
        ArrayList<Rect> boundRect = new ArrayList<>(contours.size());

        ArrayList<ContourWithData> allContoursWithData = new ArrayList<>();
        ArrayList<ContourWithData> validContoursWithData = new ArrayList<>();

       /* if (contours.size()>4) {
            write("matgrey" + System.currentTimeMillis() + ".png", mat_gray);
            write("threshold"+System.currentTimeMillis()+".png",threshold_output);

        }
*/


        if (contours.size() < 4)
            System.out.println("Not enough contours found");
        else {

            int i = 0, t = 0, m = 0;
            contours_poly = contours;
            for (MatOfPoint mop : contours) {

                thisContour = Imgproc.boundingRect(mop);
                x = thisContour.x;
                y = thisContour.y;
                w = thisContour.width;
                h = thisContour.height;

//                TODO dont need these logs now
//                Log.e("contour h", h + "");
//                Log.e("contour w", w + "");


                // Discard areas too large
                if (h > canvasH - 40 || w > canvasW * 0.4) continue;
//                if (w>h) continue;

                // Discard areas too small
//                if (h < 10 || w < 10) continue;
//                if (h < 50 || w < 40) continue;

                mop.convertTo(matOfPoint2f, CvType.CV_32FC2);
                Rect rec = Imgproc.boundingRect(mop);
                ContourWithData contourWithData = new ContourWithData();
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
//                Log.e("Contour size", Imgproc.contourArea(contours.get(i)) + "");
                i++;

            }

            Log.e("all", allContoursWithData.size() + "");

            for (int j = 0; j < allContoursWithData.size(); j++) {

                if (allContoursWithData.get(j).fltArea > 1000 && allContoursWithData.get(j).fltArea < 10000) {
                    Log.e("Contour size", allContoursWithData.get(j).fltArea + "");
                }
                if (allContoursWithData.get(j).checkIfContourIsValid() && allContoursWithData.get(j).fltArea < 13000) {


                    validContoursWithData.add(allContoursWithData.get(j));
                    Log.e("x is", allContoursWithData.get(j).boundingRect.x + " and y is  " + allContoursWithData.get(j).boundingRect.y);
                }
            }


//            TODO remove all contour that's contained by other contour
               /* for (int j = 0; j < allContoursWithData.size(); j++) {
                    Rect  inner=allContoursWithData.get(j).boundingRect;
                    for (int k = 0; k < allContoursWithData.size(); k++) {
                        Rect  outer=allContoursWithData.get(k).boundingRect;

                        if (inner.x > outer.x && inner.y > outer.y
                                && inner.x + inner.width < outer.x + outer.width
                                && inner.y + inner.height < outer.y + outer.height) {
                            allContoursWithData.remove(j);

                        }
                    }
                }*/


//                TODO  sort by position left to right
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
            ArrayList<Mat> mats = new ArrayList<Mat>();
            Mat duplicate = _img.clone();

//            Drawing each rectangle out in the image
            for (int k = 0; k < validContoursWithData.size(); k++) {

                Imgproc.drawContours(drawing, contours_poly, k, new Scalar(0, 255, 0), 1, 8, hierachymat2, 0, new Point());
                Rect rect = validContoursWithData.get(k).boundingRect;
                Mat digit = new Mat(_img, rect);
                mats.add(digit);


                Imgproc.rectangle(_img, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height), new Scalar(0, 255, 0));
                android.graphics.Rect rectToRet = cvRectToPaddedAndroidRect(rect, 0, _img);
                rectangles.add(rectToRet);
//                mainActivity.updateTextRect(rectToRet);

/*
                Mat word = new Mat(_img, rect);
                //Imgproc.adaptiveThreshold(word, word, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 11, 2);

                // Create a bitmap of just the word
                Bitmap bmp = Bitmap.createBitmap(word.cols(), word.rows(), Bitmap.Config.ARGB_8888);

                Utils.matToBitmap(word, bmp);*/


            }
            Log.e("Mats outside", mats.size() + "");

            if (!paused) {
                mainActivity.updateArrayTextBox(rectangles);
            } else {
//          TODO train each digit
                if (!lock) {
                    Log.e("Mats inside", mats.size() + "");

                    showDialog(mats, _img);
                }

            }
//            write(String.format("%d_%s", Calendar.getInstance().getTimeInMillis(), "Defind") + ".png", _img);

            long timePeriod = System.currentTimeMillis() - timestamp;
            long diffSec = timePeriod / 1000;
            long min = diffSec / 60;
            long sec = diffSec % 60;
            System.out.println("The time spent is " + min + " minutes and " + sec + " seconds.");

        }


    }


    public static class ContourWithData {
        //TODO detecting using KNN
        int MIN_CONTOUR_AREA = 1000;
        int MAX_CONTOUR_AREA = 10000;
        int RESIZED_IMAGE_WIDTH = 20;
        int RESIZED_IMAGE_HEIGHT = 30;
        MatOfPoint ptContour;
        Rect boundingRect;
        float fltArea;

        //|| fltArea > MAX_CONTOUR_AREA
        private boolean checkIfContourIsValid() {

            if (fltArea < MIN_CONTOUR_AREA) return false;
            return true;
        }

    }

    private static int getDistance(Point p1, Point p2) {
        return (int) Math.sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y));
    }

    private static Point pad(Point coord, Mat img, int padding, boolean closed) {
        Point padded = new Point();
        if (!closed) {
            padded.x = (coord.x - padding > 0) ? coord.x - padding : 0;
            padded.y = (coord.y - padding > 0) ? coord.y - padding : 0;
        } else {
            padded.x = (coord.x + padding < img.width()) ? coord.x + padding : img.width();
            padded.y = (coord.y + padding < img.height()) ? coord.y + padding : img.height();
        }

        return padded;
    }

    private android.graphics.Rect cvRectToPaddedAndroidRect(Rect rectToConvert, int padding, Mat img) {
        android.graphics.Rect newRect = new android.graphics.Rect();
        int l = rectToConvert.x;
        int t = rectToConvert.y;
        int r = rectToConvert.x + rectToConvert.width;
        int b = rectToConvert.y + rectToConvert.height;

        newRect.left = l;
        newRect.top = t;
        newRect.right = (r + padding < img.width()) ? r + padding : img.width();
        newRect.bottom = (b + padding < img.height()) ? b + padding : img.height();
       /* newRect.right = r ;
        newRect.bottom = b ;*/

        return newRect;
    }

    private void setCenterWord(Rect newCenterWord, Mat img) {
        centerWordRect = newCenterWord;
        rectToReturn = cvRectToPaddedAndroidRect(centerWordRect, padding, img);
        //Log.i(TAG, "Center Word at " + centerWordRect.x + ", " + centerWordRect.y);
    }

    protected static void write(String name, Mat image) {
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
        Log.e("File saved", "File saved");
    }

    public void showDialog(final ArrayList<Mat> mats, final Mat mat) {

        Log.e(" setting up", "up");

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(mainActivity);

        alertDialogBuilder.setTitle("Type digits");
        alertDialogBuilder.setMessage("Enter the digits enclosed by rectangle in their respective order");

        LinearLayout layout = new LinearLayout(mainActivity);
        layout.setOrientation(LinearLayout.VERTICAL);


        final EditText input = new EditText(mainActivity);
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.BLACK);
        input.setHint("Type digits");
        layout.addView(input);


        alertDialogBuilder.setView(layout);

        alertDialogBuilder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String out = input.getText().toString();
                Log.e("input is", out);

//                splitViaString(Integer.parseInt(input.getText().toString()));

                int number = Integer.parseInt(input.getText().toString());
                boolean first = false;
                int firstDigit = Integer.parseInt(input.getText().toString().charAt(0) + "");
                Log.e("first digit", "" + firstDigit);
                if (firstDigit == 0) {
                    Log.e("first", "first set true");
                    first = true;
                }

//               TODO passing the digits one by one
//                mainActivity.train(mats, splitViaString(number,first),mat);

//                TODO passing the whole image and number for training - Prefereably use an intent to show image so as to train
                mainActivity.trainDigits(input.getText().toString(), mat);
//                paused = false;

            }


        });

        alertDialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });


        AlertDialog alertDialog = alertDialogBuilder.create();
        // show alert
        alertDialog.show();

    }

    public static ArrayList<Integer> splitViaString(int number, boolean first) {

        ArrayList<Integer> result = new ArrayList<>();
        String s = number + "";
        if (first) {
            Log.e("first", "added first");
            result.add(0);
        }

        Log.e("inside split", "split");
        int digits = (int) Math.log10(number);
        for (int i = (int) Math.pow(10, digits); i > 0; i /= 10) {
            System.out.println(number / i);
            int digit = number / i;
            Log.e("Number read", digit + "");
            result.add(digit);
            number %= i;
        }

        return result; // MSD at start of list
    }


    public static void covert(int number) {
        ArrayList<Integer> result = new ArrayList<>();

        String[] k = Integer.toString(number).split("");
        System.out.println("NUMBER OF VALUES =" + k.length);
        int arrymy[] = new int[k.length];
        for (int i = 0; i < k.length; i++) {
            int newInt = Integer.parseInt(k[i]);
            result.add(newInt);
            Log.e("Number read", newInt + "");

        }
    }


}
