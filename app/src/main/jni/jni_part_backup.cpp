#include <jni.h>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <vector>
#include <android/log.h>

using namespace std;
using namespace cv;
Mat _img;
std::vector<cv::Mat> _digits;
void setInput(cv::Mat &img);

cv::Mat process();

extern "C" {
JNIEXPORT void JNICALL Java_com_eneo_ocr_Tutorial2Activity_FindFeatures(JNIEnv *, jobject,
                                                                        jlong addrGray,
                                                                        jlong addrRgba);

JNIEXPORT void JNICALL Java_com_eneo_ocr_Tutorial2Activity_FindFeatures(JNIEnv *, jobject,
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

JNIEXPORT void JNICALL Java_com_eneo_ocr_Tutorial2Activity_Process(JNIEnv *env, jobject,
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
        __android_log_print(ANDROID_LOG_ERROR,"failed to detect skew","%s deg", " f");

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

/**
 * Find and isolate the digits of the counter,
 */
void findCounterDigits(Mat mat) {

    // edge image
    cv::Mat edges = mat;

    cv::Mat img_ret = edges.clone();

    // find contours in whole image
    std::vector<std::vector<cv::Point> > contours, filteredContours;
    std::vector<cv::Rect> boundingBoxes;
    cv::findContours(edges, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);

    // filter contours by bounding rect size
    filterContours(contours, boundingBoxes, filteredContours);

// TODO   rlog << log4cpp::Priority::INFO << "number of filtered contours: " << filteredContours.size();
    __android_log_print(ANDROID_LOG_ERROR, "number of filtered contours:", "%d", filteredContours.size());

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
    __android_log_print(ANDROID_LOG_ERROR, "max number of alignedBoxes:", "%d", alignedBoundingBoxes.size());



    // sort bounding boxes from left to right
    std::sort(alignedBoundingBoxes.begin(), alignedBoundingBoxes.end(), sortRectByX());

    if (true) {
        // draw contours
        cv::Mat cont = cv::Mat::zeros(edges.rows, edges.cols, CV_8UC1);
        cv::drawContours(cont, filteredContours, -1, cv::Scalar(255));
//      TODO  cv::imshow("contours", cont);
        _img=cont;
    }

    // TODO cut out found rectangles from edged image
    /*for (int i = 0; i < alignedBoundingBoxes.size(); ++i) {
        cv::Rect roi = alignedBoundingBoxes[i];
        _digits.push_back(img_ret(roi));
        cv::rectangle(_img, roi, cv::Scalar(0, 255, 0), 2);
    }*/
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
    rotate((double)skew_deg);

    // find and isolate counter digits
//    findCounterDigits(_img);
//TODO RETURN THE IMAGE HERE
    /*if (_debugWindow) {
        showImage();
    }*/
    return _img;
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


