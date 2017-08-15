#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <opencv2/ml.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <vector>
#include <android/log.h>
#include <fstream>
#include <iostream>
#include <string>
#include <stdio.h>
#include <math.h>

using namespace std;
using namespace cv;
Mat _img;
std::vector<cv::Mat> _digits;

void setInput(cv::Mat &img);

cv::Mat process();


extern "C" {
JNIEXPORT void JNICALL Java_com_cadre_ocr_Trainer_FindFeatures(JNIEnv *, jobject,
                                                               jlong addrGray,
                                                               jlong addrRgba);

JNIEXPORT void JNICALL Java_com_cadre_ocr_Trainer_FindFeatures(JNIEnv *, jobject,
                                                               jlong addrGray,
                                                               jlong addrRgba) {
    Mat &mGr = *(Mat *) addrGray;
    Mat &mRgb = *(Mat *) addrRgba;
    vector<KeyPoint> v;

    Ptr<FeatureDetector> detector = FastFeatureDetector::create(50);
    detector->detect(mGr, v);
    for (unsigned int i = 0; i < v.size(); i++) {
        const KeyPoint &kp = v[i];
        circle(mRgb, Point(kp.pt.x, kp.pt.y), 10, Scalar(255, 0, 0, 255));
    }
}

JNIEXPORT void JNICALL Java_com_cadre_ocr_Trainer_Process(JNIEnv *env, jobject,
                                                          jlong image, jlong result) {

    // TODO
    Mat &image2 = *(Mat *) image;
    Mat &image3 = *(Mat *) result;
    setInput(image2);
    image3 = process();


}
}

class sortRectByX {
public:
    bool operator()(cv::Rect const &a, cv::Rect const &b) const {
        return a.x < b.x;
    }
};

cv::Mat cannyEdges() {
    cv::Mat edges;
    // detect edges
    cv::Canny(_img, edges, 80, 100);
    return edges;
}

void setInput(cv::Mat &img) {
    _img = img;
}

/**
 * Draw lines into image.
 * For debugging purposes.
 */
void drawLines(std::vector<cv::Vec2f> &lines) {
    // draw lines
    for (size_t i = 0; i < lines.size(); i++) {
        float rho = lines[i][0];
        float theta = lines[i][1];
        double a = cos(theta), b = sin(theta);
        double x0 = a * rho, y0 = b * rho;
        cv::Point pt1(cvRound(x0 + 1000 * (-b)), cvRound(y0 + 1000 * (a)));
        cv::Point pt2(cvRound(x0 - 1000 * (-b)), cvRound(y0 - 1000 * (a)));
        cv::line(_img, pt1, pt2, cv::Scalar(255, 0, 0), 1);
    }
}

//TODO Detecting skew of the image
float detectSkew(Mat mat) {
// TODO    log4cpp::Category &rlog = log4cpp::Category::getRoot();

    cv::Mat edges = mat;

    // find lines
    std::vector<cv::Vec2f> lines;
    cv::HoughLines(edges, lines, 1, CV_PI / 180.f, 140);

    // filter lines by theta and compute average
    std::vector<cv::Vec2f> filteredLines;
    float theta_min = 60.f * CV_PI / 180.f;
    float theta_max = 120.f * CV_PI / 180.0f;
    float theta_avr = 0.f;
    float theta_deg = 0.f;
    for (size_t i = 0; i < lines.size(); i++) {
        float theta = lines[i][1];
        if (theta >= theta_min && theta <= theta_max) {
            filteredLines.push_back(lines[i]);
            theta_avr += theta;
        }
    }
    if (filteredLines.size() > 0) {
        theta_avr /= filteredLines.size();
        theta_deg = (theta_avr / CV_PI * 180.f) - 90;

        __android_log_print(ANDROID_LOG_ERROR, "SKEW IS", "%.1f deg", theta_deg);
//      TODO  rlog.info("detectSkew: %.1f deg", theta_deg);
    } else {
//       TODO rlog.warn("failed to detect skew");
        __android_log_print(ANDROID_LOG_ERROR, "failed to detect skew", "%s deg", " f");

    }

    if (true) {
        drawLines(filteredLines);
    }

    return theta_deg;
}

/**
 * Filter contours by size of bounding rectangle.
 */
void filterContours(std::vector<std::vector<cv::Point> > &contours,
                    std::vector<cv::Rect> &boundingBoxes,
                    std::vector<std::vector<cv::Point> > &filteredContours) {
    // filter contours by bounding rect size
    for (size_t i = 0; i < contours.size(); i++) {
        cv::Rect bounds = cv::boundingRect(contours[i]);
        if (bounds.height > 20 &&
            bounds.height < 90
            && bounds.width > 5 && bounds.width < bounds.height) {
            boundingBoxes.push_back(bounds);
            filteredContours.push_back(contours[i]);
        }
    }
}

/**
 * Find bounding boxes that are aligned at y position.
 */
void findAlignedBoxes(std::vector<cv::Rect>::const_iterator begin,
                      std::vector<cv::Rect>::const_iterator end,
                      std::vector<cv::Rect> &result) {
    std::vector<cv::Rect>::const_iterator it = begin;
    cv::Rect start = *it;
    ++it;
    result.push_back(start);

    for (; it != end; ++it) {
        if (abs(start.y - it->y) < 10 &&
            abs(start.height - it->height) < 5) {
            result.push_back(*it);
        }
    }
}

cv::Rect bounding_rect;

cv::Mat findBiggestBlob(cv::Mat &matImage) {
    int largest_area = 0;
    int largest_contour_index = 0;

    vector<vector<Point> > contours; // Vector for storing contour
    vector<Vec4i> hierarchy;

    findContours(matImage, contours, hierarchy, CV_RETR_CCOMP,
                 CV_CHAIN_APPROX_SIMPLE); // Find the contours in the image

    for (int i = 0; i < contours.size(); i++) {// iterate through each contour.
        double a = contourArea(contours[i], false);  //  Find the area of contour
        if (a > largest_area) {
            largest_area = a;
            largest_contour_index = i;                //Store the index of largest contour
            bounding_rect = boundingRect(
                    contours[i]); // Find the bounding rectangle for biggest contour
        }
    }


    drawContours(matImage, contours, largest_contour_index, Scalar(255), CV_FILLED, 8,
                 hierarchy); // Draw the largest contour using previously stored index.
    cv::rectangle(_img, bounding_rect, cv::Scalar(0, 255, 0), 2);

    return matImage;
}

