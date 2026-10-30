// JNI bridge between the app and llama.cpp.
// One Session holds one loaded AI model and its context. A Session is not thread-safe:
// the Kotlin side calls it from a single dedicated thread.
// Photos go through libmtmd: an optional image encoder ("mmproj") attached to the Session.

#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "llama.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#define TAG "AiSendiriLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

// Error codes returned to Kotlin by startCompletion. Keep in sync with LlamaNative.kt.
constexpr int ERR_TOKENIZE = -1;
constexpr int ERR_CONTEXT_FULL = -2;
constexpr int ERR_DECODE = -3;
constexpr int ERR_IMAGE = -4;

struct Session {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    llama_sampler *sampler = nullptr;
    mtmd_context *mtmd = nullptr;    // image encoder, when one is attached
    // What is in the KV cache, in order: text tokens, and for each image one pseudo-token
    // per image token derived from the image's id, so a cached image matches on the next turn.
    std::vector<llama_token> cached;
    llama_pos n_past = 0;            // next position; differs from cached.size() after M-RoPE images
    std::string pending_utf8;        // bytes of an incomplete UTF-8 character
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

// Kotlin strings arrive as modified UTF-8 through GetStringUTFChars, which breaks emoji.
// Passing text as byte arrays keeps it standard UTF-8 in both directions.
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

// Length of the longest prefix of s that ends on a complete UTF-8 character.
size_t complete_utf8_prefix(const std::string &s) {
    size_t i = s.size();
    size_t back = 0;
    while (i > 0 && back < 4) {
        const auto c = static_cast<unsigned char>(s[i - 1]);
        if ((c & 0xC0) != 0x80) {
            size_t need = 1;
            if ((c & 0xE0) == 0xC0) need = 2;
            else if ((c & 0xF0) == 0xE0) need = 3;
            else if ((c & 0xF8) == 0xF0) need = 4;
            return (back + 1 >= need) ? s.size() : i - 1;
        }
        --i;
        ++back;
    }
    return s.size();
}

std::string token_to_piece(const llama_vocab *vocab, llama_token token) {
    char buf[256];
    int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, false);
    if (n < 0) {
        std::string big(static_cast<size_t>(-n), '\0');
        n = llama_token_to_piece(vocab, token, big.data(), -n, 0, false);
        return n < 0 ? std::string() : big.substr(0, static_cast<size_t>(n));
    }
    return std::string(buf, static_cast<size_t>(n));
}

// Decodes text tokens at explicit positions, so they follow an image correctly even when
// the model counts image positions differently (M-RoPE). Logits are kept for the last token.
bool decode_tokens(Session *s, const llama_token *tokens, size_t count) {
    const auto n_batch = static_cast<size_t>(llama_n_batch(s->ctx));
    llama_batch batch = llama_batch_init(static_cast<int32_t>(n_batch), 0, 1);
    bool ok = true;
    for (size_t i = 0; i < count && ok; i += n_batch) {
        const size_t n = std::min(n_batch, count - i);
        batch.n_tokens = static_cast<int32_t>(n);
        for (size_t j = 0; j < n; j++) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = s->n_past + static_cast<llama_pos>(j);
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = (i + j + 1 == count);
        }
        ok = llama_decode(s->ctx, batch) == 0;
        if (ok) {
            s->cached.insert(s->cached.end(), tokens + i, tokens + i + n);
            s->n_past += static_cast<llama_pos>(n);
        }
    }
    llama_batch_free(batch);
    return ok;
}

void reset_cache(Session *s) {
    llama_memory_clear(llama_get_memory(s->ctx), true);
    s->cached.clear();
    s->n_past = 0;
}

// Drops everything in the KV cache after the first [keep] entries at position [pos].
void trim_cache(Session *s, size_t keep, llama_pos pos) {
    llama_memory_seq_rm(llama_get_memory(s->ctx), 0, pos, -1);
    s->cached.resize(keep);
    s->n_past = pos;
}

