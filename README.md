# Легко AI · Lehko AI

Ukrainian-language mobile-first food and weight journal with a purple interface, designed for Android.

## Features

- Food diary: calories, protein, fats, carbohydrates (estimates)
- Weight tracking, charts, hydration and steps
- Optional eating windows: 12:12, 14:10, 16:8, 18:6
- Offline, rule-based suggestions
- Shortcut to ChatGPT and one-tap daily-summary copy
- Optional OpenAI API-powered chat and meal photo estimation (**requires a separate API key**)

## ChatGPT Plus in Android preview (experimental)

The Android **preview** source includes an experimental implementation of [Sign in with ChatGPT for open-source apps](https://developers.openai.com/siwc/token-sharing-open-source/sign-in):

- System-browser OAuth sign-in, dynamic client registration, PKCE, state, and nonce
- Local loopback callback and JWT signature/claim verification
- Android Keystore-encrypted OAuth tokens
- Model discovery and streaming Responses API requests with `store:false`
- Text and meal-photo requests, only after user consent

**Unverified on a physical phone:** This native integration still needs end-to-end testing with an eligible ChatGPT Plus account. Sign-in alone is not proof that plan usage works. Access depends on authorization, model capabilities, account policy, and usage limits. It does **not** access ChatGPT conversations, memory, or this chat.

The browser version offers ChatGPT shortcut/copy and a separate optional API-key path, but has no native OAuth.

## Build Android preview

GitHub Actions workflow [Build Android preview APK](.github/workflows/build-android.yml) builds a **debug APK** on each code change. Open GitHub Actions → latest successful run → download the `lehko-ai-preview-debug-apk` artifact. Extract the ZIP on Android, then install the APK.

The preview has Android package `ua.lehko.ai.preview`, distinct from the older `ua.lehko.ai` release. It installs **beside**, not on top of, the previous app. Existing local diary data are not automatically migrated; export/import backup if needed.

Do not distribute a release APK until its native sign-in, token refresh, photo analysis, data handling, and signing are tested. Never commit signing keys or OAuth tokens.

## Run

Open `index.html` in a recent browser. For installable PWA support, host the files using HTTPS. This repository is the web prototype, not the signed Android APK.

## Privacy

The public app ships with fictional starter numbers, no personal weight history, no food photos, no API keys, and no Android signing certificates. Data added by a user is saved locally in the browser and must not be committed to git. Photo calorie estimation can be inaccurate; the app is not a medical device.

## License

MIT — see [LICENSE](LICENSE).