/**
 * TODO Find and isolate the digits of the counter,
 */
void findCounterDigits(Mat mat) {

    // edge image
    cv::Mat edges = mat;

    cv::Mat img_ret = edges.clone();

    // find contours in whole image
    std::vector<std::vector<cv::Point> > contours, filteredContours;
    std::vector<cv::Rect> boundingBoxes;


    cv::findContours(edges, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);
//    cv::findContours(edges, contours, cv::RETR_EXTERNAL,  cv::CHAIN_APPROX_SIMPLE);

    // filter contours by bounding rect size
    filterContours(contours, boundingBoxes, filteredContours);



// TODO   rlog << log4cpp::Priority::INFO << "number of filtered contours: " << filteredContours.size();
    __android_log_print(ANDROID_LOG_ERROR, "number of filtered contours:", "%d",
                        filteredContours.size());

    // find bounding boxes that are aligned at y position
    std::vector<cv::Rect> alignedBoundingBoxes, tmpRes;
    for (std::vector<cv::Rect>::const_iterator ib = boundingBoxes.begin();
         ib != boundingBoxes.end(); ++ib) {
        tmpRes.clear();
        findAlignedBoxes(ib, boundingBoxes.end(), tmpRes);
        if (tmpRes.size() > alignedBoundingBoxes.size()) {
            alignedBoundingBoxes = tmpRes;
        }
    }
//    TODO rlog << log4cpp::Priority::INFO << "max number of alignedBoxes: " <<
    alignedBoundingBoxes.size();
    alignedBoundingBoxes = boundingBoxes;
    __android_log_print(ANDROID_LOG_ERROR, "max number of alignedBoxes:", "%d",
                        alignedBoundingBoxes.size());



    // sort bounding boxes from left to right
    std::sort(alignedBoundingBoxes.begin(), alignedBoundingBoxes.end(), sortRectByX());

    if (true) {
        // draw contours
        cv::Mat cont = cv::Mat::zeros(edges.rows, edges.cols, CV_8UC1);
        cv::drawContours(cont, filteredContours, -1, cv::Scalar(255));
//      TODO  cv::imshow("contours", cont);
        _img = cont;
    }

    // TODO cut out found rectangles from edged image
    for (int i = 0; i < alignedBoundingBoxes.size(); ++i) {
        cv::Rect roi = alignedBoundingBoxes[i];
        _digits.push_back(img_ret(roi));
        cv::rectangle(_img, roi, cv::Scalar(0, 255, 0), 2);
    }
}

/**
 * TODO Rotate image function.
 */
void rotate(double rotationDegrees) {
    cv::Mat M = cv::getRotationMatrix2D(cv::Point(_img.cols / 2, _img.rows / 2),
                                        rotationDegrees, 1);
    cv::Mat img_rotated;
    cv::warpAffine(_img, img_rotated, M, _img.size());
    _img = img_rotated;
    if (true) {
        cv::warpAffine(_img, img_rotated, M, _img.size());
        _img = img_rotated;
    }
}

cv::Mat process() {
    _digits.clear();


    // detect and correct remaining skew (+- 30 deg)
    float skew_deg = detectSkew(_img);
    rotate((double) skew_deg);

    // find and isolate counter digits
//    findCounterDigits(_img);
//TODO RETURN THE IMAGE HERE
    /*if (_debugWindow) {
        showImage();
    }*/
    return _img;
}

bool sort_by_x(const vector<Point> &ca, const vector<Point> &cb);

bool sort_by_x(const vector<Point> &ca, const vector<Point> &cb) {
    return boundingRect(ca).x < boundingRect(cb).x;
}

/**
 * Draw lines into image.
 * For debugging purposes.
 */
void drawLines(std::vector<cv::Vec2f> &lines);


/**
 * Draw lines into image.
 * For debugging purposes.
 */
void drawLines(std::vector<cv::Vec4i> &lines, int xoff, int yoff) {
    for (size_t i = 0; i < lines.size(); i++) {
        cv::line(_img, cv::Point(lines[i][0] + xoff, lines[i][1] + yoff),
                 cv::Point(lines[i][2] + xoff, lines[i][3] + yoff), cv::Scalar(255, 0, 0), 1);
    }
}


//TODO detecting using KNN
const int MIN_CONTOUR_AREA = 4000;
const int RESIZED_IMAGE_WIDTH = 20;
const int RESIZED_IMAGE_HEIGHT = 30;

class ContourWithData {
public:
    std::vector<cv::Point> ptContour;
    cv::Rect boundingRect;
    float fltArea;


    bool checkIfContourIsValid() {
        if (fltArea < MIN_CONTOUR_AREA) return false;
        return true;
    }

    static bool
    sortByBoundingRectXPosition(const ContourWithData &cwdLeft, const ContourWithData &cwdRight) {
        return (cwdLeft.boundingRect.x < cwdRight.boundingRect.x);
    }

};

