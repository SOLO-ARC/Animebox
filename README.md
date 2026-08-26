# FireFly (Formerly AnimeBox)

<p align="center">
  <img src="https://i.ibb.co/gnNZN1m/c4ddde9b878e66a9cc468061220e40a5.gif" width="48" alt="Hey!" />
</p>

<p align="center">
  <img src="fireflytransparentlogo.png" width="180" alt="FireFly Logo" />
</p>

<p align="center">
  <a href="https://github.com/SOLO-ARC/Animebox/releases/latest"><b>Download Latest APK</b></a>
</p>

> **Note**: **AnimeBox** has officially been rebranded to **FireFly**!

FireFly is an Android application built for exploring and tracking anime on mobile devices. It fetches anime metadata, ratings, and series information directly from AniList, providing a clean interface, customizable video player controls, and multi-profile support.

---

## Why FireFly Exists

Many mainstream anime streaming services often fall short for dedicated fans. Platforms like Crunchyroll frequently remove anime titles from their catalog, offer dated user interfaces, and sometimes deliver lower video resolutions or basic subtitle options compared to modern media standards. On the other hand, platforms like Netflix offer a polished UI and high playback quality, but their anime collections are heavily region-locked, limited in size, or completely unavailable in many countries.

FireFly was created to solve these interface and accessibility issues by providing a smooth, user-focused mobile experience.

> **Important**: This source code **does not contain or host any streaming sources or copyrighted video content**.
> - **For Streaming Apps**: If you plan to build a video playback client, streaming API endpoints must be provided by you.
> - **For Database & Tracking Apps**: If you are building an anime rating, database, or tracking app, you can use AniList or TMDB metadata to redirect users to official legal streaming services like Crunchyroll, Netflix, or local providers available in their region.

---

## Screenshots

| Home | Details |
| :---: | :---: |
| <img src="app showcase screenshots/Home.jpg" width="340" alt="Home" /> | <img src="app showcase screenshots/Detail.jpg" width="340" alt="Details" /> |

| Search | Player |
| :---: | :---: |
| <img src="app showcase screenshots/Search.jpg" width="340" alt="Search" /> | <img src="app showcase screenshots/Player.jpg" width="340" alt="Player" /> |

---

## Features

- **AniList Data Integration**: Uses AniList to display anime information, ratings, genres, and cover art.
- **Multiple Profiles**: Support for creating and managing separate user profiles, including dedicated kids profiles.
- **Home & Banner Feed**: Hero carousel highlighting popular titles, along with a continue watching section.
- **Custom Player**: Built-in video player with volume/brightness gestures, skip intro/outro options, quality selectors, and subtitle controls.
- **Search & Discovery**: Search through anime series with category and tag filters.
- **Personal Watchlist**: Per-profile anime saving and watch history tracking.
- **Backup and Restore**: Export profile data and app settings to a JSON file and import them back anytime.

---

## Building from Source

> **Note**: To build and use this application from the source code, you can integrate your own streaming API sources or link direct watch URLs to official providers.

1. Clone this repository.
2. Open the `animebox` project folder in Android Studio.
3. Sync Gradle dependencies.
4. Run `assembleDebug` or install directly onto a connected Android device running Android 8.0+.
