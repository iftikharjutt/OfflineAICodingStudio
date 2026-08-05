# AI AUDIT & PIPELINE DIAGNOSTIC REPORT

## 1. Full Chat & Inference Pipeline Architecture

```
[User Input in ChatScreen]
       │
       ▼
[ChatViewModel.sendMessage()]
       │
       ├── Check Active Mode: AssistantMode.CHAT vs AssistantMode.AGENT
       │
       ├── If CHAT Mode:
       │     └─ ChatPromptBuilder.buildPrompt(ChatPromptContext)
       │          └─ ModelTemplateDetector.formatPrompt(family, system, history, userRequest)
       │
       └── If AGENT Mode:
             └─ AgentPromptBuilder.buildPrompt(AgentPromptContext)
                  └─ ModelTemplateDetector.formatPrompt(family, system, history, userRequest)
       │
       ▼
[LlamaInferenceEngine.streamCompletion(CompletionRequest)]
       │
       ▼
[LlamaEngineNative (JNI Bridge)]
       │
       ├── Check activeSession != null
       │     └─ If null: Emit TokenEvent.Error(ModelNotLoadedException)
       │
       ├── Check nativeAvailable == true (libllama_engine.so)
       │     └─ If false: Emit TokenEvent.Error(NativeInferenceException)
       │
       ▼
[llama_engine.cpp (C++ llama.h Engine)]
       │
       ├── nativeLoadModel() -> llama_load_model_from_file(), llama_new_context_with_model()
       └── nativeGenerateToken() -> llama_tokenize(parse_special=true), eval_ids(), sample_greedy()
       │
       ▼ (Tokens Streamed Back)
[LlamaEngineNative -> ChatViewModel.collect { TokenEvent }]
       │
       ├── TokenEvent.Token -> Appends to message text in real time UI
       ├── TokenEvent.Error -> Displays error message to UI
       └── TokenEvent.Completed ->
             ├─ If CHAT Mode: Saves turn to ConversationManager
             └─ If AGENT Mode: Invokes FileOperationParser.parseJsonResponse()
```

## 2. Entry Point
- **App Entry Point:** `app/src/main/java/com/offlineai/codingstudio/MainActivity.kt`
  - Instantiates `LlamaEngineNative()`, `ChatViewModel`, `ModelsViewModel`, `SettingsViewModel`.
  - Monitors `selectedModel` via `LaunchedEffect`:
    ```kotlin
    val selectedModel by modelsViewModel.selectedModel.collectAsState()
    LaunchedEffect(selectedModel, currentSettings) {
        selectedModel?.let { model ->
            inferenceEngine.loadModel(ModelLoadRequest(modelPath = model.path, ...))
        }
    }
    ```

## 3. Every Place Where a Response or Default Text Can Be Generated

1. **Initial Welcome Message (`ChatViewModel.kt`)**:
   - `_messages` initial state: `"Hello! I am your offline AI Coding Assistant..."`
2. **Model Not Loaded Error Response (`LlamaEngineNative.kt` & `ChatViewModel.kt`)**:
   - `LlamaEngineNative`: `"No GGUF model loaded. Please select and load a model first."`
   - `ChatViewModel`: `"Error: No GGUF model loaded. Please select and load a model first."`
3. **Native Library Missing Error (`LlamaEngineNative.kt`)**:
   - `"Native LLM engine unavailable. llama_engine.so library missing."`
4. **Agent Patch Summary Fallback (`FileOperationParser.kt`)**:
   - `cleanSummary()` fallback: `"Hello! How can I help you build or edit your web project?"` (when operations are empty) or `"Generated code changes for: <files>"` (when operations exist).
5. **No Operations Default (`ChatViewModel.kt`)**:
   - `"Agent finished patch generation."` when operations list is empty.

## 4. Fallback Paths & Mock Audit
- **Mock Engine Status:** ❌ **NO MOCK ENGINE EXISTS.**
  In previous commits, `LlamaEngineNative.kt` had a mock text generator (`mockResponse = "\"summary\":\"Mock response...\""`). This was completely removed. Now, if native engine is missing or model is unselected, explicit `TokenEvent.Error` is emitted.
- **Root Cause of Default/Error Replies:**
  If a user experiences default or error replies:
  1. **No Model Selected:** Upon first install, `modelsViewModel.selectedModel` is `null`. Until the user goes to **Models Manager** and loads a `.gguf` model file, `inferenceEngine.loadModel` is not executed, causing `ModelNotLoadedException`.
  2. **Model Path Wiring:** `ChatViewModel.sendMessage()` parameter `modelPath: String?` must be passed from `modelsViewModel.selectedModel.value?.path`.

## 5. List of All Audited & Copied Files (31 files)
- `feature/chat/src/main/java/com/offlineai/feature/chat/ChatScreen.kt`
- `feature/chat/src/main/java/com/offlineai/feature/chat/ChatViewModel.kt`
- `ai/prompting/src/main/java/com/offlineai/ai/prompting/ChatPromptBuilder.kt`
- `ai/prompting/src/main/java/com/offlineai/ai/prompting/AgentPromptBuilder.kt`
- `ai/prompting/src/main/java/com/offlineai/ai/prompting/StructuredPromptBuilder.kt`
- `ai/prompting/src/main/java/com/offlineai/ai/prompting/ConversationManager.kt`
- `ai/prompting/src/main/java/com/offlineai/ai/prompting/ModelTemplateDetector.kt`
- `ai/prompting/src/main/java/com/offlineai/ai/prompting/FileOperationParser.kt`
- `ai/runtime/src/main/java/com/offlineai/ai/runtime/LlamaEngineNative.kt`
- `ai/runtime/src/main/java/com/offlineai/ai/runtime/LlamaInferenceEngine.kt`
- `ai/runtime/src/main/cpp/llama_engine.cpp`
- `ai/agent/src/main/java/com/offlineai/ai/agent/SelfRepairLoop.kt`
- `ai/agent/src/main/java/com/offlineai/ai/agent/AgenticPatchExecutor.kt`
- `core/models/src/main/java/com/offlineai/core/models/AssistantMode.kt`
- `core/models/src/main/java/com/offlineai/core/models/FileOperation.kt`
- `feature/models-manager/src/main/java/com/offlineai/feature/modelsmanager/ModelsViewModel.kt`
- `feature/models-manager/src/main/java/com/offlineai/feature/modelsmanager/ModelsScreen.kt`
- `app/src/main/java/com/offlineai/codingstudio/MainActivity.kt`
- `app/src/main/AndroidManifest.xml`
- `build.gradle.kts`
- `settings.gradle.kts`
- `app/build.gradle.kts`
- `ai/prompting/build.gradle.kts`
- `ai/runtime/build.gradle.kts`
- `ai/agent/build.gradle.kts`
- `feature/chat/build.gradle.kts`
- `ai/prompting/src/test/java/com/offlineai/ai/prompting/ChatPromptBuilderTest.kt`
- `ai/prompting/src/test/java/com/offlineai/ai/prompting/AgentPromptBuilderTest.kt`
- `ai/prompting/src/test/java/com/offlineai/ai/prompting/ConversationManagerTest.kt`
- `ai/prompting/src/test/java/com/offlineai/ai/prompting/ModelTemplateDetectorTest.kt`
- `ai/prompting/src/test/java/com/offlineai/ai/prompting/FileOperationParserTest.kt`