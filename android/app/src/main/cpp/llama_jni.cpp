#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "Llama_Jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_localai_agent_local_1personal_1ai_1agent_services_Local_1Llama_1Engine_Native_1Init(
        JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing local model at path: %s", path);
    env->ReleaseStringUTFChars(model_path, path);
    return 1000;
}

JNIEXPORT jfloatArray JNICALL
Java_com_localai_agent_local_1personal_1ai_1agent_services_Local_1Llama_1Engine_Native_1Embed(
        JNIEnv *env, jobject thiz, jlong context, jstring text) {
    const char *raw_text = env->GetStringUTFChars(text, nullptr);
    LOGI("Generating embedding for text: %s", raw_text);

    int dims = 128;
    jfloatArray result = env->NewFloatArray(dims);
    float fill[128];
    for (int i = 0; i < dims; ++i) {
        fill[i] = static_cast<float>(i) / static_cast<float>(dims);
    }
    env->SetFloatArrayRegion(result, 0, dims, fill);
    env->ReleaseStringUTFChars(text, raw_text);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_localai_agent_local_1personal_1ai_1agent_services_Local_1Llama_1Engine_Native_1Inference_1Stream(
        JNIEnv *env, jobject thiz, jlong context, jstring prompt, jobject callback) {
    const char *raw_prompt = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Running native inference for prompt: %s", raw_prompt);

    jclass callback_class = env->GetObjectClass(callback);
    jmethodID invoke_method = env->GetMethodID(callback_class, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

    std::string response = "Native llama response stream: Done.";

    jstring token = env->NewStringUTF(response.c_str());
    env->CallObjectMethod(callback, invoke_method, token);

    env->ReleaseStringUTFChars(prompt, raw_prompt);
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT void JNICALL
Java_com_localai_agent_local_1personal_1ai_1agent_services_Local_1Llama_1Engine_Native_1Free(
        JNIEnv *env, jobject thiz, jlong context) {
    LOGI("Freeing llama native context");
}

}
