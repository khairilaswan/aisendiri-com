# AI Sendiri

Your own personal AI, right on your phone. Private, simple, and always ready.

AI Sendiri runs a small AI model **on the phone itself** with [llama.cpp](https://github.com/ggml-org/llama.cpp).
After a one-time download it works offline, needs no account, and chats never leave the phone.

**Status:** Phase 1 + text chat MVP (Android). Voice, photo understanding and image creation are shown as "Coming soon".

## What's in this build

- Beginner-first flow: welcome → phone check → recommended AI → download → first-run test → home.
- Automatic phone check (RAM, free space, CPU cores and features, chipset, thermal, battery, Wi-Fi). Hardware only, never personal data.
- AI catalog with friendly names: **Light** (Qwen2.5 0.5B), **Balanced** (Gemma 3 1B), **Smart** (Qwen2.5 1.5B); Llama 3.2 1B in Advanced mode.
- Download with progress, pause/resume, storage check, Wi-Fi tip, and size + SHA-256 + GGUF header verification.
- Offline streaming chat with stop button, quick actions, copy, report (Play's AI-content rule), chat history.
- English and Bahasa Melayu.
- Advanced mode (Settings → Advanced mode): temperature, top-p, min-p, repeat penalty, reply length, context size, threads, system prompt, model details, device and engine diagnostics, speed.

## Project layout

| Path | What it is |
|---|---|
| `shared/` | Kotlin Multiplatform module: all screens (Compose Multiplatform), recommendation logic, strings. `androidMain` has the Android implementations. |
| `llama/` | Android library wrapping llama.cpp through JNI (`aisendiri_llama.cpp`). |
| `androidApp/` | The Android app shell (`MainActivity`). |
| `third_party/llama.cpp` | llama.cpp, pinned as a git submodule (tag `b11700`). |

iOS targets are declared in `shared/` so the code stays ready for an iOS app; the iOS shell and Metal build come later and need a Mac.

## Getting a test build

Every push to `main` builds a debug APK on GitHub Actions and publishes it as a pre-release.
See **[docs/INSTALL-ANDROID.md](docs/INSTALL-ANDROID.md)**.

## Building locally

```bash
git clone --recurse-submodules https://github.com/khairilaswan/aisendiri-com
cd aisendiri-com
./gradlew :androidApp:assembleDebug
```

Needs JDK 17+, the Android SDK, NDK `29.0.13113456` and CMake `3.31.6` (Android Studio installs these on first sync).

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/STORE-READINESS.md](docs/STORE-READINESS.md).