// Pseudo-token standing in for every token of one image in Session::cached.
llama_token image_marker_token(const char *id) {
    uint32_t h = 2166136261u; // FNV-1a
    for (const char *c = id ? id : ""; *c; c++) h = (h ^ static_cast<uint8_t>(*c)) * 16777619u;
    return -static_cast<llama_token>(h & 0x3fffffff) - 1; // always negative: never a real token
}

void build_sampler(Session *s, float temperature, float top_p, float min_p, float repeat_penalty, int seed) {
    if (s->sampler) llama_sampler_free(s->sampler);
    s->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (repeat_penalty > 1.0f) {
        llama_sampler_chain_add(s->sampler, llama_sampler_init_penalties(
                llama_vocab_n_tokens(s->vocab), 64, repeat_penalty, 0.0f, 0.0f));
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(s->sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_top_p(top_p, 1));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_min_p(min_p, 1));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(s->sampler, llama_sampler_init_dist((uint32_t) seed));
    }
}

// Gemma 4 has no built-in template in llama.cpp, so its turn format is written out here.
// The empty thought block asks for no thinking. Gemma-SEA-LION E2B still often writes notes first;
// the app hides them (ThoughtFilter).
std::string format_gemma4(const std::vector<std::string> &roles, const std::vector<std::string> &contents) {
    std::string out;
    for (size_t i = 0; i < roles.size(); i++) {
        const std::string role = roles[i] == "assistant" ? "model" : roles[i];
        out += "<|turn>" + role + "\n" + contents[i] + "<turn|>\n";
    }
    out += "<|turn>model\n<|channel>thought\n<channel|>";
    return out;
}

Session *session(jlong handle) { return reinterpret_cast<Session *>(handle); }

} // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_com_aisendiri_llama_LlamaNative_init(JNIEnv *env, jobject, jstring native_lib_dir) {
    llama_log_set(log_callback, nullptr);
    // Loads the CPU backend variant that best fits this phone's processor.
    ggml_backend_load_all_from_path(to_string(env, native_lib_dir).c_str());
    llama_backend_init();
}

JNIEXPORT jstring JNICALL
Java_com_aisendiri_llama_LlamaNative_systemInfo(JNIEnv *env, jobject) {
    std::string info;
    for (size_t i = 0; i < ggml_backend_dev_count(); i++) {
        auto *dev = ggml_backend_dev_get(i);
        info += std::string(ggml_backend_dev_name(dev)) + " (" + ggml_backend_dev_description(dev) + ")\n";
    }
    info += llama_print_system_info();
    return env->NewStringUTF(info.c_str());
}

JNIEXPORT jlong JNICALL
Java_com_aisendiri_llama_LlamaNative_load(JNIEnv *env, jobject, jstring path, jint n_ctx, jint n_threads,
                                          jint n_batch) {
    auto model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    llama_model *model = llama_model_load_from_file(to_string(env, path).c_str(), model_params);
    if (!model) {
        LOGE("Failed to load model");
        return 0;
    }

    auto ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(n_ctx);
    ctx_params.n_batch = static_cast<uint32_t>(n_batch);
    ctx_params.n_ubatch = static_cast<uint32_t>(n_batch);
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    ctx_params.no_perf = true;
    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Failed to create context");
        llama_model_free(model);
        return 0;
    }

    auto *s = new Session();
    s->model = model;
    s->ctx = ctx;
    s->vocab = llama_model_get_vocab(model);
    LOGI("Model loaded: ctx=%d threads=%d batch=%d", n_ctx, n_threads, n_batch);
    return reinterpret_cast<jlong>(s);
}

JNIEXPORT void JNICALL
Java_com_aisendiri_llama_LlamaNative_free(JNIEnv *, jobject, jlong handle) {
    Session *s = session(handle);
    if (!s) return;
    if (s->sampler) llama_sampler_free(s->sampler);
    if (s->mtmd) mtmd_free(s->mtmd);
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;
}

JNIEXPORT jstring JNICALL
Java_com_aisendiri_llama_LlamaNative_modelDescription(JNIEnv *env, jobject, jlong handle) {
    char buf[256];
    llama_model_desc(session(handle)->model, buf, sizeof(buf));
    return env->NewStringUTF(buf);
}

