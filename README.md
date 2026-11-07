# Neuro Player 🎵

<p align="center">
  <strong>A feature-rich music player for Android</strong><br>
  Built with Jetpack Compose and Material Design 3
</p>

---

## ✨ Features

### 🎨 Modern UI/UX
- **Material You** - Dynamic color theming that adapts to your wallpaper
- **Smooth Animations** - Fluid transitions and micro-interactions
- **Customizable UI** - Adjustable corner radius and navigation bar settings
- **Dark/Light Theme** - Automatic or manual theme switching
- **Album Art Colors** - Dynamic color extraction from album artwork

### 🎵 Powerful Playback
- **Media3 ExoPlayer** - Industry-leading audio engine with FFmpeg support
- **Background Playback** - Full media session integration
- **Queue Management** - Drag-and-drop reordering
- **Shuffle & Repeat** - All playback modes supported
- **Gapless Playback** - Seamless transitions between tracks
- **Custom Transitions** - Configure crossfades between songs

### 📚 Library Management
- **Multi-format Support** - MP3, FLAC, AAC, OGG, WAV, and more
- **Browse By** - Songs, Albums, Artists, Genres, Folders
- **Smart Artist Parsing** - Configurable delimiters for multi-artist tracks
- **Album Artist Grouping** - Proper album organization
- **Folder Filtering** - Choose which directories to scan

### 🔍 Discovery & Organization
- **Full-text Search** - Search across your entire library
- **Daily Mix** - AI-powered personalized playlist based on listening habits
- **Playlists** - Create and manage custom playlists
- **Statistics** - Track your listening history and habits

### 🎤 Lyrics
- **Synchronized Lyrics** - LRC format via LRCLIB API
- **Lyrics Editing** - Modify or add lyrics to your tracks
- **Scrolling Display** - Follow along as you listen

### 🖼️ Artist Artwork
- **Deezer Integration** - Automatic artist images from Deezer API
- **Smart Caching** - Memory (LRU) + database caching for offline access
- **Fallback Icons** - Beautiful placeholders when images unavailable

### 📲 Connectivity
- **Chromecast** - Stream to your TV or smart speakers
- **Android Auto** - Full Android Auto support for in-car playback (Soon)
- **Widgets** - Home screen control with Glance widgets

### ⚙️ Advanced Features
- **Tag Editor** - Edit metadata with TagLib (MP3, FLAC, M4A support)
- **AI Playlists** - Generate playlists with AI (Supports Gemini, Deepseek, OpenAI, etc.)

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| **Language** | [Kotlin](https://kotlinlang.org/) 100% |
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) |
| **Design System** | [Material Design 3](https://m3.material.io/) |
| **Audio Engine** | [Media3 ExoPlayer](https://developer.android.com/guide/topics/media/media3) + FFmpeg |
| **Architecture** | MVVM with StateFlow/SharedFlow |
| **DI** | [Hilt](https://dagger.dev/hilt/) |
| **Database** | [Room](https://developer.android.com/training/data-storage/room) |
| **Networking** | [Retrofit](https://square.github.io/retrofit/) + OkHttp |
| **Image Loading** | [Coil](https://coil-kt.github.io/coil/) |
| **Async** | Kotlin Coroutines & Flow |
| **Background Tasks** | WorkManager |
| **Metadata** | [TagLib](https://github.com/nicholaus/taglib-android) |
| **Widgets** | [Glance](https://developer.android.com/jetpack/compose/glance) |

---

## 📱 Requirements

- **Android 11** (API 30) or higher
- **6GB RAM** recommended for smooth performance

---
## 🚀 Getting Started

### Prerequisites

- A recent Android Studio release
- JDK 21

### Build

```sh
git clone https://github.com/Neuyngudcal/PixelPlayer.git
cd PixelPlayer
./gradlew :app:assembleDebug
```

---

## 📂 Project Structure

```
app/src/main/java/com/theveloper/pixelplay/
├── data/
│   ├── database/       # Room entities, DAOs, migrations
│   ├── model/          # Domain models (Song, Album, Artist, etc.)
│   ├── network/        # API services (LRCLIB, Deezer)
│   ├── preferences/    # DataStore preferences
│   ├── repository/     # Data repositories
│   ├── service/        # MusicService, HTTP server
│   └── worker/         # WorkManager sync workers
├── di/                 # Hilt dependency injection modules
├── presentation/
│   ├── components/     # Reusable Compose components
│   ├── navigation/     # Navigation graph
│   ├── screens/        # Screen composables
│   └── viewmodel/      # ViewModels
├── ui/
│   ├── glancewidget/   # Home screen widgets
│   └── theme/          # Colors, typography, theming
└── utils/              # Extensions and utilities
```

---

## 📄 License & credits

Neuro Player is licensed under the MIT License — see [LICENSE](LICENSE).

Neuro Player is based on [PixelPlayer](https://github.com/theovilardo/PixelPlayer) at commit
`39030156` (2026-05-12), the last revision published under the MIT License.
Copyright © 2024–2026 Theo Vilardo and PixelPlayer contributors.
