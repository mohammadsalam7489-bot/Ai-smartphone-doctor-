# AI Smartphone Doctor — Secure Server-Side AI Proxy

This lightweight, zero-dependency Node.js service keeps your `GEMINI_API_KEY` **100% off Android APKs, GitHub repositories, and client bytecode**.

## Supported Models
Calls Google Gemini `v1beta` `generateContent` using currently supported Gemini 3.x models:
1. `gemini-3.8-flash` (primary)
2. `gemini-3.5-flash-lite` (automatic fallback on 404/429/503)
3. `gemini-3-flash-preview` (secondary fallback)

Retired `gemini-1.5-flash`, `gemini-2.0-flash`, and `gemini-2.5-flash` are completely removed.

## Quick Local / Cloud Run Deployment

1. Configure your secret strictly on the server (e.g., Cloud Run secret / Render env var / local `.env` not tracked by git):
   ```bash
   export GEMINI_API_KEY="<your-server-side-key>"
   export PORT=8080
   node server.js
   ```
2. Verify health (never reveals the key):
   ```bash
   curl http://localhost:8080/health
   ```
3. Point CI/CD or Gradle builds to your deployed HTTPS URL (non-secret URL, no key embedded):
   ```bash
   gradle :app:assembleDebug -PAI_PROXY_URL="https://your-proxy-url.run.app"
   ```
   Or set GitHub Repository Variable `vars.AI_PROXY_URL`.

When `AI_PROXY_URL` is blank, the Android app automatically uses **Firebase AI Logic (`Firebase.ai` + Firebase App Check)** without requiring any raw Gemini API key in the APK.
