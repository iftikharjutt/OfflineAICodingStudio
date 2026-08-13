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
    llama_model* model = nullptr;
    llama_context* ctx = nullptr;
    bool prompt_evaluated = false;
    llama_token pending = -1;
    llama_pos n_past = 0;
    int last_batch_n = 0;
};

static std::map<std::string, SessionState> g_sessions;

static void batch_clear(llama_batch& b) { b.n_tokens = 0; }

static void batch_add(llama_batch& b, llama_token t, llama_pos p, bool logits) {
    b.token[b.n_tokens] = t;
    b.pos[b.n_tokens] = p;
    b.n_seq_id[b.n_tokens] = 1;
    b.seq_id[b.n_tokens][0] = 0;
    b.logits[b.n_tokens] = logits;
    b.n_tokens++;
}

static bool eval_ids(SessionState& st, const std::vector<llama_token>& ids) {
    if (!st.ctx || ids.empty()) return false;

    const uint32_t n_ctx = llama_n_ctx(st.ctx);
    if ((uint64_t)st.n_past + ids.size() > n_ctx) {
        LOGE("Context overflow prevented: n_past=%d, incoming=%zu, n_ctx=%u",
             (int)st.n_past, ids.size(), n_ctx);
        return false;
    }

    const int n_batch = 256;
    llama_batch batch = llama_batch_init(n_batch, 0, 1);
    size_t i = 0;

    while (i < ids.size()) {
        batch_clear(batch);
        const size_t end = std::min(ids.size(), i + (size_t)n_batch);

        for (size_t j = i; j < end; ++j) {
            batch_add(batch, ids[j], st.n_past + (llama_pos)(j - i), j == ids.size() - 1);
        }

        st.last_batch_n = batch.n_tokens;
        if (llama_decode(st.ctx, batch) != 0) {
            LOGE("llama_decode failed at n_past=%d", (int)st.n_past);
            llama_batch_free(batch);
            return false;
        }

        st.n_past += batch.n_tokens;
        i = end;
    }

    llama_batch_free(batch);
    return true;
}