extern "C"
JNIEXPORT void JNICALL
Java_com_cadre_ocr_Trainer_Detect(JNIEnv *env, jobject instance, jlong mat,
                                  jlong addrRgba) {

    // TODO Creating My Detecting Algorithm here

    std::vector<ContourWithData> allContoursWithData;
    std::vector<ContourWithData> validContoursWithData;

    Mat &image = *(Mat *) mat;
    Mat &processed = *(Mat *) addrRgba;

    Mat image2;


    cv::cvtColor(image, image2, CV_BGR2GRAY);
    setInput(image2);
//    float skew_deg = detectSkew(_img);
//    rotate((double)skew_deg);


//    processed=_img;
    cv::Mat matClassificationInts;

//   TODO Passing path to classifications file

    cv::FileStorage fsClassifications("/storage/emulated/0/EneoCV/classifications.xml",
                                      cv::FileStorage::READ);

    if (fsClassifications.isOpened() == false) {
//        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open classifications:", "%s", "");

        /*std::cout << "error, unable to open training classifications file, exiting program\n\n";
        return (0);*/
    }

    fsClassifications["classifications"] >> matClassificationInts;
    fsClassifications.release();


    cv::Mat matTrainingImagesAsFlattenedFloats;

    cv::FileStorage fsTrainingImages("/storage/emulated/0/EneoCV/images.xml",
                                     cv::FileStorage::READ);

    if (fsTrainingImages.isOpened() == false) {
        //        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open images:", "%s", "");


        /* std::cout << "error, unable to open training images file, exiting program\n\n";
         return(0);*/
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "successfully opened the image:", "%s", "");

    }

    fsTrainingImages["images"] >> matTrainingImagesAsFlattenedFloats;
    fsTrainingImages.release();


//    cv::Ptr<cv::ml::KNearest>  kNearest(cv::ml::KNearest::create());
    Ptr<ml::KNearest> kNearest = ml::KNearest::create();

    kNearest->train(matTrainingImagesAsFlattenedFloats, cv::ml::ROW_SAMPLE, matClassificationInts);


    cv::Mat matTestingNumbers = image;

    if (matTestingNumbers.empty()) {
        //        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open image file passed:", "%s", "");

/*

        std::cout << "error: image not read from file\n\n";

*/
    }

    cv::Mat matGrayscale;
    cv::Mat matBlurred;
    cv::Mat matThresh;
    cv::Mat matThreshCopy;

    cv::cvtColor(matTestingNumbers, matGrayscale, CV_BGR2GRAY);

    cv::GaussianBlur(matGrayscale,
                     matBlurred,
                     cv::Size(5, 5),
                     0);

    cv::adaptiveThreshold(matBlurred,
                          matThresh,
                          255,
                          cv::ADAPTIVE_THRESH_MEAN_C,
                          cv::THRESH_BINARY_INV,
                          11,
                          14);

    matThreshCopy = matThresh.clone();

    std::vector<std::vector<cv::Point> > ptContours;
    std::vector<cv::Vec4i> v4iHierarchy;

//    cv::findContours(matBlurred, ptContours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);
//TODO START
//    findCounterDigits(matThreshCopy);

//    findBiggestBlob(matThreshCopy);

//TODO END
    /*  cv::findContours(matThreshCopy,
                      ptContours,
                      v4iHierarchy,
                      cv::RETR_EXTERNAL,
                      cv::CHAIN_APPROX_SIMPLE);*/

    for (int i = 0; i < ptContours.size(); i++) {
        ContourWithData contourWithData;
        contourWithData.ptContour = ptContours[i];
        contourWithData.boundingRect = cv::boundingRect(contourWithData.ptContour);
        contourWithData.fltArea = cv::contourArea(contourWithData.ptContour);
        allContoursWithData.push_back(contourWithData);
    }

    for (int i = 0; i < allContoursWithData.size(); i++) {
        if (allContoursWithData[i].checkIfContourIsValid()) {
            validContoursWithData.push_back(allContoursWithData[i]);
        }
    }

    std::sort(validContoursWithData.begin(), validContoursWithData.end(),
              ContourWithData::sortByBoundingRectXPosition);

    std::string strFinalString;

    for (int i = 0; i < validContoursWithData.size(); i++) {
        cv::rectangle(matTestingNumbers,
                      validContoursWithData[i].boundingRect,
                      cv::Scalar(0, 255, 0),
                      2);

        cv::Mat matROI = matThresh(validContoursWithData[i].boundingRect);

        cv::Mat matROIResized;
        cv::resize(matROI, matROIResized, cv::Size(RESIZED_IMAGE_WIDTH, RESIZED_IMAGE_HEIGHT));

        cv::Mat matROIFloat;
        matROIResized.convertTo(matROIFloat, CV_32FC1);

        cv::Mat matROIFlattenedFloat = matROIFloat.reshape(1, 1);

        cv::Mat matCurrentChar(0, 0, CV_32F);

        kNearest->findNearest(matROIFlattenedFloat, 1, matCurrentChar);

        float fltCurrentChar = (float) matCurrentChar.at<float>(0, 0);

        strFinalString = strFinalString + char(int(fltCurrentChar));
    }

    //        TODO Log success and result
    __android_log_print(ANDROID_LOG_ERROR, "Number read is:", "%s", strFinalString.c_str());

//    processed=_img;
    processed = _img;

    /* std::cout << "\n\n" << "Numbers read = " << strFinalString << "\n\n";

     cv::imshow("matTestingNumbers", matTestingNumbers);

     cv::waitKey(0);

     return(0);*/


}

extern "C"
JNIEXPORT void JNICALL
Java_com_cadre_ocr_Trainer_kirti(JNIEnv *env, jclass type, jlong mat, jlong done,
                                 jlong last, jlong digit) {

    std::vector<ContourWithData> allContoursWithData;
    std::vector<ContourWithData> validContoursWithData;

    Mat src_gray;
    Mat &image = *(Mat *) mat;
    Mat &processed = *(Mat *) done;
    Mat &lastOne = *(Mat *) last;
    int thresh = 100;
    int max_thresh = 255;
    RNG rng(12345);
//    TODO pass this number as the digit read for that integer
    int number = (int) digit;

    setInput(image);

    /// Convert image to gray and blur it
    cvtColor(image, src_gray, CV_BGR2GRAY);
    blur(src_gray, src_gray, Size(3, 3));

    Mat threshold_output;
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;

//    TODO Setting up initial variables

    cv::Mat matClassificationInts;

//   TODO Passing path to classifications file

    cv::FileStorage fsClassifications("/storage/emulated/0/EneoCV/classifications.xml",
                                      cv::FileStorage::READ);

    if (fsClassifications.isOpened() == false) {
//        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open classifications:", "%s", "");

        /*std::cout << "error, unable to open training classifications file, exiting program\n\n";
        return (0);*/
    }

    fsClassifications["classifications"] >> matClassificationInts;
    fsClassifications.release();


    cv::Mat matTrainingImagesAsFlattenedFloats;

    cv::FileStorage fsTrainingImages("/storage/emulated/0/EneoCV/images.xml",
                                     cv::FileStorage::READ);

    if (fsTrainingImages.isOpened() == false) {
        //        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open images:", "%s", "");


        /* std::cout << "error, unable to open training images file, exiting program\n\n";
         return(0);*/
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "successfully opened the image:", "%s", "");

    }

    fsTrainingImages["images"] >> matTrainingImagesAsFlattenedFloats;
    fsTrainingImages.release();


