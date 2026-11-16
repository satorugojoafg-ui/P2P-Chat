# P2P CHAT 2.0

A bilingual (**Persian RTL default** / **English LTR**) Android messaging client built with **Kotlin**, **Jetpack Compose**, **Material 3**, **Room**, **DataStore**, **EncryptedSharedPreferences**, **BiometricPrompt**, and **WebRTC DataChannel** peer-to-peer media transfers.

---

## What's New in 2.0

- **Complete UI Privacy Cleanup**: Removed all visible backend/server diagnostics, hosting names, API endpoints, and technical errors from the entire UI. All connection errors are translated into friendly messages while keeping the internal REST and signaling layer intact.
- **Futuristic Visual Redesign**:
  - **Themes**: Dark Graphite + Deep Navy + Neon Violet + Electric Cyan (default), Light Frost theme, and System-adaptive theme.
  - **Bilingual Typography**: Bundled local **Vazirmatn** (Persian) and **Space Grotesk** (English) fonts with dynamic **Compact / Normal / Large** scaling.
  - **Chat Wallpapers**: Cosmic Graphite, Neon Aurora, Midnight Grid, and Minimal Clean.
- **Advanced Home Screen**: Time-aware greeting, user avatar shortcut, horizontal **Online Contacts** strip, live conversation search, **All / Unread / Archived** filter chips, conversation actions (**Pin**, **Mute**, **Archive**, **Clear Local History**, **Delete Locally**), and illustrated empty states.
- **Advanced Private Chat**:
  - Animated sent/received bubbles, read receipts, and **Reply-to-Message** quotes.
  - Long-press actions & multi-select bar (**Reply**, **Copy**, **Local Delete**).
  - **In-chat message search**, **Draft auto-persistence** per chat, and **Scroll-to-latest** floating button.
  - Live **Typing Indicator** transmitted directly over the open **WebRTC DataChannel**.
  - Improved attachment tray with **Cancel / Retry** controls for direct peer-to-peer photo & video transfers and completion alerts.
- **New Settings & Safety Screens** (14 total screens):
  - **Redesigned Settings**: Account, Appearance, Language, Notifications, Privacy, Chat settings, Storage & Cache, About P2P CHAT.
  - **Privacy Dashboard**: Profile/Last Seen/Online visibility controls (with clear device-local sync notice), Notification preview privacy, and hardware **Biometric App Lock** (`BiometricPrompt`).
  - **Notification Preferences**: Message alerts, Direct media alerts, Sound, Preview toggles.
  - **Storage Management**: Real disk footprint breakdown (P2P Media, Temp Cache, Message Cache) with one-tap clearing controls.
  - **About P2P CHAT**: Version info, media transfer capabilities, and security posture without misleading claims.

---

## Architecture

- **UI Layer**: Jetpack Compose + Material 3 + Navigation Compose (`@Serializable` type-safe routes).
- **State & ViewModels**: MVVM (`StateFlow`, `collectAsStateWithLifecycle`) with local Room reactive streams (`Flow`) and DataStore preferences (`UserSettings`).
- **Persistence**:
  - `EncryptedSharedPreferences` (`MasterKey` AES256-GCM) for bearer auth tokens.
  - `Room` (`AppDatabase` v2 with `MIGRATION_1_2`) for messages, conversations, user cache, pins/mutes/archives, drafts, reply quotes, and local soft-deletes.
  - `DataStore` for theme, font scale, wallpaper, language, notification, privacy, and app-lock preferences.
- **Networking & Media**:
  - `Retrofit` + `OkHttp` + `Moshi` over HTTPS (`ApiConfig.BASE_URL`).
  - `Stream WebRTC Android` (`DataChannel` + ICE signaling) for direct peer-to-peer photo and video transfers without storing chat media on the server.

---

## Build, Test & Run

### Android Client
1. Open the project in **Android Studio** or use Gradle CLI:
   ```bash
   gradle :app:testDebugUnitTest
   gradle :app:assembleDebug
   ```
2. The generated debug APK is located at:
   `app/build/outputs/apk/debug/app-debug.apk`

### Optional Backend Upgrade (`backend/app.py`)
The Android client works out-of-the-box with the existing live production v1 endpoints. If you wish to deploy the optional v2 enhancements (server-synced privacy visibility, conversation pin/mute/archive sync, and HTTP typing fallback), deploy `/backend/app.py` to your WSGI host:
- Startup automatically runs `init_and_migrate_db()` which safely adds `reply_to_id` to `messages` and creates `user_privacy`, `typing_events`, and `conversation_preferences` tables without deleting any existing SQLite data.
