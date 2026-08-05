#include <jni.h>
#include <string>
#include <map>
#include <vector>
#include <cstring>
#include <algorithm>
#include "llama.h"

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
    const int n_batch = 256;
    llama_batch batch = llama_batch_init(n_batch, 0, 1);
    size_t i = 0;
    while (i < ids.size()){
        batch_clear(batch);
        size_t end = std::min(ids.size(), i + (size_t)n_batch);
        for (size_t j = i; j < end; ++j)
            batch_add(batch, ids[j], st.n_past++, (j == ids.size()-1));
        st.last_batch_n = batch.n_tokens;
        if (llama_decode(st.ctx, batch) != 0){ llama_batch_free(batch); return false; }
        i = end;
    }
    llama_batch_free(batch);
    return true;
}

static llama_token sample_greedy(SessionState& st){
    const int n_vocab = llama_n_vocab(st.model);
    float* logits = llama_get_logits_ith(st.ctx, st.last_batch_n - 1);
    if (!logits) return -1;
    int best = 0; float bv = logits[0];
    for (int i = 1; i < n_vocab; ++i)
        if (logits[i] > bv){ bv = logits[i]; best = i; }
    return (llama_token)best;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeInit(JNIEnv*, jobject){
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeLoadModel(
        JNIEnv* env, jobject, jstring jpath, jint ctxSize, jint threads){
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    llama_model* model = llama_load_model_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!model) return env->NewStringUTF("");

    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = ctxSize;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    llama_context* ctx = llama_new_context_with_model(model, cp);
    if (!ctx){ llama_free_model(model); return env->NewStringUTF(""); }

    SessionState st; st.model = model; st.ctx = ctx;
    std::string id = "s" + std::to_string(g_sessions.size() + 1);
    g_sessions[id] = st;
    return env->NewStringUTF(id.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_offlineai_ai_runtime_LlamaEngineNative_nativeUnloadModel(
        JNIEnv* env, jobject, jstring jid){
    const char* cid = env->GetStringUTFChars(jid, nullptr);
    std::string id(cid);
    env->ReleaseStringUTFChars(jid, cid);
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
    if (it == g_sessions.end()) return env->NewStringUTF("");
    SessionState& st = it->second;

    if (isFirstToken){
        st.prompt_evaluated = false;
        st.pending = -1;
        st.n_past = 0;

        const char* p = env->GetStringUTFChars(jprompt, nullptr);
        int n = llama_tokenize(st.model, p, (int)strlen(p), nullptr, 0, true, true);
        if (n < 0) n = -n;
        std::vector<llama_token> toks(n);
        int got = llama_tokenize(st.model, p, (int)strlen(p), toks.data(), (int)toks.size(), true, true);
        env->ReleaseStringUTFChars(jprompt, p);
        if (got > 0){
            toks.resize(got);
            if (!eval_ids(st, toks)) return env->NewStringUTF("");
            st.prompt_evaluated = true;
        } else {
            return env->NewStringUTF("");
        }
    } else {
        if (st.pending >= 0){
            if (!eval_ids(st, {st.pending})) return env->NewStringUTF("");
            st.pending = -1;
        }
    }

    llama_token t = sample_greedy(st);
    if (t < 0 || llama_token_is_eog(st.model, t)) return env->NewStringUTF("<EOS>");
    st.pending = t;
    char buf[256];
    int len = llama_token_to_piece(st.model, t, buf, sizeof(buf), 0, false);
    if (len <= 0) return env->NewStringUTF("");
    return env->NewStringUTF(std::string(buf, len).c_str());
}

} // extern "C"
