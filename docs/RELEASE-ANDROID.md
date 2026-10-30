# Releasing AI Sendiri on Google Play

Test builds (`com.aisendiri.app.debug`) keep coming from the **Android build** workflow. Play Store builds (`com.aisendiri.app`) come from the **Play Store release build** workflow, signed with an upload key only you hold.

## 1. Create the upload key (once)

Do this on your own computer, not in CI, and pick strong passwords.

With Android Studio: Build → Generate Signed App Bundle or APK → Android App Bundle → Create new… Save it as `aisendiri-upload.jks`, alias `upload`, validity 25+ years.

Or with Java's `keytool`:

```sh
keytool -genkeypair -v -keystore aisendiri-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

Back up `aisendiri-upload.jks` and both passwords in two places (for example a password manager and a USB drive). With Play App Signing, Google holds the real app signing key, so a lost upload key can be reset through Play Console support, but it takes days.

Never commit the keystore. `.gitignore` blocks `*.jks` and `*.keystore` except the shared `debug.keystore`.

## 2. Give the key to GitHub Actions (once)

Turn the keystore into text:

- macOS: `base64 -i aisendiri-upload.jks | pbcopy`
- Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("aisendiri-upload.jks")) | Set-Clipboard`

Then in GitHub: repo → Settings → Secrets and variables → Actions → New repository secret, and add:

| Secret | Value |
| --- | --- |
| `UPLOAD_KEYSTORE_BASE64` | the text you just copied |
| `UPLOAD_STORE_PASSWORD` | keystore password |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | key password (same as the keystore password if you only set one) |

## 3. Build a release

GitHub → Actions → **Play Store release build** → Run workflow → enter the version (for example `1.0.0`). Pushing a tag like `v1.0.0` does the same.

The run's artifact has:

- `AiSendiri-<version>-<code>-signed.aab`: upload this to Play Console.
- `AiSendiri-<version>-<code>-signed.apk`: install on the A51/A15 to test the release build before uploading. It can sit next to the test build, because the app id is different. Uninstall it before installing from Play, since Play re-signs the app with a different key.

The version code is the workflow's run number, so every run is higher than the last, as Play requires. The workflow also checks that every native library supports 16 KB memory pages.

On pull requests the workflow builds unsigned, just to prove the release build (with R8 shrinking) still works.

## 4. Play Console (first release)

1. Create a developer account at play.google.com/console (one-time US$25, identity verification).
2. Create app → name "AI Sendiri", default language Bahasa Melayu (ms-MY), App, Free.
3. Fill **App content** using `docs/play-store/APP-CONTENT.md` and the **Store listing** using `docs/play-store/LISTING.md`.
4. Testing → Internal testing → Create release → keep **Play App Signing** on (default) → upload the `.aab`.
5. Personal developer accounts must run a **closed test with at least 12 testers for 14 days in a row** before Google allows a production release. Start it as soon as the internal test looks good: Testing → Closed testing → add testers' Gmail addresses or a Google Group.
6. After 14 days, apply for production access in the Dashboard, then promote the release.

## Before every release

- Bump the version name in the workflow input.
- Install the signed APK on the A51 and A15: first run, download AI Ringan, chat, Talk, a photo question, Report button, Settings → Licences.
- If the app starts collecting or sending anything new, update `docs/play-store/APP-CONTENT.md`, the Data safety form and `site/privacy/index.html` first.
