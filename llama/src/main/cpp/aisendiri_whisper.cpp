// JNI bridge between the app and whisper.cpp (speech to text).
// Like the llama bridge, a context is used from one Kotlin thread; only abort() may be
// called from another thread.

#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <string>

#include "whisper.h"

#define TAG "AiSendiriWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

struct Session {
    whisper_context *ctx = nullptr;
    std::atomic<bool> abort{false};
};

void log_callback(ggml_log_level level, const char *text, void * /*user_data*/) {
    int prio = ANDROID_LOG_DEBUG;
    if (level == GGML_LOG_LEVEL_ERROR) prio = ANDROID_LOG_ERROR;
    else if (level == GGML_LOG_LEVEL_WARN) prio = ANDROID_LOG_WARN;
    else if (level == GGML_LOG_LEVEL_INFO) prio = ANDROID_LOG_INFO;
    __android_log_print(prio, TAG, "%s", text);
}

std::string to_string(JNIEnv *env, jstring s) {
    const char *chars = env->GetStringUTFChars(s, nullptr);
    std::string out(chars);
    env->ReleaseStringUTFChars(s, chars);
    return out;
}

std::string to_string(JNIEnv *env, jbyteArray bytes) {
    const jsize len = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(len), '\0');
    env->GetByteArrayRegion(bytes, 0, len, reinterpret_cast<jbyte *>(out.data()));
    return out;
}

jbyteArray to_bytes(JNIEnv *env, const std::string &s) {
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(s.size()));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    return arr;
}

Session *session(jlong handle) { return reinterpret_cast<Session *>(handle); }

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_aisendiri_llama_WhisperNative_load(JNIEnv *env, jobject, jstring path) {
    whisper_log_set(log_callback, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    const std::string model_path = to_string(env, path);
    whisper_context *ctx = whisper_init_from_file_with_params(model_path.c_str(), params);
    if (ctx == nullptr) {
        LOGE("could not load %s", model_path.c_str());
        return 0;
    }
    auto *s = new Session();
    s->ctx = ctx;
    return reinterpret_cast<jlong>(s);
}

extern "C" JNIEXPORT void JNICALL
Java_com_aisendiri_llama_WhisperNative_free(JNIEnv *, jobject, jlong handle) {
    Session *s = session(handle);
    if (s == nullptr) return;
    whisper_free(s->ctx);
    delete s;
}

extern "C" JNIEXPORT void JNICALL
Java_com_aisendiri_llama_WhisperNative_abort(JNIEnv *, jobject, jlong handle) {
    Session *s = session(handle);
    if (s != nullptr) s->abort = true;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_aisendiri_llama_WhisperNative_transcribe(
        JNIEnv *env, jobject, jlong handle, jfloatArray samples, jstring language,
        jint threads, jint audio_ctx, jbyteArray initial_prompt) {
    Session *s = session(handle);
    if (s == nullptr) return nullptr;
    s->abort = false;

    const std::string lang = to_string(env, language);
    const std::string prompt = initial_prompt != nullptr ? to_string(env, initial_prompt) : std::string();

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.language = lang.c_str();
    params.translate = false;
    params.no_context = true;
    params.no_timestamps = true;
    params.single_segment = false;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    params.suppress_nst = true;
    params.audio_ctx = audio_ctx;
    if (!prompt.empty()) params.initial_prompt = prompt.c_str();
    params.abort_callback = [](void *user_data) -> bool {
        return static_cast<Session *>(user_data)->abort.load();
    };
    params.abort_callback_user_data = s;

    const jsize n = env->GetArrayLength(samples);
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    const int rc = whisper_full(s->ctx, params, pcm, n);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    if (rc != 0 || s->abort) {
        LOGI("transcribe stopped: rc=%d abort=%d", rc, s->abort.load() ? 1 : 0);
        return nullptr;
    }

    std::string text;
    const int segments = whisper_full_n_segments(s->ctx);
    for (int i = 0; i < segments; ++i) text += whisper_full_get_segment_text(s->ctx, i);
    return to_bytes(env, text);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aisendiri_llama_WhisperNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(whisper_print_system_info());
}
