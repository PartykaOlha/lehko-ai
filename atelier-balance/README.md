# Atelier Balance — Pearl Glass 3.1
Android application built from the existing offline HTML app. Contains the 17–23 Nov 2026 menu, recipe recommendations based on pantry checkboxes, quantities for two, groceries, budget, exercise, rewards and reminders.

This app is isolated in the `atelier-balance/` directory on the `atelier-balance-apk` branch. It does not replace the existing Lehko AI app.

## APK via GitHub Actions
The workflow `.github/workflows/atelier-balance-apk.yml` runs on pushes to the dedicated branch, compiles a DEBUG APK and uploads `AtelierBalance-debug-apk`. From GitHub Actions open the successful run, download the artifact ZIP, extract and install app-debug.apk.

This is a debug-signed build. A future workflow run may be signed with a different transient key; back up data before reinstalling. Local WebView storage is separate from Chrome storage. Native reminders require user permission and may be delayed by Android energy optimization.

No credentials, API keys or private diary data are stored in this repository.
