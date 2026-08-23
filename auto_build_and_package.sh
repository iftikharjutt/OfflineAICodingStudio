#!/data/data/com.termux/files/usr/bin/bash
set -e

PROJECT_DIR="/data/data/com.termux/files/home/OfflineAICodingStudio"
cd "$PROJECT_DIR"

echo "=== [1/4] Ensuring llama.cpp PIC static build is completed ==="
cd "$PROJECT_DIR/llama.cpp"
cmake --build build-cpu --config Release -j4 --target llama

echo "=== [2/4] Linking rock-solid CPU ARM NEON libllama_engine.so ==="
cd "$PROJECT_DIR"
clang++ -shared -fPIC -O3 -std=c++17 \
  -I llama.cpp/include -I llama.cpp/ggml/include \
  ai/runtime/src/main/cpp/llama_engine.cpp \
  -Wl,--start-group \
  llama.cpp/build-cpu/src/libllama.a \
  llama.cpp/build-cpu/ggml/src/libggml.a \
  llama.cpp/build-cpu/ggml/src/libggml-cpu.a \
  llama.cpp/build-cpu/ggml/src/libggml-base.a \
  -Wl,--end-group \
  -llog -lm -ldl \
  -o libllama_engine.so

ls -lh libllama_engine.so
mkdir -p "$PROJECT_DIR/ai/runtime/src/main/jniLibs/arm64-v8a"
cp -f libllama_engine.so "$PROJECT_DIR/ai/runtime/src/main/jniLibs/arm64-v8a/"

echo "=== [3/4] Building APK with ./build_apk.sh ==="
cd "$PROJECT_DIR"
./build_apk.sh

echo "=== [4/4] Deploying to /sdcard/Download ==="
cp -f "$PROJECT_DIR/dist/OfflineAICodingStudio-v1.0.0-debug.apk" /sdcard/Download/OfflineAICodingStudio-v1.0.0-debug.apk
ls -lh /sdcard/Download/OfflineAICodingStudio-v1.0.0-debug.apk

echo "=== COMPLETE! Wake lock released ==="
which termux-wake-unlock 2>/dev/null && termux-wake-unlock || true
