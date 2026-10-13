# OpenAI integration plan

**Current status:** ChatGPT subscription sign-in is not yet implemented. The web prototype has a shortcut to ChatGPT and an optional API-key-based chat and photo analyzer. These are distinct.

1. Confirm official eligibility and developer documentation for Sign in with ChatGPT for this open-source app.
2. Use Android system-browser OAuth flow with PKCE, state/nonce, validated redirect URIs, and secure token storage.
3. Do not put access tokens, API keys, photos, or health data in repository files or app source.
4. Add a clear opt-in before sharing weight, food diary entries, or meal photos with AI providers.
5. Implement user-visible connect/disconnect, permissions, secure data deletion, and privacy notices.
6. Test camera/gallery and Android installation on real supported devices before publishing a new APK.

The existing app does not automatically connect to or access any user's ChatGPT account, conversations, or memory.
