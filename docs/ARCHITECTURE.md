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
| `SpeechToText` | `WhisperSpeechToText` → `llama/` JNI → whisper.cpp | same, Core ML encoder optional |
| `VoiceRecorder` | `AndroidVoiceRecorder` (AudioRecord, 16 kHz mono, memory only) | `AVAudioEngine` |
| `Speaker` | `AndroidSpeaker` (system TextToSpeech, offline voices only) | `AVSpeechSynthesizer` |
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
- Gemma 4 based AIs (SEA-LION v4.5 E2B) have no built-in llama.cpp template, so `aisendiri_llama.cpp` writes their
  turn format itself, with an empty thought block. SEA-LION E2B often writes English notes before its answer anyway,
  ending them with `<channel|>`; `ThoughtFilter` holds the reply back and drops the notes.

## Choosing an AI

`Recommender` uses total RAM (as Android reports it: a 4 GB phone shows about 3.6 GiB) and free storage
(file size + 1 GB headroom). It picks the largest beginner model that "works well", else one that "may be slow",
else none. If the first-run test fails, it offers the next smaller model. Defaults: context 2048
(4096 on 7+ GiB), threads = cores / 2 clamped to 2–4, batch 256. Advanced mode can override each.

## Downloads

Files go to app-private storage (`files/models`). A `.part` file allows pause/resume. Before the rename to the
final name the app checks the size and SHA-256 published by the Hugging Face API, and the GGUF magic bytes.
Model files are weights (data), not executable code.

## Voice (Talk)

- Speech to text: whisper.cpp pinned as a submodule (`third_party/whisper.cpp`). Only its `src/whisper.cpp` is
  compiled, into `libaisendiri_whisper.so`, linked against llama.cpp's ggml. One ggml means one set of CPU
  backends and no duplicate symbols. Its Kotlin binding is `WhisperNative` in the `llama/` module.
- Models: Whisper multilingual `small` q5_1 (~181 MB, default on chips with ARMv8.2 dot-product, best for Bahasa Melayu)
  and `base` q5_1 (~57 MB, ~3x faster, default on older chips such as the A51 and on phones under 3 GiB RAM), from `ggerganov/whisper.cpp` on Hugging Face. Same
  download, resume and SHA-256 checks as chat AIs; the file magic is `lmgg` instead of `GGUF`.
- Recognition language follows the app (ms/en), with a short Malay-English initial prompt for Manglish.
  "Fast listening" sets `audio_ctx` to the clip length plus a margin instead of the full 30 s window. Temperature fallback is off (one decoding pass) and tokens are capped by clip length, so a hard clip can't multiply the wait.
- End of speech: `Endpointer` (commonMain) uses loudness against an adaptive noise floor: stops after 1 s
  of silence, gives up after 8 s with no speech, caps at 30 s. Audio is never written to disk.
- Text to speech: Android `TextToSpeech`, preferring Google's engine (Samsung's has no Malay voice), offline
  voices only. Falls back to an Indonesian voice for Malay, otherwise shows text and a button to install a
  voice. Replies are spoken sentence by sentence while they stream, and the AI is asked for short spoken answers.
- A voice conversation is saved like a normal chat, so it shows in history.

## Photos

- Picking: Android's Photo Picker (`PickVisualMedia`) and the phone's camera app (`TakePicture` into a
  `FileProvider` cache file). Neither needs a storage or camera permission. The app keeps a copy in
  `files/photos`: rotated upright from EXIF, longest side 1280 px, JPEG. It is deleted with its chat.
- Reading: llama.cpp's `mtmd` library (built from the same submodule, video off). An image encoder ("mmproj") is
  attached to the loaded session. The prompt holds a media marker per photo; `mtmd_tokenize` splits it into text
  and image chunks, which are evaluated in order. In the KV cache each image is remembered by a hash of its id, so
  follow-up questions don't re-encode it. "Photo detail" caps the image tokens.
- Add-ons (`VisionCatalog`): Smart AI uses its own encoder. Light AIs can't see, so their add-on also downloads a
  photo AI (Qwen3-VL 2B); chats with a photo switch to it. See `docs/VISION-MODEL-CHECK.md`.
