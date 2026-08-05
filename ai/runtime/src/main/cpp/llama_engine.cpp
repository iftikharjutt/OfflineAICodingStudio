#include <jni.h>
#include <string>
#include <map>
#include <vector>
#include <cstring>
#include <algorithm>
#include <android/log.h>
#include "llama.h"

#define TAG "llama_engine_native_cpp"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

struct SessionState {
    llama_model*   model = nullptr;
    llama_context* ctx   = nullptr;
    bool           prompt_evaluated = false;
    llama_token    pending = -1;
    llama_pos      n_past  = 0;
    int            last_batch_n = 0;
    std::string    last_error;
};
static std::map<std::string, SessionState> g_sessions;

static void batch_clear(llama_batch& b){ b.n_tokens = 0; }
static void batch_add(llama_batch& b, llama_token t, llama_pos p, bool logits){
    b.token   [b.n_tokens] = t;
    b.pos     [b.n_tokens] = p;
    b.n_seq_id[b.n_tokens] = 1;
    b.seq_id  [b.n_tokens][0] = 0;
    b.logits  [b.n_tokens] = logits;
    b.n_tokens++;
}

static bool eval_ids(SessionState& st, const std::vector<llama_token>& ids){
    const int n_batch = 256;
    llama_batch batch = llama_batch_init(n_batch, 0, 1);
    size_t i = 0;
    while (i < ids.size()){
        batch_clear(batch);
        size_t end = std::min(ids.size(), i + (size_t)n_batch);
        for (size_t j = i; j < end; ++j)
            batch_add(batch, ids[j], st.n_past++, (j == ids.size()-1));
        st.last_batch_n = batch.n_tokens;
        int status = llama_decode(st.ctx, batch);
        if (status != 0){
            st.last_error = "[JNI_ERROR: llama_decode failed with code " + std::to_string(status) + " at n_past=" + std::to_string(st.n_past) + "]";
            LOGE("%s", st.last_error.c_str());
            llama_batch_free(batch);
            return false;
        }
        i = end;
    }
    llama_batch_free(batch);
    return true;
}

static llama_token sample_greedy(SessionState& st){
    const int n_vocab = llama_n_vocab(st.model);
    float* logits = llama_get_logits(st.ctx);
    if (!logits && st.last_batch_n > 0) {
        logits = llama_get_logits_ith(st.ctx, st.last_batch_n - 1);
    }
    if (!logits) {
        st.last_error = "[JNI_ERROR: Failed to retrieve logits from llama_context]";
        LOGE("%s", st.last_error.c_str());
        return -1;
    }
    int best = 0; float bv = logits[0];
    for (int i = 1; i < n_vocab; ++i) {
        if (logits[i] > bv){ bv = logits[i]; best = i; }
    }
    return (llama_token)best;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeInit(JNIEnv*, jobject){
    LOGI("nativeInit called.");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeLoadModel(
        JNIEnv* env, jobject, jstring jpath, jint ctxSize, jint threads){
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    LOGI("nativeLoadModel starting: path=%s, ctxSize=%d, threads=%d", path, ctxSize, threads);
    
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    
    if (!model){
        LOGE("llama_load_model_from_file returned null for path: %s", path);
        return env->NewStringUTF("");
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = ctxSize;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    llama_context* ctx = llama_new_context_with_model(model, cp);
    if (!ctx){
        LOGE("llama_new_context_with_model failed");
        llama_free_model(model);
        return env->NewStringUTF("");
    }

    SessionState st; st.model = model; st.ctx = ctx;
    std::string id = "session_" + std::to_string(g_sessions.size() + 1);
    g_sessions[id] = st;
    LOGI("nativeLoadModel success: assigned sessionID=%s", id.c_str());
    return env->NewStringUTF(id.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeUnloadModel(
        JNIEnv* env, jobject, jstring jid){
    const char* cid = env->GetStringUTFChars(jid, nullptr);
    std::string id(cid);
    env->ReleaseStringUTFChars(jid, cid);
    LOGI("nativeUnloadModel called for session=%s", id.c_str());
    auto it = g_sessions.find(id);
    if (it == g_sessions.end()) return JNI_FALSE;
    if (it->second.ctx)   llama_free(it->second.ctx);
    if (it->second.model) llama_free_model(it->second.model);
    g_sessions.erase(it);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeGenerateToken(
        JNIEnv* env, jobject, jstring jid, jstring jprompt, jboolean isFirstToken){
    const char* cid = env->GetStringUTFChars(jid, nullptr);
    std::string id(cid);
    env->ReleaseStringUTFChars(jid, cid);
    auto it = g_sessions.find(id);
    if (it == g_sessions.end()) {
        std::string err = "[JNI_ERROR: Session " + id + " not found in native memory]";
        LOGE("%s", err.c_str());
        return env->NewStringUTF(err.c_str());
    }
    SessionState& st = it->second;

    if (isFirstToken){
        st.prompt_evaluated = false;
        st.pending = -1;
        st.n_past = 0;
        st.last_error.clear();

        const char* p = env->GetStringUTFChars(jprompt, nullptr);
        
        // First try with add_special=false, parse_special=true
        int n = llama_tokenize(st.model, p, (int)strlen(p), nullptr, 0, false, true);
        if (n < 0) n = -n;
        
        std::vector<llama_token> toks(n);
        int got = llama_tokenize(st.model, p, (int)strlen(p), toks.data(), (int)toks.size(), false, true);
        
        // Fallback: If 0 tokens returned, try with add_special=true
        if (got <= 0) {
            n = llama_tokenize(st.model, p, (int)strlen(p), nullptr, 0, true, true);
            if (n < 0) n = -n;
            toks.resize(n);
            got = llama_tokenize(st.model, p, (int)strlen(p), toks.data(), (int)toks.size(), true, true);
        }
        
        env->ReleaseStringUTFChars(jprompt, p);
        
        if (got > 0){
            toks.resize(got);
            LOGI("Tokenized prompt into %d tokens. Starting evaluation...", got);
            if (!eval_ids(st, toks)) {
                return env->NewStringUTF(st.last_error.c_str());
            }
            st.prompt_evaluated = true;
        } else {
            std::string err = "[JNI_ERROR: Tokenizer returned 0 tokens for prompt text]";
            LOGE("%s", err.c_str());
            return env->NewStringUTF(err.c_str());
        }
    } else {
        if (st.pending >= 0){
            if (!eval_ids(st, {st.pending})) {
                return env->NewStringUTF(st.last_error.c_str());
            }
            st.pending = -1;
        }
    }

    llama_token t = sample_greedy(st);
    if (t < 0) {
        std::string err = st.last_error.empty() ? "[JNI_ERROR: sample_greedy returned -1 token]" : st.last_error;
        return env->NewStringUTF(err.c_str());
    }

    bool isEog = llama_token_is_eog(st.model, t);
    LOGI("Sampled token ID=%d (isEog=%d)", t, isEog);

    if (isEog) {
        return env->NewStringUTF("<EOS>");
    }

    st.pending = t;
    char buf[256];
    int len = llama_token_to_piece(st.model, t, buf, sizeof(buf), 0, false);
    if (len <= 0) {
        // Fallback for special non-printable pieces
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(std::string(buf, len).c_str());
}

} // extern "C"
