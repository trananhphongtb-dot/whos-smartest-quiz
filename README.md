# Who's Smartest - Quiz

International general-knowledge quiz game for Android, in the gameshow style with 3 rounds: warm-up, head-to-head, and final round.

- Online matches for 2–4 players, solo practice, and matches against 1–3 bots
- Friends, ranking, and room invites via Google sign-in and Firebase Realtime Database
- **7,000 questions in English**, general world knowledge only (no country-specific questions): Mathematics, Science, English, World History, Geography, World Figures, Landmarks, World Organizations, Natural Records, Universe
- Language selection on the sign-in screen (default English): English, Spanish, French, German, Portuguese, Arabic, Chinese (Simplified), Japanese, Korean, Russian, Hindi, Italian, Vietnamese

## Status

- App name and package: **Who's Smartest - Quiz**, `com.whosmartest.quiz`
- Sign-in screen is multilingual; the remaining in-game screens are still being translated from the original Vietnamese UI.
- Firebase project for this app is separate from the Vietnamese apps (separate ranking, rooms, and friends). The `google-services.json` for the new Firebase project is added separately and is not in this snapshot.
- Sound files (`.wav`) are not included in connector pushes; they are added via web upload.

## Get the APK

Every push to `main` triggers GitHub Actions to build.
Go to the **Actions** tab, pick the latest run, and download `WhosSmartest-debug` from **Artifacts** to install the debug APK. The release AAB (`WhosSmartest-release-aab`) is for Google Play upload.