//    cv::Ptr<cv::ml::KNearest>  kNearest(cv::ml::KNearest::create());
    Ptr<ml::KNearest> kNearest = ml::KNearest::create();

    kNearest->train(matTrainingImagesAsFlattenedFloats, cv::ml::ROW_SAMPLE, matClassificationInts);


//    TODO Ending initial variables section

    /// Detect edges using Threshold
    threshold(src_gray, threshold_output, thresh, 255, THRESH_BINARY);
    /// Find contours
    findContours(threshold_output, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE,
                 Point(0, 0));

    /// Approximate contours to polygons + get bounding rects and circles
    vector<vector<Point> > contours_poly(contours.size());
    vector<Rect> boundRect(contours.size());
    vector<Point2f> center(contours.size());
    vector<float> radius(contours.size());

    /*for (int i = 0; i < contours.size(); i++) {
        approxPolyDP(Mat(contours[i]), contours_poly[i], 3, true);
        boundRect[i] = boundingRect(Mat(contours_poly[i]));
//        minEnclosingCircle( (Mat)contours_poly[i], center[i], radius[i] );
    }*/

//    TODO Machine Learning
    for (int i = 0; i < contours.size(); i++) {
        ContourWithData contourWithData;
        if (contours[i].size() > 300 && contours[i].size() < 1000) {
            contourWithData.ptContour = contours[i];
            approxPolyDP(Mat(contours[i]), contours_poly[i], 3, true);
            boundRect[i] = boundingRect(Mat(contours_poly[i]));

            contourWithData.boundingRect = boundRect[i];
            contourWithData.fltArea = cv::contourArea(contourWithData.ptContour);
            allContoursWithData.push_back(contourWithData);
            __android_log_print(ANDROID_LOG_ERROR, "Contour Size:", "%d", contours[i].size());

        } else {
            __android_log_print(ANDROID_LOG_ERROR, "Contour Size Ignored:", "%d",
                                contours[i].size());
        }


        /*approxPolyDP(Mat(contours[i]), contours_poly[i], 3, true);
        boundRect[i] = boundingRect(Mat(contours_poly[i]));

        contourWithData.boundingRect = boundRect[i];
        contourWithData.fltArea = cv::contourArea(contourWithData.ptContour);
        allContoursWithData.push_back(contourWithData);*/
    }

    for (int i = 0; i < allContoursWithData.size(); i++) {
        if (allContoursWithData[i].checkIfContourIsValid()) {
            validContoursWithData.push_back(allContoursWithData[i]);
        }
    }

    std::sort(validContoursWithData.begin(), validContoursWithData.end(),
              ContourWithData::sortByBoundingRectXPosition);

    std::string strFinalString;
    Mat drawing = Mat::zeros(threshold_output.size(), CV_8UC3);


    for (int i = 0; i < validContoursWithData.size(); i++) {

        Scalar color = Scalar(rng.uniform(0, 255), rng.uniform(0, 255), rng.uniform(0, 255));
        drawContours(drawing, contours_poly, i, color, 1, 8, vector<Vec4i>(), 0, Point());
//        rectangle(_img, boundRect[i].tl(), boundRect[i].br(), color, 4, 8, 0);

        cv::rectangle(_img,
                      validContoursWithData[i].boundingRect,
                      cv::Scalar(0, 255, 0),
                      2);

        cv::Mat matROI = threshold_output(validContoursWithData[i].boundingRect);

        cv::Mat matROIResized;
        cv::resize(matROI, matROIResized, cv::Size(RESIZED_IMAGE_WIDTH, RESIZED_IMAGE_HEIGHT));

        cv::Mat matROIFloat;
        matROIResized.convertTo(matROIFloat, CV_32FC1);

        cv::Mat matROIFlattenedFloat = matROIFloat.reshape(1, 1);

        cv::Mat matCurrentChar(0, 0, CV_32F);

        kNearest->findNearest(matROIFlattenedFloat, 1, matCurrentChar);

        float fltCurrentChar = (float) matCurrentChar.at<float>(0, 0);

        strFinalString = strFinalString + char(int(fltCurrentChar));
    }

    __android_log_print(ANDROID_LOG_ERROR, "Number read is:", "%s", strFinalString.c_str());

//    TODO Machine Learning end


    /// Draw polygonal contour + bonding rects + circles
//    Mat drawing = Mat::zeros(threshold_output.size(), CV_8UC3);
    /*for (int i = 0; i < contours.size(); i++) {
        Scalar color = Scalar(rng.uniform(0, 255), rng.uniform(0, 255), rng.uniform(0, 255));
        drawContours(drawing, contours_poly, i, color, 1, 8, vector<Vec4i>(), 0, Point());
        rectangle(_img, boundRect[i].tl(), boundRect[i].br(), color, 4, 8, 0);
//        rectangle(_img, boundRect[i].tl(), cv::Scalar(0, 255, 0), 2);
//        circle( drawing, center[i], (int)radius[i], color, 2, 8, 0 );
    }*/

    /// Show in a window
//    namedWindow( "Contours", CV_WINDOW_AUTOSIZE );
//    imshow( "Contours", drawing );
    processed = _img;
    lastOne = threshold_output;
