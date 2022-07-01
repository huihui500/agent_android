#include <jni.h>
#include <string>
#include <opencv2/imgproc/types_c.h>
#include "include/opencv2/wechat_qrcode.hpp"
#include "bitmap_utils.h"
#include "android/log.h"
#include <jni.h>


using namespace cv;
using namespace std;

#define LOG_TAG "System.out"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

Ptr<wechat_qrcode::WeChatQRCode> detector;

int color_class(Mat src, int x, int y, int w, int h)
{
    int redsum=0, yellowsum=0, blacksum=0;
    int r, g, b;
    int color_index=-1;
    for(int i=0; i<h; i++)
    {
        for(int j=0; j<w; j++)
        {
            r = src.at<Vec4b>(y+i, x+j)[0];
            g = src.at<Vec4b>(y+i, x+j)[1];
            b = src.at<Vec4b>(y+i, x+j)[2];
            if (r>1.5*g && r > 1.5*b && abs(g-b) < 30)
                redsum+=1;
            else if(abs(r-g)<30 && r > 2*b && g > 2*b)
                yellowsum+=1;
            else if(abs(r-b) < 20 && abs(r-g) < 20 && r < 70)
                blacksum+=1;
        }
    }
    std::cout << "r,y,b" << redsum << ", " << yellowsum << ", " << blacksum << std::endl;
    if (redsum > yellowsum && redsum > blacksum)
        color_index = 0;
    else if(yellowsum > redsum && yellowsum > blacksum)
        color_index = 1;
    else
        color_index = 2;
    return color_index;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_org_pytorch_demo_objectdetection_MainActivity_opBitmap(JNIEnv *env, jobject thiz, jobject bitmap, jobject argb8888) {
    // TODO: implement opBitmap()
    Mat srcMat;// rgba
    vector<Mat> vPoints;
    vector<int> color;

    bitmap2Mat(env, bitmap, &srcMat, true);
    if(srcMat.empty()){
        std::cout << "img empty: " << srcMat.empty() << std::endl;
        return env->NewStringUTF("empty Image");
    }
    vector<string> ret = detector->detectAndDecode(srcMat, vPoints);
    LOGI("decode over:");
    stringstream ss;
    ss << ret.size() << " ";
    for(int i=0; i<ret.size(); i++)
    {
        int x, y, xm, ym, w, h;
        x = min(min((int)vPoints[i].at<float>(0, 0), (int)vPoints[i].at<int>(1, 0)), (int)vPoints[i].at<int>(2, 0));
        y = min(min((int)vPoints[i].at<float>(0, 1), (int)vPoints[i].at<float>(1, 1)), (int)vPoints[i].at<float>(2, 1));

        xm = max(max((int)vPoints[i].at<float>(0, 0), (int)vPoints[i].at<float>(1, 0)), (int)vPoints[i].at<float>(2, 0));
        ym = max(max((int)vPoints[i].at<float>(0, 1), (int)vPoints[i].at<float>(1, 1)), (int)vPoints[i].at<float>(2, 1));

        h = ym - y;
        w = xm - x;

        int v = color_class(srcMat, x, y, w, h);
        color.push_back(v);
        ss << v << " " << ret[i] << " ";
    }
    string all_info = ss.str();
    if(ret.empty())
        return env->NewStringUTF("none");
    else
        return env->NewStringUTF(all_info.c_str());//使用dstMat创建一个Bitmap对象
}
extern "C"
JNIEXPORT jstring JNICALL
Java_org_pytorch_demo_objectdetection_MainActivity_initQr(JNIEnv *env, jobject thiz) {
    // TODO: implement initQr()
    try {
        detector = makePtr<wechat_qrcode::WeChatQRCode>(
                "/sdcard/qr_module/detect_prototxt",
                "/sdcard/qr_module/detect_caffemodel",
                "/sdcard/qr_module/sr_prototxt",
                "/sdcard/qr_module/sr_caffemodel");
    }
    catch (const std::exception& e)
    {
        std::cout << e.what() << std::endl;
        return env->NewStringUTF("fail load");
    }
    return env->NewStringUTF("sucess load");
}