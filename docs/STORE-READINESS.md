# Google Play and App Store readiness

Status of the store rules that matter for an on-device AI app. ✅ done in this build, ⏳ to do before publishing.

## Both stores

- ✅ No downloaded executable code. AI models are GGUF weight files; all code ships in the app binary.
- ✅ Works without an account. Offline features never require sign-up or payment.
- ✅ Chats stay on the device; no analytics or tracking SDKs. Cloud backup of app data is disabled.
- ✅ Users can report AI replies (🚩 Report under each answer opens a pre-filled email).
- ✅ "AI can make mistakes" notice in chat.
- ⏳ Privacy policy page at aisendiri.com (the in-app Privacy text is the draft).
- ⏳ Set up the `report@aisendiri.com` mailbox used by the Report button.
- ⏳ Model licences screen in the app: Qwen2.5 (Apache 2.0), Gemma Terms of Use, Llama 3.2 Community License ("Built with Llama"), Whisper and whisper.cpp (MIT).
- ⏳ Age rating questionnaires; AI chat apps usually land at Teen / 12+.

## Google Play

- ✅ Target SDK 35, 64-bit (arm64-v8a) native code.
- ✅ Permissions: INTERNET, ACCESS_NETWORK_STATE, and RECORD_AUDIO (asked only when the user first taps the mic in Talk).
- ⏳ Data safety: declare microphone audio as "processed on device, not collected" (it never leaves the phone and is never saved).
- ⏳ Data safety form: "no data collected", "no data shared" (true for this build).
- ⏳ Generative AI declaration in Play Console, plus a content-filtering note.
- ⏳ Release signing key via Play App Signing (the committed `debug.keystore` is for test builds only).
- ⏳ Large downloads: consider a foreground service (WorkManager) so a download continues with the app in the background.

## App Store (later)

- ⏳ iOS app shell around `shared/` (`MainViewController`), llama.cpp XCFramework with Metal.
- ⏳ Downloads use `URLSession` background sessions; model files excluded from iCloud backup (`isExcludedFromBackup`).
- ⏳ Increased Memory Limit entitlement for bigger models.
- ⏳ `NSMicrophoneUsageDescription` string. Whisper runs on the device, so no speech-recognition permission is needed.
- ⏳ Privacy manifest (`PrivacyInfo.xcprivacy`) declaring required-reason APIs (disk space, system boot time).