// Created by Stephine Osoro on 08/11/2016.
//

    /* Mat &image = *(Mat *) mat;
     Mat &processed = *(Mat *) done;
     Mat &lastOne = *(Mat *) last;


     Mat test_data;
     Rect bounding_rect;
     Mat inputImage, grayImage, blurredImage, thresholdImage, contourImage, regionOfInterest;
     inputImage = image;

 //inputImage= imread("/root/Desktop/test_ocr/index.jpg",1);
     cvtColor(inputImage, grayImage, CV_BGR2GRAY);
     threshold(grayImage, thresholdImage, 90, 255, CV_THRESH_OTSU | CV_THRESH_BINARY);
     vector<vector<Point> > contours;
     vector<Vec4i> hierarchy;
     findContours(thresholdImage, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_TC89_KCOS,
                  Point(0, 0));
     vector<vector<Point> > contours_poly(contours.size());
     vector<Rect> boundRect(contours.size());
     int kirti = contours.size();
 //    Mat ROI[kirti];

     Mat *ROI = new Mat[kirti]; //right
     sort(contours.begin(), contours.end(), sort_by_x);
 //cout<< kirti<<endl;
 //cout<<"****************"<<endl;
     for (size_t i = 0; i < contours.size(); i++) {
         approxPolyDP(Mat(contours[i]), contours_poly[i], 5, false);
         boundRect[i] = boundingRect(Mat(contours_poly[i]));
         rectangle(grayImage, boundRect[i].tl(), boundRect[i].br(), Scalar(0, 255, 0), 1, 8, 0);
     }
 //tl()= top left corner br()= bottom right corner
     vector<int> number;
     vector<int>::iterator it;
     int predicted;
     string name;
     for (int i = 0; i < kirti; i++) {
         if (boundRect[i].width > 50 && boundRect[i].height > 50) {
             ROI[i] = grayImage(boundRect[i]);
             name = format("%d.jpg", i);
             Mat result;
             result = ROI[i].clone();
             resize(result, result, Size(28, 28));
             threshold(result, result, 100, 255, CV_THRESH_BINARY_INV);
             lastOne = result;
 //            imwrite(name,result);
 //            imshow("sequence",result);
 //                waitKey(1000);
             *//*cv::ml::SVM svm;
            svm.load("classifier.xml");
            cv::Mat testMat = result.clone().reshape(1,1);
            testMat.convertTo(testMat, CV_32F);
            predicted = svm.predict(testMat);
            cout << endl << "Recognizing following number -> " << predicted<< endl << endl;
            number.push_back(predicted);*//*
        }
    }
    for (it = number.begin(); it < number.end(); it++) {
//        cout<<*it;
        __android_log_print(ANDROID_LOG_ERROR, "Number read is:", "%d", *it);

    }
//cout<<number;
    *//*namedWindow("input", CV_WINDOW_FREERATIO);
    imshow("input", grayImage);
    waitKey(0);*//*
    processed = thresholdImage;*/
//cout<<"*********"<<endl;



}