// Formats a conversation with the model's own chat template. Falls back to ChatML,
// which most small instruct models understand. Returns null if formatting fails.
JNIEXPORT jbyteArray JNICALL
Java_com_aisendiri_llama_LlamaNative_formatChat(JNIEnv *env, jobject, jlong handle, jobjectArray roles,
                                                jobjectArray contents) {
    Session *s = session(handle);
    const jsize n = env->GetArrayLength(roles);
    std::vector<std::string> role_strs, content_strs;
    role_strs.reserve(n);
    content_strs.reserve(n);
    for (jsize i = 0; i < n; i++) {
        auto r = static_cast<jstring>(env->GetObjectArrayElement(roles, i));
        auto c = static_cast<jbyteArray>(env->GetObjectArrayElement(contents, i));
        role_strs.push_back(to_string(env, r));
        content_strs.push_back(to_string(env, c));
        env->DeleteLocalRef(r);
        env->DeleteLocalRef(c);
    }
    std::vector<llama_chat_message> msgs;
    msgs.reserve(n);
    for (jsize i = 0; i < n; i++) msgs.push_back({role_strs[i].c_str(), content_strs[i].c_str()});

    const char *tmpl = llama_model_chat_template(s->model, nullptr);
    if (tmpl && std::strstr(tmpl, "<|turn>") != nullptr) {
        return to_bytes(env, format_gemma4(role_strs, content_strs));
    }
    size_t total = 0;
    for (auto &c : content_strs) total += c.size();
    std::vector<char> buf(total * 2 + 1024);
    int len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    if (len < 0 && tmpl != nullptr) {
        tmpl = "chatml";
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    }
    if (len < 0) return nullptr;
    if (static_cast<size_t>(len) > buf.size()) {
        buf.resize(static_cast<size_t>(len));
        len = llama_chat_apply_template(tmpl, msgs.data(), msgs.size(), true, buf.data(), (int32_t) buf.size());
    }
    return to_bytes(env, std::string(buf.data(), static_cast<size_t>(len)));
}

// Prepares a reply to the formatted prompt. Reuses the part of the KV cache that matches
// the previous turn, so long chats stay fast. Returns the prompt token count or an error code.
JNIEXPORT jint JNICALL
Java_com_aisendiri_llama_LlamaNative_startCompletion(JNIEnv *env, jobject, jlong handle, jbyteArray prompt,
                                                     jint reserve_tokens, jfloat temperature, jfloat top_p,
                                                     jfloat min_p, jfloat repeat_penalty, jint seed) {
    Session *s = session(handle);
    const std::string text = to_string(env, prompt);

    const int n_max = static_cast<int>(text.size()) + 16;
    std::vector<llama_token> tokens(static_cast<size_t>(n_max));
    const int n = llama_tokenize(s->vocab, text.c_str(), (int32_t) text.size(), tokens.data(), n_max, true, true);
    if (n < 0) return ERR_TOKENIZE;
    tokens.resize(static_cast<size_t>(n));

    const auto n_ctx = static_cast<int>(llama_n_ctx(s->ctx));
    if (n + reserve_tokens > n_ctx) return ERR_CONTEXT_FULL;

    size_t keep = 0;
    while (keep < s->cached.size() && keep < tokens.size() && s->cached[keep] == tokens[keep]) keep++;
    if (keep == tokens.size() && keep > 0) keep--; // always decode at least one token to get fresh logits
    // The kept part is all text, so its positions equal its indexes.
    trim_cache(s, keep, static_cast<llama_pos>(keep));
    s->pending_utf8.clear();

    if (!decode_tokens(s, tokens.data() + keep, tokens.size() - keep)) {
        reset_cache(s);
        return ERR_DECODE;
    }
    build_sampler(s, temperature, top_p, min_p, repeat_penalty, seed);
    return n;
}

