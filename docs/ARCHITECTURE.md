# Architecture

## Why Kotlin Multiplatform

One codebase for Android now and iOS later. Screens (Compose Multiplatform), recommendation logic, catalog,
settings model and all text live in `shared/src/commonMain`. Platform work sits behind small interfaces
(`shared/.../platform/Platform.kt`):

| Interface | Android implementation | iOS later |
|---|---|---|
| `DeviceInspector` | `AndroidDeviceInspector` (ActivityManager, StatFs, /proc/cpuinfo, PowerManager) | `ProcessInfo`, `NSFileManager` |
| `ModelRepository` | `AndroidModelRepository` (HttpURLConnection, resume via Range, SHA-256) | `URLSession` background download |
| `ChatEngine` | `LlamaChatEngine` → `llama/` JNI → llama.cpp | llama.cpp XCFramework + Metal |
| `SettingsStore`, `ConversationStore` | SharedPreferences, JSON files | `NSUserDefaults`, files |
| `PlatformActions` | Intents (share, report email) | UIKit equivalents |

`AppController` (commonMain) owns app state and navigation; screens only render it.

## On-device engine

- llama.cpp pinned to tag `b11700` as a submodule, built by CMake through AGP.
- Built like llama.cpp's own Android example: `GGML_BACKEND_DL` + `GGML_CPU_ALL_VARIANTS`, so every ARM CPU
  variant ships as its own library and the best one for the phone is loaded at runtime
  (A51 Exynos 9611 → ARMv8.0; A15 Helio G99 → ARMv8.2 with dot-product). Native libraries are extracted on
  install so llama.cpp can find them.
- Native code is always built `Release`, even in debug APKs.
- One `Session` per loaded model, used from a single thread. Each turn re-applies the model's chat template
  to the whole conversation and reuses the matching prefix of the KV cache, so only new text is processed.
  If the chat outgrows the context window, the oldest messages are dropped.
- Text crosses JNI as UTF-8 bytes; incomplete multi-byte characters are held back until complete.

## Choosing an AI

`Recommender` uses total RAM (as Android reports it: a 4 GB phone shows about 3.6 GiB) and free storage
(file size + 1 GB headroom). It picks the largest beginner model that "works well", else one that "may be slow",
else none. If the first-run test fails, it offers the next smaller model. Defaults: context 2048
(4096 on 7+ GiB), threads = cores / 2 clamped to 2–4, batch 256. Advanced mode can override each.

## Downloads

Files go to app-private storage (`files/models`). A `.part` file allows pause/resume. Before the rename to the
final name the app checks the size and SHA-256 published by the Hugging Face API, and the GGUF magic bytes.
Model files are weights (data), not executable code.