extern "C"
JNIEXPORT void JNICALL
Java_com_cadre_ocr_Trainer_Train(JNIEnv *env, jclass type, jlong mat, jlong done, jlong digit) {

    // TODO Training my algorithm
    const int MIN_CONTOUR_AREA = 200;
    const int RESIZED_IMAGE_WIDTH = 20;
    const int RESIZED_IMAGE_HEIGHT = 30;


    std::vector<ContourWithData> allContoursWithData;
    std::vector<ContourWithData> validContoursWithData;


    Mat &image = *(Mat *) mat;
    Mat &processed = *(Mat *) done;
//    Mat &lastOne = *(Mat *) done;
    int number = (int) digit;

    cv::Mat imgTrainingNumbers;
    cv::Mat imgGrayscale;
    cv::Mat imgBlurred;
    cv::Mat imgThresh;
    cv::Mat imgThreshCopy;

    std::vector<std::vector<cv::Point> > ptContours;
    std::vector<cv::Vec4i> v4iHierarchy;

    cv::Mat matClassificationInts;
    cv::Mat oldMatClassificationInts;
    Mat src_gray;
    cv::Mat matTrainingImagesAsFlattenedFloats;
    cv::Mat oldmatTrainingImagesAsFlattenedFloats;

    std::vector<int> intValidChars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    imgTrainingNumbers = image;

    int thresh = 100;
    int max_thresh = 255;
    RNG rng(12345);

    setInput(image);

    /// Convert image to gray and blur it
    cvtColor(image, src_gray, CV_BGR2GRAY);
    blur(src_gray, src_gray, Size(3, 3));


    if (imgTrainingNumbers.empty()) {
//        std::cout << "error: image not read from file\n\n";
        __android_log_print(ANDROID_LOG_ERROR, "Number read is:", "%s",
                            "Image Cannot be read from file");

        exit(1);
    }


    Mat threshold_output;
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;

//    TODO Setting up initial variables



//   TODO Passing path to classifications file
//    cv::FileStorage fileStorage

    cv::FileStorage fClassifications("/storage/emulated/0/EneoCV/classificationsData.xml",
                                     cv::FileStorage::READ);

    if (fClassifications.isOpened() == false) {
//        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open classifications:", "%s", "");

    }


    cv::FileStorage fTrainingImages("/storage/emulated/0/EneoCV/imagesData.xml",
                                    cv::FileStorage::READ);

    if (fTrainingImages.isOpened() == false) {
        //        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open images:", "%s", "");
    }

    if (fClassifications.isOpened() == true) {
        fClassifications["classifications"] >> oldMatClassificationInts;
        matClassificationInts.push_back(oldMatClassificationInts);
        fClassifications.release();
    }

    if (fTrainingImages.isOpened() == true) {
        fTrainingImages["images"] >> oldmatTrainingImagesAsFlattenedFloats;
        matTrainingImagesAsFlattenedFloats.push_back(oldmatTrainingImagesAsFlattenedFloats);
        fTrainingImages.release();
    }


//    cv::Ptr<cv::ml::KNearest>  kNearest(cv::ml::KNearest::create());
//    TODO not required
    /* Ptr<ml::KNearest> kNearest = ml::KNearest::create();

     kNearest->train(matTrainingImagesAsFlattenedFloats, cv::ml::ROW_SAMPLE, matClassificationInts);
 */

//    TODO Ending initial variables section

    /// Detect edges using Threshold
    threshold(src_gray, threshold_output, thresh, 255, THRESH_BINARY);
    /// Find contours
    findContours(threshold_output, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE,
                 Point(0, 0));

    /// Approximate contours to polygons + get bounding rects and circles
    vector<vector<Point> > contours_poly(contours.size());
    vector<Rect> boundRect(contours.size());
    vector<Point2f> center(contours.size());
    vector<float> radius(contours.size());


//    TODO Filtering contours

    for (int i = 0; i < contours.size(); i++) {
        ContourWithData contourWithData;
        if (contours[i].size() > 300 && contours[i].size() < 1000) {
            contourWithData.ptContour = contours[i];
            approxPolyDP(Mat(contours[i]), contours_poly[i], 3, true);
            boundRect[i] = boundingRect(Mat(contours_poly[i]));

            contourWithData.boundingRect = boundRect[i];
            contourWithData.fltArea = cv::contourArea(contourWithData.ptContour);
            allContoursWithData.push_back(contourWithData);
            __android_log_print(ANDROID_LOG_ERROR, "Contour Size:", "%d", contours[i].size());

        } else {
            __android_log_print(ANDROID_LOG_ERROR, "Contour Size Ignored:", "%d",
                                contours[i].size());
        }


        /*approxPolyDP(Mat(contours[i]), contours_poly[i], 3, true);
        boundRect[i] = boundingRect(Mat(contours_poly[i]));

        contourWithData.boundingRect = boundRect[i];
        contourWithData.fltArea = cv::contourArea(contourWithData.ptContour);
        allContoursWithData.push_back(contourWithData);*/
    }

    for (int i = 0; i < allContoursWithData.size(); i++) {
        if (allContoursWithData[i].checkIfContourIsValid()) {
            validContoursWithData.push_back(allContoursWithData[i]);
        }
    }

    std::sort(validContoursWithData.begin(), validContoursWithData.end(),
              ContourWithData::sortByBoundingRectXPosition);

    std::string strFinalString;
    Mat drawing = Mat::zeros(threshold_output.size(), CV_8UC3);

    __android_log_print(ANDROID_LOG_ERROR, "valid", "%d", validContoursWithData.size());

    for (int i = 0; i < validContoursWithData.size(); i++) {

        Scalar color = Scalar(rng.uniform(0, 255), rng.uniform(0, 255), rng.uniform(0, 255));
        drawContours(drawing, contours_poly, i, color, 1, 8, vector<Vec4i>(), 0, Point());
        cv::rectangle(_img,
                      validContoursWithData[i].boundingRect,
                      cv::Scalar(0, 255, 0),
                      2);
        cv::Mat matROI = threshold_output(validContoursWithData[i].boundingRect);
        cv::Mat matROIResized;
        cv::resize(matROI, matROIResized, cv::Size(RESIZED_IMAGE_WIDTH, RESIZED_IMAGE_HEIGHT));
        cv::Mat matROIFloat;
        matROIResized.convertTo(matROIFloat, CV_32FC1);
        cv::Mat matROIFlattenedFloat = matROIFloat.reshape(1, 1);
        cv::Mat matCurrentChar(0, 0, CV_32F);

//        Will not work
        /*cv::imshow("matROIResized", matROIResized);
        cv::imshow("imgTrainingNumbers", imgTrainingNumbers);*/

        int intChar = number;
//                cv::waitKey(0);

        /*if (intChar == 27) {
            exit(1);
        }
        else */
        if (std::find(intValidChars.begin(), intValidChars.end(), intChar) !=
            intValidChars.end()) {

            matClassificationInts.push_back(intChar);
            __android_log_print(ANDROID_LOG_ERROR, "Number stored is", "%d", intChar);


            cv::Mat matImageFloat;
            matROIResized.convertTo(matImageFloat, CV_32FC1);

            cv::Mat matImageFlattenedFloat = matImageFloat.reshape(1, 1);

            matTrainingImagesAsFlattenedFloats.push_back(matImageFlattenedFloat);
        }

        /* float fltCurrentChar = (float) matCurrentChar.at<float>(0, 0);
         strFinalString = strFinalString + char(int(fltCurrentChar));*/
    }

    processed = _img;
//    TODO Filtering Contours end
    /* ptContours=contours;

     for (int i = 0; i < ptContours.size(); i++) {
         if (cv::contourArea(ptContours[i]) > MIN_CONTOUR_AREA) {
             cv::Rect boundingRect = cv::boundingRect(ptContours[i]);

             cv::rectangle(imgTrainingNumbers, boundingRect, cv::Scalar(0, 0, 255), 2);

             cv::Mat matROI = imgThresh(boundingRect);

             cv::Mat matROIResized;
             cv::resize(matROI, matROIResized, cv::Size(RESIZED_IMAGE_WIDTH, RESIZED_IMAGE_HEIGHT));

             cv::imshow("matROI", matROI);
             cv::imshow("matROIResized", matROIResized);
             cv::imshow("imgTrainingNumbers", imgTrainingNumbers);

             int intChar = cv::waitKey(0);

             if (intChar == 27) {
                 exit(1);
             }
             else if (std::find(intValidChars.begin(), intValidChars.end(), intChar) != intValidChars.end()) {

                 matClassificationInts.push_back(intChar);

                 cv::Mat matImageFloat;
                 matROIResized.convertTo(matImageFloat, CV_32FC1);

                 cv::Mat matImageFlattenedFloat = matImageFloat.reshape(1, 1);

                 matTrainingImagesAsFlattenedFloats.push_back(matImageFlattenedFloat);
             }
         }
     }
 */
//    std::cout << "Training complete\n\n";
    __android_log_print(ANDROID_LOG_ERROR, "Training complete", "%s", "complete");

    cv::FileStorage fsClassifications("/storage/emulated/0/EneoCV/classificationsData.xml",
                                      cv::FileStorage::WRITE);

    if (fsClassifications.isOpened() == false) {
        __android_log_print(ANDROID_LOG_ERROR, "Training failed", "%s",
                            "error, unable to open training classifications file, exiting program\n\n");

        exit(1);
    }

    fsClassifications << "classifications" << matClassificationInts;
    fsClassifications.release();

    cv::FileStorage fsTrainingImages("/storage/emulated/0/EneoCV/imagesData.xml",
                                     cv::FileStorage::WRITE);

    if (fsTrainingImages.isOpened() == false) {
        __android_log_print(ANDROID_LOG_ERROR, "Training failed", "%s",
                            "error, unable to open training images file, exiting program\n\n");

        exit(1);
    }

    fsTrainingImages << "images" << matTrainingImagesAsFlattenedFloats;
    fsTrainingImages.release();

//    exit(1);

}


