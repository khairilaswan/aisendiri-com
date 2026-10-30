# Play Console: Data safety and App content answers

Play Console → Policy and programs → App content. Answers below match the app as of version 1.0.0. If a future version adds analytics, crash reporting, accounts or cloud features, these answers must change before that version ships.

## Data safety

Google counts data as "collected" only when it is sent off the phone by the app. AI Sendiri processes chats, voice and photos on the phone and sends none of it anywhere, so the honest answer is "no data collected".

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |
| (Shown after "No") Is all of the user data collected by your app encrypted in transit? | Not asked |
| Do you provide a way for users to request that their data is deleted? | Not asked (nothing is collected). Chats can be deleted in the app. |

The store page will then say "No data collected" and "No data shared with third parties".

Why each data type is "not collected":

- **Messages / chats**: processed by the AI on the phone and stored in app-private storage. Cloud backup and device transfer are off (`data_extraction_rules.xml`, `allowBackup=false`).
- **Audio (microphone)**: Whisper turns speech into text on the phone. Audio is held in memory only and never saved or sent.
- **Photos**: the system photo picker or camera app hands the app one photo, which the AI reads on the phone. It is saved with the chat in app-private storage.
- **Device info (RAM, storage, CPU, battery, Wi-Fi state)**: read on the phone to pick an AI; never sent.
- **Model downloads**: the phone downloads AI files from Hugging Face. That sends the phone's IP address and the user agent `AiSendiri-Android` to Hugging Face like any web request, but no user data. Google treats fetching content like this as normal network traffic, not data collection.
- **Report button**: opens the user's own email app with a draft. Nothing is sent unless the user presses Send in their email app, and the app itself transmits nothing. Google's guidance treats this kind of user-initiated hand-off to another app as not collected by your app. If you'd rather be extra cautious, declare *App activity → Other user-generated content*: collected, optional, purpose *App functionality* and *Developer communications*. The privacy policy covers both readings.

## Other App content declarations

| Section | Answer |
| --- | --- |
| Privacy policy | https://aisendiri.com/privacy (`site/privacy/index.html`) |
| Ads | No, the app does not contain ads |
| App access | All functionality is available without special access (no login) |
| Content rating | IARC questionnaire, category **Utility, Productivity, Communication, or Other**. No violence, sexual content, drugs or gambling in the app itself. Answer **Yes** where it asks whether the app has AI-generated content or open-ended chat, and **No** to user-to-user interaction (users can't reach each other). Expect Teen / PEGI 12. |
| Target audience | **13–15, 16–17 and 18+**. Do not tick under 13: that brings in the Families policy, which an open-ended AI chat can't meet. |
| News app | No |
| COVID-19 apps | No |
| Data safety | See above |
| Government apps | No |
| Financial features | None |
| Health apps | None. The app does not give medical features. |
| Foreground service | Not used |
| Photo and video permissions | Not used. The app uses the system photo picker and the camera app, so it has no `READ_MEDIA_IMAGES` or `CAMERA` permission. |
| Advertising ID | No (the app has no ad or analytics SDKs, so no `AD_ID` permission) |

## Generative AI rules (AI-Generated Content policy)

Play requires apps that generate content with AI to stop restricted content and let users report it:

- ✅ Report button under every AI reply (opens a pre-filled email to report@aisendiri.com). The mailbox must exist and be read before launch.
- ✅ "AI can make mistakes" notice in chat.
- ✅ The default AIs are instruction-tuned models with built-in safety training (Sailor2, Gemma-SEA-LION, Qwen3-VL), and the system prompt asks for helpful, honest answers.
- ✅ Model licences and Gemma's Prohibited Use Policy are in Settings → Licences.
- Advanced mode lets users edit the system prompt. That's allowed, but keep it in Advanced mode so beginners can't remove the defaults by accident.

## Permissions in the release build

| Permission | Why | Play declaration needed? |
| --- | --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Download AI files; warn before downloading on mobile data | No |
| `RECORD_AUDIO` | Talk to the AI, asked the first time the user taps the mic | No (not a restricted permission), but Data safety must not claim audio is collected |

## Technical requirements (checked)

- Target SDK 36 (Android 16), required for new apps and updates from 31 August 2026.
- 64-bit only (arm64-v8a).
- 16 KB memory page support for native code (llama.cpp, whisper.cpp): built with NDK r29 and checked in the release workflow.
- Android App Bundle (.aab) signed with the upload key; Google re-signs it with the app signing key (Play App Signing).
- AI files are downloaded after install and are not inside the app bundle, which keeps the Play download small and far under the 200 MB limit (the release workflow prints the sizes).
