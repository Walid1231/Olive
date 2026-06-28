# Olive Music Player 🎵
Olive is a modern, feature-rich music player for Android, built natively with **Kotlin** and **Jetpack Compose**. Designed with a beautiful, premium Ghibli-inspired aesthetic, Olive seamlessly bridges your local music library with powerful online streaming and social features.
## ✨ Features
- **Beautiful Compose UI:** Fully native, responsive UI featuring dynamic theming (Day/Night modes), glassmorphism effects, and fluid micro-animations.
- **Local & Online Playback:** Powerful playback engine powered by **Media3 / ExoPlayer**. Listen to your local library or stream music online.
- **Social Connect:** Sign in with Google to find friends via unique Friend Codes, share playlists instantly, and discover what your friends are listening to.
- **Download & Extract:** Built-in integration with `yt-dlp` and `FFmpeg` to extract media streams, download music, and transcode audio on the fly.
- **In-App Updates (OTA):** Automatic update checker that securely pulls the latest releases directly from GitHub, ensuring you're always on the latest version.
- **Library Management:** Organize your music by Playlists, Albums, Artists, Genres, and Folders (backed by **Room** Database).
## 🛠️ Tech Stack
Olive is built using modern Android development best practices:
- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- **Architecture:** MVVM (Model-View-ViewModel)
- **Dependency Injection:** [Dagger Hilt](https://dagger.dev/hilt/)
- **Media Playback:** [Media3 / ExoPlayer](https://developer.android.com/media/media3)
- **Database:** [Room](https://developer.android.com/training/data-storage/room)
- **Backend & Auth:** [Firebase](https://firebase.google.com/) (Auth, Firestore, Storage)
- **Networking:** [Retrofit](https://square.github.io/retrofit/) & [OkHttp](https://square.github.io/okhttp/)
- **Media Extraction:** [youtubedl-android](https://github.com/yausername/youtubedl-android) (yt-dlp + FFmpeg)
- **Image Loading:** [Coil](https://coil-kt.github.io/coil/compose/)
## 🚀 Getting Started
### Prerequisites
- Android Studio (latest version recommended)
- JDK 17
- Minimum Android SDK: API 26 (Android 8.0 Oreo)
- Target Android SDK: API 35
### Building the Project
1. Clone the repository:
   ```bash
   git clone https://github.com/Walid1231/Olive.git
   ```
2. Open the project in Android Studio.
3. Ensure your `JAVA_HOME` is pointed to the bundled Android Studio JDK (JBR).
4. Build the app using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```
### Firebase Setup
To build the app with all social and authentication features enabled, you must provide your own `google-services.json` file:
1. Create a project in the [Firebase Console](https://console.firebase.google.com/).
2. Enable Authentication (Google Sign-In) and Firestore.
3. Download the `google-services.json` file and place it in the `app/` directory.
## 📦 Releases & Updates
Olive features a built-in Over-The-Air (OTA) update system. When a new version is published to the GitHub Releases page (e.g., tagged as `v1.0.3`), the app will automatically notify users and allow them to download and install the `.apk` directly from within the app.
