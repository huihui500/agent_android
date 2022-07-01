#include <jni.h>
#include <string>
#include <opencv2/imgproc/types_c.h>
#include "include/opencv2/wechat_qrcode.hpp"
#include "bitmap_utils.h"
#include "android/log.h"
#include <jni.h>
#include <opencv2/dnn/all_layers.hpp>
#include <opencv2/dnn/dict.hpp>
#include <opencv2/dnn/dnn.hpp>
#include <opencv2/dnn/dnn.inl.hpp>
#include <opencv2/dnn/layer.details.hpp>
#include <opencv2/dnn/layer.hpp>
#include <opencv2/dnn/shape_utils.hpp>

using namespace cv;
using namespace std;

#define LOG_TAG "System.out"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)


extern "C"
JNIEXPORT jstring JNICALL
Java_org_pytorch_demo_objectdetection_MainActivity_opBitmap(JNIEnv *env, jobject thiz, jobject bitmap, jobject argb8888) {
    // TODO: implement opBitmap()
    Ptr<wechat_qrcode::WeChatQRCode> detector;
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

    Mat srcMat;
    Mat dstMat;
    bitmap2Mat(env, bitmap, &srcMat, true);
    bool flag = srcMat.empty();
    std::cout << "img empty: " << srcMat.empty() << std::endl;
    vector<string> ret = detector->detectAndDecode(srcMat);
//    cvtColor(srcMat, dstMat, CV_BGR2GRAY);//将图片的像素信息灰度化盛放在dstMat
    LOGI("decode over:");
//    return env->NewStringUTF("none");
    if(ret.empty())
        return env->NewStringUTF("none");
    else
        return env->NewStringUTF(ret[0].c_str());//使用dstMat创建一个Bitmap对象
}