# Automatic APK Build, Distribution & In-App Update Setup

This project includes a complete end-to-end **GitHub Actions + In-App Self-Update** pipeline that lets existing users update **without uninstalling** and **preserves login session (`ai_doctor_session`), settings, language preference, and local Room diagnostic logs**.

---

## 1. How the Architecture Works

1. **Versioning (`app/version.properties` & Gradle)**:
   - Local builds read `VERSION_CODE` and `VERSION_NAME` from `app/version.properties` (currently `VERSION_CODE=2`, `VERSION_NAME=1.1.0`).
   - GitHub Actions automatically computes a strictly increasing:
     - `versionCode = 100 + github.run_number`
     - `versionName = 1.1.<github.run_number>`
     and passes `-PappVersionCode=... -PappVersionName=...` to Gradle.
2. **GitHub Actions Workflow (`.github/workflows/android-auto-release.yml`)**:
   - Triggers on every push to `main` (or manual `workflow_dispatch`).
   - Runs `gradle :app:testDebugUnitTest` to verify nothing is broken.
   - Builds a signed APK (`Ai-Smartphone-Doctor.apk`) using your consistent keystore.
   - Computes SHA-256 and file size and writes `update-manifest.json`.
   - Uploads `Ai-Smartphone-Doctor.apk` + `update-manifest.json` to:
     - Immutable GitHub Release tag (`v1.1.<run_number>`)
     - Rolling `latest` GitHub Release tag (queried by installed apps)
   - Optionally publishes to **Firebase App Distribution** if Firebase secrets are configured.
3. **In-App Automatic Update Client (`AppUpdateManager.kt` + `DoctorViewModel.kt`)**:
   - Checks `BuildConfig.UPDATE_MANIFEST_URL` (with GitHub Releases API fallback `BuildConfig.GITHUB_RELEASES_API_URL`) automatically when the app opens and manually via **Diagnostics → CHECK UPDATES**.
   - Compares `remote.versionCode > local.versionCode`.
   - When an update is found:
     - Posts an Android notification (**"Update Available: vX.Y.Z"**).
     - Displays an in-app **Update Available** banner and an interactive update dialog showing version number, release date, changelog, and direct APK download link.
   - On tapping **Update**:
     - Streams APK download into `filesDir/updates/` with live byte/percentage progress.
     - Verifies **SHA-256 digest** and confirms downloaded APK `packageName == context.packageName`.
     - Checks `packageManager.canRequestPackageInstalls()` (`REQUEST_INSTALL_PACKAGES`) and guides the user smoothly if one-time permission is needed.
     - Launches `Intent(Intent.ACTION_VIEW)` via `FileProvider` (`${applicationId}.fileprovider`) so Android performs an **in-place upgrade** while preserving Room DB history, language/voice preferences, and troubleshooting session context.

---

## 2. Preserving Data & Enabling Seamless In-Place Upgrades (Critical)

Android only allows updating an installed app without uninstalling (preserving Room DB, SharedPreferences session, and settings) when:
- **`applicationId` stays identical**: `com.aistudio.smartphonedoctor.oapeir`
- **Signing certificate stays identical** across every build.

Configure ONE of these signing options in your GitHub repository **Settings → Secrets and variables → Actions**:

### Option A (Release Keystore — Recommended)
- `RELEASE_KEYSTORE_BASE64`: `base64 -w0 my-upload-key.jks`
- `STORE_PASSWORD`: your keystore password
- `KEY_PASSWORD`: your upload key password (alias must be `upload` to match `app/build.gradle.kts`)

### Option B (Consistent Debug Keystore)
- `DEBUG_KEYSTORE_BASE64`: `base64 -w0 debug.keystore`
  (If you encode the same `debug.keystore` used locally, CI APKs will seamlessly upgrade locally built APKs!)

---

## 3. Optional CI Secrets (Firebase Config & App Distribution)

- `GOOGLE_SERVICES_JSON`: Base/raw JSON content of `app/google-services.json` if overriding Firebase project config at CI build time (Gemini requests use Firebase AI Logic + App Check — zero raw API keys stored in CI or APK).
- `FIREBASE_APP_ID`: Your Firebase Android App ID (e.g. `1:123456:android:abcdef`) for Firebase App Distribution.
- `FIREBASE_CREDENTIAL_FILE_CONTENT`: Firebase service account JSON content with *Firebase App Distribution Admin* role.
- `FIREBASE_TESTER_GROUPS`: Tester group alias in Firebase Console (defaults to `testers`).
