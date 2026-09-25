#include <jni.h>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <net.h>
#include <algorithm>
#include <cmath>
#include <memory>
#include <vector>
namespace {
struct Engine {
    ncnn::Net det, rec;
    Engine() {
        for (auto* net : {&det, &rec}) {
            net->opt.num_threads=2;
            net->opt.use_vulkan_compute=false;
            net->opt.use_fp16_arithmetic=false;
            net->opt.use_fp16_storage=false;
            net->opt.use_packing_layout=false;
        }
    }
};
void fail(JNIEnv* env,const char* message) {
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),message);
}
ncnn::Mat pixels(JNIEnv* env,jobject bitmap,int w,int h) {
    AndroidBitmapInfo info{};
    if(AndroidBitmap_getInfo(env,bitmap,&info)!=ANDROID_BITMAP_RESULT_SUCCESS ||
       info.format!=ANDROID_BITMAP_FORMAT_RGBA_8888 || w<1 || h<1)return {};
    void* data=nullptr;
    if(AndroidBitmap_lockPixels(env,bitmap,&data)!=ANDROID_BITMAP_RESULT_SUCCESS)return {};
    ncnn::Mat result=ncnn::Mat::from_pixels_resize(static_cast<unsigned char*>(data),
        ncnn::Mat::PIXEL_RGBA2BGR,info.width,info.height,info.stride,w,h);
    AndroidBitmap_unlockPixels(env,bitmap);
    return result;
}
jfloatArray array(JNIEnv* env,const std::vector<float>& values) {
    jfloatArray result=env->NewFloatArray(static_cast<jsize>(values.size()));
    if(result)env->SetFloatArrayRegion(result,0,static_cast<jsize>(values.size()),values.data());
    return result;
}
}
extern "C" JNIEXPORT jlong JNICALL
Java_au_id_pointandyshoot_bookscanner_PpOcrNative_open(JNIEnv* env,jclass,jobject assets) {
    auto e=std::make_unique<Engine>();
    AAssetManager* manager=AAssetManager_fromJava(env,assets);
    if(!manager || e->det.load_param(manager,"ppocrv4/det.param") || e->det.load_model(manager,"ppocrv4/det.bin") ||
       e->rec.load_param(manager,"ppocrv4/rec.param") || e->rec.load_model(manager,"ppocrv4/rec.bin")) {
        fail(env,"Bundled PP-OCRv4 models could not load");return 0;
    }
    return reinterpret_cast<jlong>(e.release());
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_au_id_pointandyshoot_bookscanner_PpOcrNative_detect(JNIEnv* env,jclass,jlong handle,jobject bitmap,jint w,jint h) {
    if(!handle || w<32 || h<32 || w>1536 || h>1536 || w%32 || h%32) {
        fail(env,"Invalid detector input");return nullptr;
    }
    ncnn::Mat input=pixels(env,bitmap,w,h);
    if(input.empty()){fail(env,"Invalid detector bitmap");return nullptr;}
    const float mean[]={123.675f,116.28f,103.53f},norm[]={1.f/58.395f,1.f/57.12f,1.f/57.375f};
    input.substract_mean_normalize(mean,norm);
    auto ex=reinterpret_cast<Engine*>(handle)->det.create_extractor();ncnn::Mat out;
    if(ex.input("input",input) || ex.extract("output",out) || out.empty() || out.c!=1) {
        fail(env,"PP-OCRv4 detector inference failed");return nullptr;
    }
    std::vector<float> result(2+out.w*out.h);result[0]=out.w;result[1]=out.h;
    const float* data=out.channel(0);std::copy(data,data+out.w*out.h,result.begin()+2);
    return array(env,result);
}
extern "C" JNIEXPORT jfloatArray JNICALL
Java_au_id_pointandyshoot_bookscanner_PpOcrNative_recognise(JNIEnv* env,jclass,jlong handle,jobject bitmap) {
    AndroidBitmapInfo info{};
    if(!handle || AndroidBitmap_getInfo(env,bitmap,&info)!=ANDROID_BITMAP_RESULT_SUCCESS || !info.height) {
        fail(env,"Invalid recogniser input");return nullptr;
    }
    int width=std::clamp(static_cast<int>(std::ceil(info.width*48.f/info.height)),16,1536);
    ncnn::Mat input=pixels(env,bitmap,width,48);
    if(input.empty()){fail(env,"Invalid recogniser bitmap");return nullptr;}
    const float mean[]={127.5f,127.5f,127.5f},norm[]={1.f/127.5f,1.f/127.5f,1.f/127.5f};
    input.substract_mean_normalize(mean,norm);
    auto ex=reinterpret_cast<Engine*>(handle)->rec.create_extractor();ncnn::Mat out;
    if(ex.input("input",input) || ex.extract("output",out) || out.empty() || out.w!=6625 || out.dims!=2) {
        fail(env,"Unexpected PP-OCRv4 recognition output");return nullptr;
    }
    std::vector<float> result(out.h*2);
    for(int t=0;t<out.h;++t){const float* row=out.row(t);auto best=std::max_element(row,row+out.w);result[t*2]=best-row;result[t*2+1]=*best;}
    return array(env,result);
}
extern "C" JNIEXPORT void JNICALL
Java_au_id_pointandyshoot_bookscanner_PpOcrNative_close(JNIEnv*,jclass,jlong handle) {delete reinterpret_cast<Engine*>(handle);}