static llama_token sample_greedy(SessionState& st) {
    const struct llama_vocab* vocab = llama_model_get_vocab(st.model);
    const int n_vocab = llama_vocab_n_tokens(vocab);
    float* logits = llama_get_logits_ith(st.ctx, st.last_batch_n - 1);
    if (!logits) {
        LOGE("Failed to get logits for batch index %d", st.last_batch_n - 1);
        return -1;
    }

    int best = 0;
    float bv = logits[0];
    for (int i = 1; i < n_vocab; ++i) {
        if (logits[i] > bv) {
            bv = logits[i];
            best = i;
        }
    }

    char buf[128];
    int len = llama_token_to_piece(vocab, best, buf, sizeof(buf), 0, true);
    std::string token_str = (len > 0) ? std::string(buf, len) : "<unknown>";
    bool is_eog = llama_vocab_is_eog(vocab, best);
    LOGI("Greedy sample: token_id=%d, logit=%.4f, str='%s', is_eog=%d",
         best, bv, token_str.c_str(), is_eog);
    return (llama_token)best;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeInit(JNIEnv*, jobject) {
    LOGI("nativeInit called.");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeLoadModel(
        JNIEnv* env, jobject, jstring jpath, jint ctxSize, jint threads) {
    const char* pathChars = env->GetStringUTFChars(jpath, nullptr);
    if (!pathChars) return env->NewStringUTF("");
    const std::string path(pathChars);
    env->ReleaseStringUTFChars(jpath, pathChars);

    LOGI("nativeLoadModel starting: path=%s, ctxSize=%d, threads=%d",
         path.c_str(), ctxSize, threads);

    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    llama_model* model = llama_model_load_from_file(path.c_str(), mp);
    if (!model) {
        LOGE("llama_model_load_from_file returned null for path: %s", path.c_str());
        return env->NewStringUTF("");
    }

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t)std::max(512, (int)ctxSize);
    cp.n_threads = std::max(1, (int)threads);
    cp.n_threads_batch = std::max(1, (int)threads);

    llama_context* ctx = llama_init_from_model(model, cp);
    if (!ctx) {
        LOGE("llama_init_from_model failed");
        llama_model_free(model);
        return env->NewStringUTF("");
    }

    SessionState st;
    st.model = model;
    st.ctx = ctx;
    std::string id = "session_" + std::to_string(g_sessions.size() + 1);
    g_sessions[id] = st;
    LOGI("nativeLoadModel success: assigned sessionID=%s", id.c_str());
    return env->NewStringUTF(id.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeUnloadModel(
        JNIEnv* env, jobject, jstring jid) {
    const char* cid = env->GetStringUTFChars(jid, nullptr);
    if (!cid) return JNI_FALSE;
    const std::string id(cid);
    env->ReleaseStringUTFChars(jid, cid);

    LOGI("nativeUnloadModel called for session=%s", id.c_str());
    auto it = g_sessions.find(id);
    if (it == g_sessions.end()) return JNI_FALSE;
    if (it->second.ctx) llama_free(it->second.ctx);
    if (it->second.model) llama_model_free(it->second.model);
    g_sessions.erase(it);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeGenerateToken(
        JNIEnv* env, jobject, jstring jid, jstring jprompt, jboolean isFirstToken) {
    const char* cid = env->GetStringUTFChars(jid, nullptr);
    if (!cid) return env->NewStringUTF("");
    const std::string id(cid);
    env->ReleaseStringUTFChars(jid, cid);

    auto it = g_sessions.find(id);
    if (it == g_sessions.end()) {
        LOGE("nativeGenerateToken: Session %s not found", id.c_str());
        return env->NewStringUTF("");
    }
    SessionState& st = it->second;

    if (isFirstToken) {
        st.prompt_evaluated = false;
        st.pending = -1;
        st.n_past = 0;

        if (st.ctx) {
            llama_memory_t memory = llama_get_memory(st.ctx);
            if (memory) llama_memory_clear(memory, true);
        }

        const struct llama_vocab* vocab = llama_model_get_vocab(st.model);
        if (!vocab) {
            LOGE("Model has no vocabulary");
            return env->NewStringUTF("");
        }

        const char* p = env->GetStringUTFChars(jprompt, nullptr);
        if (!p) return env->NewStringUTF("");
        const int promptLen = (int)strlen(p);

        int n = llama_tokenize(vocab, p, promptLen, nullptr, 0, false, true);
        if (n < 0) n = -n;
        if (n <= 0) {
            env->ReleaseStringUTFChars(jprompt, p);
            LOGE("llama_tokenize size query returned %d", n);
            return env->NewStringUTF("");
        }

        std::vector<llama_token> toks((size_t)n);
        int got = llama_tokenize(vocab, p, promptLen, toks.data(), n, false, true);
        env->ReleaseStringUTFChars(jprompt, p);

        LOGI("Tokenized prompt into %d tokens (add_special=false, parse_special=true)", got);
        if (got <= 0) {
            LOGE("llama_tokenize returned %d tokens", got);
            return env->NewStringUTF("");
        }

        toks.resize((size_t)got);
        if (!eval_ids(st, toks)) {
            LOGE("eval_ids failed for initial prompt tokens");
            return env->NewStringUTF("");
        }
        st.prompt_evaluated = true;
    } else if (st.pending >= 0) {
        if (!eval_ids(st, {st.pending})) {
            LOGE("eval_ids failed for pending token; context may be full");
            return env->NewStringUTF("");
        }
        st.pending = -1;
    }

    if (!st.ctx || st.n_past >= (llama_pos)llama_n_ctx(st.ctx)) {
        LOGE("No context slots remain for generation");
        return env->NewStringUTF("");
    }

    llama_token t = sample_greedy(st);
    const struct llama_vocab* vocab = llama_model_get_vocab(st.model);
    if (t < 0 || !vocab || llama_vocab_is_eog(vocab, t)) {
        return env->NewStringUTF("<EOS>");
    }

    st.pending = t;
    char buf[256];
    int len = llama_token_to_piece(vocab, t, buf, sizeof(buf), 0, true);
    if (len <= 0) return env->NewStringUTF("");
    return env->NewStringUTF(std::string(buf, (size_t)len).c_str());
}

} // extern "C"
