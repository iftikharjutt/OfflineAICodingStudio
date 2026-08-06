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
    const uint32_t n_ctx = llama_n_ctx(st.ctx);
    const int n_batch = 256;
    llama_batch batch = llama_batch_init(n_batch, 0, 1);
    size_t i = 0;
    while (i < ids.size()){
        if ((uint32_t)st.n_past >= n_ctx) {
            LOGE("Context limit reached: n_past (%d) >= n_ctx (%u)", (int)st.n_past, n_ctx);
            llama_batch_free(batch);
            return false;
        }

        batch_clear(batch);
        size_t end = std::min(ids.size(), i + (size_t)n_batch);
        for (size_t j = i; j < end; ++j) {
            if ((uint32_t)st.n_past >= n_ctx) break;
            batch_add(batch, ids[j], st.n_past++, (j == ids.size()-1));
        }
        st.last_batch_n = batch.n_tokens;
        if (llama_decode(st.ctx, batch) != 0){
            LOGE("llama_decode failed at n_past=%d", (int)st.n_past);
            llama_batch_free(batch);
            return false;
        }
        i = end;
    }
    llama_batch_free(batch);
    return true;
}

static std::vector<llama_token> g_last_tokens;

static llama_token sample_with_penalty(SessionState& st, float repeat_penalty = 1.15f) {
    const struct llama_vocab* vocab = llama_model_get_vocab(st.model);
    const int n_vocab = llama_vocab_n_tokens(vocab);
    float* logits = llama_get_logits_ith(st.ctx, st.last_batch_n - 1);
    if (!logits) return -1;

    // Apply repetition penalty to last 64 generated tokens
    for (size_t i = 0; i < g_last_tokens.size(); ++i) {
        llama_token id = g_last_tokens[i];
        if (id >= 0 && id < n_vocab) {
            if (logits[id] <= 0) {
                logits[id] *= repeat_penalty;
            } else {
                logits[id] /= repeat_penalty;
            }
        }
    }

    int best = 0; float bv = logits[0];
    for (int i = 1; i < n_vocab; ++i) {
        if (logits[i] > bv) { bv = logits[i]; best = i; }
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
    llama_model* model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    
    if (!model){
        LOGE("llama_model_load_from_file returned null for path: %s", path);
        return env->NewStringUTF("");
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = ctxSize;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    llama_context* ctx = llama_init_from_model(model, cp);
    if (!ctx){
        LOGE("llama_init_from_model failed");
        llama_model_free(model);
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
    if (it->second.model) llama_model_free(it->second.model);
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
        LOGE("nativeGenerateToken: Session %s not found", id.c_str());
        return env->NewStringUTF("");
    }
    SessionState& st = it->second;

    if (isFirstToken){
        st.prompt_evaluated = false;
        st.pending = -1;
        st.n_past = 0;
        g_last_tokens.clear();

        // Clear residual memory / KV cache for a clean turn
        if (st.ctx) {
            llama_memory_t mem = llama_get_memory(st.ctx);
            if (mem) {
                llama_memory_clear(mem, true);
            }
        }

        const struct llama_vocab* vocab = llama_model_get_vocab(st.model);
        const char* p = env->GetStringUTFChars(jprompt, nullptr);
        // add_special = false (ChatML formatted), parse_special = true
        int n = llama_tokenize(vocab, p, (int)strlen(p), nullptr, 0, false, true);
        if (n < 0) n = -n;
        std::vector<llama_token> toks(n);
        int got = llama_tokenize(vocab, p, (int)strlen(p), toks.data(), (int)toks.size(), false, true);
        env->ReleaseStringUTFChars(jprompt, p);
        
        if (got > 0){
            toks.resize(got);
            LOGI("Tokenized prompt into %d tokens. Starting evaluation...", got);
            if (!eval_ids(st, toks)) {
                LOGE("eval_ids failed for initial prompt tokens");
                return env->NewStringUTF("");
            }
            st.prompt_evaluated = true;
        } else {
            LOGE("llama_tokenize returned 0 tokens for prompt");
            return env->NewStringUTF("");
        }
    } else {
        if (st.pending >= 0){
            if (!eval_ids(st, {st.pending})) {
                LOGE("eval_ids failed for pending token");
                return env->NewStringUTF("");
            }
            st.pending = -1;
        }
    }

    const struct llama_vocab* vocab = llama_model_get_vocab(st.model);
    llama_token t = sample_with_penalty(st);
    if (t < 0 || llama_vocab_is_eog(vocab, t)) {
        return env->NewStringUTF("<EOS>");
    }

    g_last_tokens.push_back(t);
    if (g_last_tokens.size() > 64) {
        g_last_tokens.erase(g_last_tokens.begin());
    }

    st.pending = t;
    char buf[256];
    int len = llama_token_to_piece(vocab, t, buf, sizeof(buf), 0, true);
    if (len <= 0) return env->NewStringUTF("");
    return env->NewStringUTF(std::string(buf, len).c_str());
}

} // extern "C"