extern "C"
JNIEXPORT void JNICALL
Java_com_cadre_ocr_Trainer_Traindigits(JNIEnv *env, jclass type, jlong mat, jlong done,
                                       jintArray digit) {

// TODO Training my algorithm
    const int MIN_CONTOUR_AREA = 200;
    const int RESIZED_IMAGE_WIDTH = 20;
    const int RESIZED_IMAGE_HEIGHT = 30;


    std::vector<ContourWithData> allContoursWithData;
    std::vector<ContourWithData> validContoursWithData;


    Mat &image = *(Mat *) mat;
    Mat &processed = *(Mat *) done;
//    Mat &lastOne = *(Mat *) done;
//    int number = (int) digit;

    cv::Mat imgTrainingNumbers;
    cv::Mat imgGrayscale;
    cv::Mat imgBlurred;
    cv::Mat imgThresh;
    cv::Mat imgThreshCopy;

    std::vector<std::vector<cv::Point> > ptContours;
    std::vector<cv::Vec4i> v4iHierarchy;

    cv::Mat matClassificationInts;
    cv::Mat oldMatClassificationInts;
    Mat src_gray;
    cv::Mat matTrainingImagesAsFlattenedFloats;
    cv::Mat oldmatTrainingImagesAsFlattenedFloats;

    std::vector<int> intValidChars = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    imgTrainingNumbers = image;

    int thresh = 100;
    int max_thresh = 255;
    RNG rng(12345);

   /* int n, y = 0;

    y = number;
    while (y != 0) {
        n += 1;
        y /= 10;
    }

//printing separated digits
    int i;
    for (i = ceil(pow(10, (n - 1))); i != 0; i /= 10) {
        printf("%d  ", (number / i) % 10);
    }*/


    // initializations, declarations, etc
    int *c_array;
    jint i = 0;
    jboolean isCopy;

    jsize len = env->GetArrayLength(digit);

    // get a pointer to the array &isCopy
    c_array =  env->GetIntArrayElements(digit, 0);

    __android_log_print(ANDROID_LOG_ERROR, "Number 0 read is:", "%d",c_array[0]);

    // do some exception checking
   /* if (c_array == NULL) {
        return -1; *//* exception occurred *//*
    }*/
//    (*env)->GetArrayLength(env, digit);
    // do stuff to the array
//    TODO I don't need looping through this array
    /*for (i=0; i<len; i++) {
        c_array[i] =   digit[i];
    }*/

    for (i=0; i<len; i++) {
        __android_log_print(ANDROID_LOG_ERROR, "Number read is:", "%d",c_array[i] );
    }


    setInput(image);

/// Convert image to gray and blur it
    cvtColor(image, src_gray, CV_BGR2GRAY);
    blur(src_gray, src_gray, Size(3, 3));


    if (imgTrainingNumbers.empty()) {
//        std::cout << "error: image not read from file\n\n";
        __android_log_print(ANDROID_LOG_ERROR, "Number read is:", "%s",
                            "Image Cannot be read from file");

        exit(1);
    }


    Mat threshold_output;
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;

//    TODO Setting up initial variables



//   TODO Passing path to classifications file
//    cv::FileStorage fileStorage

    cv::FileStorage fClassifications("/storage/emulated/0/EneoCV/classificationsData.xml",
                                     cv::FileStorage::READ);

    if (fClassifications.isOpened() == false) {
//        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open classifications:", "%s", "");

    }


    cv::FileStorage fTrainingImages("/storage/emulated/0/EneoCV/imagesData.xml",
                                    cv::FileStorage::READ);

    if (fTrainingImages.isOpened() == false) {
//        TODO Log failure
        __android_log_print(ANDROID_LOG_ERROR, "failed to open images:", "%s", "");
    }

    if (fClassifications.isOpened() == true) {
        fClassifications["classifications"] >> oldMatClassificationInts;
        matClassificationInts.push_back(oldMatClassificationInts);
        fClassifications.release();
    }

    if (fTrainingImages.isOpened() == true) {
        fTrainingImages["images"] >> oldmatTrainingImagesAsFlattenedFloats;
        matTrainingImagesAsFlattenedFloats.push_back(oldmatTrainingImagesAsFlattenedFloats);
        fTrainingImages.release();
    }


//    cv::Ptr<cv::ml::KNearest>  kNearest(cv::ml::KNearest::create());
//    TODO not required
/* Ptr<ml::KNearest> kNearest = ml::KNearest::create();

 kNearest->train(matTrainingImagesAsFlattenedFloats, cv::ml::ROW_SAMPLE, matClassificationInts);
*/

//    TODO Ending initial variables section

/// Detect edges using Threshold
    threshold(src_gray, threshold_output, thresh, 255, THRESH_BINARY);
/// Find contours
    findContours(threshold_output, contours, hierarchy, CV_RETR_TREE, CV_CHAIN_APPROX_SIMPLE,
                 Point(0, 0));

/// Approximate contours to polygons + get bounding rects and circles
    vector<vector<Point> > contours_poly(contours.size());
    vector<Rect> boundRect(contours.size());
    vector<Point2f> center(contours.size());
    vector<float> radius(contours.size());


//    TODO Filtering contours

    for (int i = 0; i < contours.size(); i++) {
        ContourWithData contourWithData;
        if (contours[i].size() > 300 && contours[i].size() < 1000) {
            contourWithData.ptContour = contours[i];
            approxPolyDP(Mat(contours[i]), contours_poly[i], 3, true);
            boundRect[i] = boundingRect(Mat(contours_poly[i]));

            contourWithData.boundingRect = boundRect[i];
            contourWithData.fltArea = cv::contourArea(contourWithData.ptContour);
            allContoursWithData.push_back(contourWithData);
            __android_log_print(ANDROID_LOG_ERROR, "Contour Size:", "%d", contours[i].size());

        } else {
            __android_log_print(ANDROID_LOG_ERROR, "Contour Size Ignored:", "%d",
                                contours[i].size());
        }


/*approxPolyDP(Mat(contours[i]), contours_poly[i], 3, true);
boundRect[i] = boundingRect(Mat(contours_poly[i]));

contourWithData.boundingRect = boundRect[i];
contourWithData.fltArea = cv::contourArea(contourWithData.ptContour);
allContoursWithData.push_back(contourWithData);*/
    }

    for (int i = 0; i < allContoursWithData.size(); i++) {
        if (allContoursWithData[i].checkIfContourIsValid()) {
            validContoursWithData.push_back(allContoursWithData[i]);
        }
    }

    std::sort(validContoursWithData.begin(), validContoursWithData.end(),
              ContourWithData::sortByBoundingRectXPosition);

    std::string strFinalString;
    Mat drawing = Mat::zeros(threshold_output.size(), CV_8UC3);

    __android_log_print(ANDROID_LOG_ERROR, "valid SIZE", "%d", validContoursWithData.size());
    __android_log_print(ANDROID_LOG_ERROR, "Number array SIZE", "%d",len);


    for (int i = 0; i < validContoursWithData.size(); i++) {

        Scalar color = Scalar(rng.uniform(0, 255), rng.uniform(0, 255), rng.uniform(0, 255));
        drawContours(drawing, contours_poly, i, color, 1, 8, vector<Vec4i>(), 0, Point());
        cv::rectangle(_img,
                      validContoursWithData[i].boundingRect,
                      cv::Scalar(0, 255, 0),
                      2);
        cv::Mat matROI = threshold_output(validContoursWithData[i].boundingRect);
        cv::Mat matROIResized;
        cv::resize(matROI, matROIResized, cv::Size(RESIZED_IMAGE_WIDTH, RESIZED_IMAGE_HEIGHT));
        cv::Mat matROIFloat;
        matROIResized.convertTo(matROIFloat, CV_32FC1);
        cv::Mat matROIFlattenedFloat = matROIFloat.reshape(1, 1);
        cv::Mat matCurrentChar(0, 0, CV_32F);

//        Will not work
/*cv::imshow("matROIResized", matROIResized);
cv::imshow("imgTrainingNumbers", imgTrainingNumbers);*/

        int intChar = c_array[i];
//                cv::waitKey(0);

/*if (intChar == 27) {
    exit(1);
}
else */
        if (std::find(intValidChars.begin(), intValidChars.end(), intChar) !=
            intValidChars.end()) {

            matClassificationInts.push_back(intChar);
            __android_log_print(ANDROID_LOG_ERROR, "Number stored is", "%d", intChar);


            cv::Mat matImageFloat;
            matROIResized.convertTo(matImageFloat, CV_32FC1);

            cv::Mat matImageFlattenedFloat = matImageFloat.reshape(1, 1);

            matTrainingImagesAsFlattenedFloats.push_back(matImageFlattenedFloat);
        }

/* float fltCurrentChar = (float) matCurrentChar.at<float>(0, 0);
 strFinalString = strFinalString + char(int(fltCurrentChar));*/
    }

    processed = _img;
//    TODO Filtering Contours end
/* ptContours=contours;

 for (int i = 0; i < ptContours.size(); i++) {
     if (cv::contourArea(ptContours[i]) > MIN_CONTOUR_AREA) {
         cv::Rect boundingRect = cv::boundingRect(ptContours[i]);

         cv::rectangle(imgTrainingNumbers, boundingRect, cv::Scalar(0, 0, 255), 2);

         cv::Mat matROI = imgThresh(boundingRect);

         cv::Mat matROIResized;
         cv::resize(matROI, matROIResized, cv::Size(RESIZED_IMAGE_WIDTH, RESIZED_IMAGE_HEIGHT));

         cv::imshow("matROI", matROI);
         cv::imshow("matROIResized", matROIResized);
         cv::imshow("imgTrainingNumbers", imgTrainingNumbers);

         int intChar = cv::waitKey(0);

         if (intChar == 27) {
             exit(1);
         }
         else if (std::find(intValidChars.begin(), intValidChars.end(), intChar) != intValidChars.end()) {

             matClassificationInts.push_back(intChar);

             cv::Mat matImageFloat;
             matROIResized.convertTo(matImageFloat, CV_32FC1);

             cv::Mat matImageFlattenedFloat = matImageFloat.reshape(1, 1);

             matTrainingImagesAsFlattenedFloats.push_back(matImageFlattenedFloat);
         }
     }
 }
*/
//    std::cout << "Training complete\n\n";
    __android_log_print(ANDROID_LOG_ERROR, "Training complete", "%s", "complete");

    cv::FileStorage fsClassifications("/storage/emulated/0/EneoCV/classificationsData.xml",
                                      cv::FileStorage::WRITE);

    if (fsClassifications.isOpened() == false) {
        __android_log_print(ANDROID_LOG_ERROR, "Training failed", "%s",
                            "error, unable to open training classifications file, exiting program\n\n");

        exit(1);
    }

    fsClassifications << "classifications" << matClassificationInts;
    fsClassifications.release();

    cv::FileStorage fsTrainingImages("/storage/emulated/0/EneoCV/imagesData.xml",
                                     cv::FileStorage::WRITE);

    if (fsTrainingImages.isOpened() == false) {
        __android_log_print(ANDROID_LOG_ERROR, "Training failed", "%s",
                            "error, unable to open training images file, exiting program\n\n");

        exit(1);
    }

    fsTrainingImages << "images" << matTrainingImagesAsFlattenedFloats;
    fsTrainingImages.release();
    // release the memory so java can have it again
    env->ReleaseIntArrayElements(digit, c_array, 0);


//    exit(1);

}

