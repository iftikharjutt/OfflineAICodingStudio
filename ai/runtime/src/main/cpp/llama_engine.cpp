#include <jni.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>
#include <memory>
#include <cstdlib>
#include <android/log.h>
#include "llama.h"

#define TAG "LlamaEngineNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct LlamaSession {
    std::string id;
    std::string model_path;
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    int n_ctx = 2048;
    int n_threads = 4;
    std::vector<llama_token> tokens;
    size_t current_pos = 0;

    ~LlamaSession() {
        if (ctx) {
            llama_free(ctx);
            ctx = nullptr;
        }
        if (model) {
            llama_free_model(model);
            model = nullptr;
        }
    }
};

static std::mutex g_sessions_mutex;
static std::unordered_map<std::string, std::shared_ptr<LlamaSession>> g_sessions;
static bool g_initialized = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeInit(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_sessions_mutex);
    if (!g_initialized) {
        llama_backend_init();
        g_initialized = true;
        LOGI("llama_backend_init called successfully");
    }
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeLoadModel(
    JNIEnv *env, jobject thiz,
    jstring model_path, jint context_size, jint threads) {

    const char *path = env->GetStringUTF8Chars(model_path, nullptr);
    std::string model_path_str(path ? path : "");
    if (path) env->ReleaseStringUTF8Chars(model_path, path);

    std::lock_guard<std::mutex> lock(g_sessions_mutex);
    if (!g_initialized) {
        llama_backend_init();
        g_initialized = true;
    }

    llama_model_params mparams = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(model_path_str.c_str(), mparams);

    if (!model) {
        LOGE("Failed to load model from path: %s", model_path_str.c_str());
        return env->NewStringUTF8("");
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = context_size > 0 ? context_size : 2048;
    cparams.n_threads = threads > 0 ? threads : 4;

    llama_context* ctx = llama_new_context_with_model(model, cparams);
    if (!ctx) {
        LOGE("Failed to create context for model: %s", model_path_str.c_str());
        llama_free_model(model);
        return env->NewStringUTF8("");
    }

    auto session = std::make_shared<LlamaSession>();
    session->id = "session_" + std::to_string(rand());
    session->model_path = model_path_str;
    session->model = model;
    session->ctx = ctx;
    session->n_ctx = cparams.n_ctx;
    session->n_threads = cparams.n_threads;

    g_sessions[session->id] = session;
    LOGI("Successfully loaded model into session %s", session->id.c_str());

    return env->NewStringUTF8(session->id.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeUnloadModel(
    JNIEnv *env, jobject thiz, jstring session_id) {

    const char *sid = env->GetStringUTF8Chars(session_id, nullptr);
    std::string session_id_str(sid ? sid : "");
    if (sid) env->ReleaseStringUTF8Chars(session_id, sid);

    std::lock_guard<std::mutex> lock(g_sessions_mutex);
    auto it = g_sessions.find(session_id_str);
    if (it != g_sessions.end()) {
        g_sessions.erase(it);
        LOGI("Unloaded session %s", session_id_str.c_str());
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeGenerateToken(
    JNIEnv *env, jobject thiz, jstring session_id, jstring prompt) {

    const char *sid = env->GetStringUTF8Chars(session_id, nullptr);
    std::string session_id_str(sid ? sid : "");
    if (sid) env->ReleaseStringUTF8Chars(session_id, sid);

    const char *p = env->GetStringUTF8Chars(prompt, nullptr);
    std::string prompt_str(p ? p : "");
    if (p) env->ReleaseStringUTF8Chars(prompt, p);

    std::lock_guard<std::mutex> lock(g_sessions_mutex);
    auto it = g_sessions.find(session_id_str);
    if (it == g_sessions.end() || !it->second || !it->second->ctx) {
        return env->NewStringUTF8("<EOS>");
    }

    auto session = it->second;
    const llama_vocab* vocab = llama_model_get_vocab(session->model);
    if (!vocab) {
        return env->NewStringUTF8("<EOS>");
    }

    // Tokenize if session prompt is new
    if (session->tokens.empty() && !prompt_str.empty()) {
        int n_tokens = prompt_str.length() + 32;
        session->tokens.resize(n_tokens);
        int res = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), session->tokens.data(), n_tokens, true, true);
        if (res < 0) {
            session->tokens.resize(-res);
            res = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.length(), session->tokens.data(), -res, true, true);
        }
        if (res > 0) {
            session->tokens.resize(res);
        } else {
            session->tokens.clear();
        }
        session->current_pos = 0;
    }

    if (session->current_pos < session->tokens.size()) {
        llama_token tok = session->tokens[session->current_pos++];
        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len > 0) {
            std::string piece(buf, len);
            return env->NewStringUTF8(piece.c_str());
        }
    }

    return env->NewStringUTF8("<EOS>");
}

}
