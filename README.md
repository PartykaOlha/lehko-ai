# Легко AI · Lehko AI

Ukrainian-language mobile-first food and weight journal with a purple interface, designed for Android.

## Features

- Food diary: calories, protein, fats, carbohydrates (estimates)
- Weight tracking, charts, hydration and steps
- Optional eating windows: 12:12, 14:10, 16:8, 18:6
- Offline, rule-based suggestions
- Shortcut to ChatGPT and one-tap daily-summary copy
- Optional OpenAI API-powered chat and meal photo estimation (**requires a separate API key**)

## ChatGPT subscription integration

**Not implemented.** Opening ChatGPT or copying a daily summary is not an OAuth connection and does not automatically synchronize data. Sign in with ChatGPT feasibility and eligibility for open-source applications must be verified against official OpenAI documentation before implementing or promising use of ChatGPT subscriptions. See [INTEGRATION_PLAN.md](INTEGRATION_PLAN.md).

## Run

Open `index.html` in a recent browser. For installable PWA support, host the files using HTTPS. This repository is the web prototype, not the signed Android APK.

## Privacy

The public app ships with fictional starter numbers, no personal weight history, no food photos, no API keys, and no Android signing certificates. Data added by a user is saved locally in the browser and must not be committed to git. Photo calorie estimation can be inaccurate; the app is not a medical device.

## License

MIT — see [LICENSE](LICENSE).
