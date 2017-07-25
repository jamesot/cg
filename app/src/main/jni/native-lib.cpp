//
// Created by Stephine Osoro on 08/11/2016.
//

#include <opencv2/core/core.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/ml/ml.hpp>
#include <opencv2/ml.hpp>
using namespace std;
using namespace cv;
bool sort_by_x(const vector<Point> &ca, const vector<Point> &cb);
int main()
{
    Mat test_data;
    Rect bounding_rect;
    Mat inputImage,grayImage,blurredImage,thresholdImage,contourImage,regionOfInterest;
    inputImage= imread("/root/Desktop/Kirti/Digit_Recognition_1/test_image/b_test.jpg",1);
//inputImage= imread("/root/Desktop/test_ocr/index.jpg",1);
    cvtColor(inputImage, grayImage, CV_BGR2GRAY);
    threshold(grayImage, thresholdImage, 90, 255, CV_THRESH_OTSU|CV_THRESH_BINARY_INV);
    vector<vector<Point> > contours;
    vector<Vec4i> hierarchy;
    findContours( thresholdImage, contours, hierarchy, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_TC89_KCOS, Point(0, 0) );
    vector<vector<Point> > contours_poly( contours.size() );
    vector<Rect> boundRect( contours.size() );
    int kirti=contours.size();
    Mat ROI[kirti];
    sort(contours.begin(), contours.end(), sort_by_x);
//cout<< kirti<<endl;
//cout<<"****************"<<endl;
    for( size_t i = 0; i < contours.size(); i++ )
    {
        approxPolyDP( Mat(contours[i]), contours_poly[i], 5, false );
        boundRect[i] = boundingRect( Mat(contours_poly[i]) );
        rectangle( grayImage, boundRect[i].tl(), boundRect[i].br(), Scalar(0,255,0),1, 8,0 );
    }
//tl()= top left corner br()= bottom right corner
    vector<int> number;
    vector<int>::iterator it;
    int predicted;
    string name;
    for(int i=0;i<kirti;i++)
    {
        if(boundRect[i].width>50 && boundRect[i].height>50)
        {
            ROI[i]= grayImage(boundRect[i]);
            name= format("%d.jpg",i);
            Mat result;
            result=ROI[i].clone();
            resize(result, result, Size(28,28));
            threshold(result, result, 100, 255, CV_THRESH_BINARY_INV);
            imwrite(name,result);
            imshow("sequence",result);
            waitKey(1000);
            cv::ml::SVM svm;
            svm.load("classifier.xml");
            cv::Mat testMat = result.clone().reshape(1,1);
            testMat.convertTo(testMat, CV_32F);
            predicted = svm.predict(testMat);
            cout << endl << "Recognizing following number -> " << predicted<< endl << endl;
            number.push_back(predicted);
        }
    }
    for(it=number.begin();it<number.end();it++)
    {
        cout<<*it;
    }
//cout<<number;
    namedWindow("input", CV_WINDOW_FREERATIO);
    imshow("input", grayImage);
    waitKey(0);
//cout<<"*********"<<endl;
}
bool sort_by_x(const vector<Point> &ca, const vector<Point> &cb)
{
    return boundingRect(ca).x < boundingRect(cb).x;
}