# Google Play and App Store readiness

Status of the store rules that matter for an on-device AI app. ✅ done in this build, ⏳ to do before publishing.

## Both stores

- ✅ No downloaded executable code. AI models are GGUF weight files; all code ships in the app binary.
- ✅ Works without an account. Offline features never require sign-up or payment.
- ✅ Chats stay on the device; no analytics or tracking SDKs. Cloud backup of app data is disabled.
- ✅ Users can report AI replies (🚩 Report under each answer opens a pre-filled email).
- ✅ "AI can make mistakes" notice in chat.
- ✅ Privacy policy written in BM and English (`site/privacy/index.html`), linked from Settings → Privacy. ⏳ Host it at https://aisendiri.com/privacy.
- ⏳ Set up the `report@aisendiri.com` mailbox used by the Report button.
- ✅ Settings → Licences lists every AI and library with its licence, Gemma's Prohibited Use Policy and "Built with Llama".
- ⏳ Age rating questionnaires; AI chat apps usually land at Teen / 12+ (answers in `docs/play-store/APP-CONTENT.md`).

## Google Play

- ✅ Target SDK 36, 64-bit (arm64-v8a) native code, 16 KB page aligned (checked in the release workflow).
- ✅ Permissions: INTERNET, ACCESS_NETWORK_STATE, and RECORD_AUDIO (asked only when the user first taps the mic in Talk).
- ✅ Photos use the system photo picker and the phone's camera app, so the app declares no READ_MEDIA_IMAGES, storage or CAMERA permission (Play's photo and video permissions policy). The camera app saves into a private FileProvider path.
- ✅ Data safety answers ("no data collected") with the reasoning per data type: `docs/play-store/APP-CONTENT.md`.
- ✅ Store listing text in BM and English: `docs/play-store/LISTING.md`.
- ✅ Signed release bundle workflow with R8 shrinking; upload key held by Khai, Play App Signing on (`docs/RELEASE-ANDROID.md`). ⏳ Create the key and add the GitHub secrets.
- ⏳ Closed test with 12 testers for 14 days (new personal developer accounts) before production.
- ⏳ Large downloads: consider a foreground service (WorkManager) so a download continues with the app in the background.

## App Store (later)

- ⏳ iOS app shell around `shared/` (`MainViewController`), llama.cpp XCFramework with Metal.
- ⏳ Downloads use `URLSession` background sessions; model files excluded from iCloud backup (`isExcludedFromBackup`).
- ⏳ Increased Memory Limit entitlement for bigger models.
- ⏳ `NSCameraUsageDescription` for taking a photo; use `PHPickerViewController` for the gallery (needs no photo library permission).
- ⏳ `NSMicrophoneUsageDescription` string. Whisper runs on the device, so no speech-recognition permission is needed.
- ⏳ Privacy manifest (`PrivacyInfo.xcprivacy`) declaring required-reason APIs (disk space, system boot time).