// Attaches an image encoder (mmproj file) to a loaded model. Returns false if it can't be
// loaded or doesn't handle images.
JNIEXPORT jboolean JNICALL
Java_com_aisendiri_llama_LlamaNative_attachVision(JNIEnv *env, jobject, jlong handle, jstring mmproj_path,
                                                  jint n_threads, jint max_image_tokens) {
    Session *s = session(handle);
    if (s->mtmd) {
        mtmd_free(s->mtmd);
        s->mtmd = nullptr;
    }
    mtmd_helper_log_set(log_callback, nullptr);
    auto params = mtmd_context_params_default();
    params.use_gpu = false;
    params.n_threads = n_threads;
    params.print_timings = false;
    params.warmup = false;
    if (max_image_tokens > 0) params.image_max_tokens = max_image_tokens;
    mtmd_context *m = mtmd_init_from_file(to_string(env, mmproj_path).c_str(), s->model, params);
    if (!m || !mtmd_support_vision(m)) {
        LOGE("Failed to load image encoder");
        if (m) mtmd_free(m);
        return JNI_FALSE;
    }
    s->mtmd = m;
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_aisendiri_llama_LlamaNative_mediaMarker(JNIEnv *env, jobject) {
    return env->NewStringUTF(mtmd_default_marker());
}

// Like startCompletion, for a prompt with one media marker per image. Images are RGB bytes
// (3 per pixel) with an id each; an image already in the KV cache from an earlier turn is
// not encoded again. Returns the prompt token count or an error code.
JNIEXPORT jint JNICALL
Java_com_aisendiri_llama_LlamaNative_startCompletionWithImages(
        JNIEnv *env, jobject, jlong handle, jbyteArray prompt, jobjectArray images, jintArray widths,
        jintArray heights, jobjectArray ids, jint reserve_tokens, jfloat temperature, jfloat top_p, jfloat min_p,
        jfloat repeat_penalty, jint seed) {
    Session *s = session(handle);
    if (!s->mtmd) return ERR_IMAGE;
    const std::string text = to_string(env, prompt);

    const jsize n_images = env->GetArrayLength(images);
    std::vector<jint> w(n_images), h(n_images);
    env->GetIntArrayRegion(widths, 0, n_images, w.data());
    env->GetIntArrayRegion(heights, 0, n_images, h.data());
    std::vector<mtmd_bitmap *> bitmaps;
    for (jsize i = 0; i < n_images; i++) {
        auto bytes = static_cast<jbyteArray>(env->GetObjectArrayElement(images, i));
        auto id = static_cast<jstring>(env->GetObjectArrayElement(ids, i));
        const std::string rgb = to_string(env, bytes);
        if (rgb.size() != static_cast<size_t>(w[i]) * h[i] * 3) {
            for (auto *b : bitmaps) mtmd_bitmap_free(b);
            return ERR_IMAGE;
        }
        mtmd_bitmap *bmp = mtmd_bitmap_init(w[i], h[i], reinterpret_cast<const unsigned char *>(rgb.data()));
        mtmd_bitmap_set_id(bmp, to_string(env, id).c_str());
        bitmaps.push_back(bmp);
        env->DeleteLocalRef(bytes);
        env->DeleteLocalRef(id);
    }

    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    mtmd_input_text input = {text.c_str(), text.size(), true, true};
    const int32_t tok_res = mtmd_tokenize(s->mtmd, chunks, &input,
                                          const_cast<const mtmd_bitmap **>(bitmaps.data()), bitmaps.size());
    for (auto *b : bitmaps) mtmd_bitmap_free(b);
    if (tok_res != 0) {
        mtmd_input_chunks_free(chunks);
        return tok_res == 2 ? ERR_IMAGE : ERR_TOKENIZE;
    }

    // Flatten the chunks into the same form as Session::cached to find what can be reused.
    const size_t n_chunks = mtmd_input_chunks_size(chunks);
    std::vector<llama_token> entries;
    std::vector<size_t> chunk_start(n_chunks);
    std::vector<llama_pos> chunk_pos(n_chunks + 1, 0);
    for (size_t i = 0; i < n_chunks; i++) {
        const auto *chunk = mtmd_input_chunks_get(chunks, i);
        chunk_start[i] = entries.size();
        if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t n = 0;
            const llama_token *t = mtmd_input_chunk_get_tokens_text(chunk, &n);
            entries.insert(entries.end(), t, t + n);
        } else {
            entries.insert(entries.end(), mtmd_input_chunk_get_n_tokens(chunk),
                           image_marker_token(mtmd_input_chunk_get_id(chunk)));
        }
        chunk_pos[i + 1] = chunk_pos[i] + mtmd_input_chunk_get_n_pos(chunk);
    }
    const int n_total = static_cast<int>(entries.size());
    const auto n_ctx = static_cast<int>(llama_n_ctx(s->ctx));
    if (n_total + reserve_tokens > n_ctx) {
        mtmd_input_chunks_free(chunks);
        return ERR_CONTEXT_FULL;
    }

    size_t keep = 0;
    while (keep < s->cached.size() && keep < entries.size() && s->cached[keep] == entries[keep]) keep++;
    if (keep == entries.size() && keep > 0) keep--; // always decode at least one token to get fresh logits

    // Find the chunk that [keep] falls in. Images are reused whole or encoded again; text can be
    // reused partly while positions still equal indexes (no M-RoPE image before it), otherwise
    // decoding restarts at the start of the chunk.
    size_t first = 0;
    while (first + 1 < n_chunks && chunk_start[first + 1] <= keep) first++;
    const auto *first_chunk = mtmd_input_chunks_get(chunks, first);
    const bool first_is_text = mtmd_input_chunk_get_type(first_chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT;
    size_t skip = keep - chunk_start[first];
    if (!first_is_text || chunk_pos[first] != static_cast<llama_pos>(chunk_start[first])) skip = 0;
    keep = chunk_start[first] + skip;
    trim_cache(s, keep, chunk_pos[first] + static_cast<llama_pos>(skip));
    s->pending_utf8.clear();

    bool ok = true;
    for (size_t i = first; i < n_chunks && ok; i++) {
        const auto *chunk = mtmd_input_chunks_get(chunks, i);
        const bool last = i + 1 == n_chunks;
        if (mtmd_input_chunk_get_type(chunk) == MTMD_INPUT_CHUNK_TYPE_TEXT) {
            size_t n = 0;
            const llama_token *t = mtmd_input_chunk_get_tokens_text(chunk, &n);
            const size_t from = (i == first) ? skip : 0;
            ok = decode_tokens(s, t + from, n - from);
        } else {
            llama_pos new_n_past = s->n_past;
            ok = mtmd_helper_eval_chunk_single(s->mtmd, s->ctx, chunk, s->n_past, 0, llama_n_batch(s->ctx), last,
                                               &new_n_past) == 0;
            if (ok) {
                s->cached.insert(s->cached.end(), mtmd_input_chunk_get_n_tokens(chunk),
                                 image_marker_token(mtmd_input_chunk_get_id(chunk)));
                s->n_past = new_n_past;
            }
        }
    }
    mtmd_input_chunks_free(chunks);
    if (!ok) {
        reset_cache(s);
        return ERR_DECODE;
    }
    build_sampler(s, temperature, top_p, min_p, repeat_penalty, seed);
    return n_total;
}

// Generates one token. Returns the new text (possibly empty while a multi-byte character
// is still incomplete), or null when the reply is finished or the context is full.
JNIEXPORT jbyteArray JNICALL
Java_com_aisendiri_llama_LlamaNative_nextToken(JNIEnv *env, jobject, jlong handle) {
    Session *s = session(handle);
    if (!s->sampler) return nullptr;
    if (static_cast<uint32_t>(s->n_past) >= llama_n_ctx(s->ctx)) return nullptr;

    llama_token token = llama_sampler_sample(s->sampler, s->ctx, -1);
    if (llama_vocab_is_eog(s->vocab, token)) return nullptr;
    if (!decode_tokens(s, &token, 1)) return nullptr;

    s->pending_utf8 += token_to_piece(s->vocab, token);
    const size_t ready = complete_utf8_prefix(s->pending_utf8);
    std::string out = s->pending_utf8.substr(0, ready);
    s->pending_utf8.erase(0, ready);
    return to_bytes(env, out);
}

} // extern "C"
