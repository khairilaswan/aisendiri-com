# Install a test build on your phone (Galaxy A51 / A15)

No Android Studio or computer needed.

## 1. Download the APK on your phone

1. On your phone, open **github.com/khairilaswan/aisendiri-com/releases** in Chrome and sign in to GitHub (the repo is private).
2. Tap the newest **Test build N**.
3. Under **Assets**, tap `AiSendiri-debug-N.apk`. Chrome may warn that the file can be harmful. Tap **Download anyway**.

Alternative: open the repo's **Actions** tab, open the newest green run, and download the `AiSendiri-debug-N` artifact (it arrives as a .zip with the APK inside).

## 2. Allow installing from Chrome (first time only)

1. Tap the downloaded file (or open **My Files → Downloads**).
2. Samsung shows "For your security, your phone is not allowed to install unknown apps from this source". Tap **Settings**.
3. Turn on **Allow from this source**, then go back.
4. Tap **Install**. If **Play Protect** asks, tap **More details → Install anyway** (it doesn't know test apps yet).

## 3. First run

1. Open **AI Sendiri**, choose English or Bahasa Melayu, tap **Get started**.
2. The app checks your phone and recommends an AI. On a 4 GB A51/A15 that is the **Light AI** (about 400 MB). On 6–8 GB you get a bigger one.
3. Use Wi-Fi for the download. You can pause and resume.
4. After the download, the app starts the AI and runs a short test. Then tap **Start chatting**.
5. Turn on airplane mode and chat again to confirm it works offline.

## Updating

Install newer test builds the same way. They install over the old one and keep your downloaded AI and chats (all test builds share one signing key).

The test app is called **AI Sendiri** and has its own id (`com.aisendiri.app.debug`), so it won't clash with a future Play Store version.

## Sending feedback

Useful things to tell me after testing:

- Phone model and RAM (Settings → About phone).
- Which AI it recommended, and whether download and first test passed.
- Reply speed. Turn on **Settings → Advanced mode**: each reply then shows tokens per second.
- Anything confusing in the wording, in either language.
- If the app closes by itself: what you were doing just before.
