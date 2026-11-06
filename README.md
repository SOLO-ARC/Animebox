FireFly (Formerly AnimeBox)
<p align="center">
  <a href="https://github.com/SOLO-ARC/Animebox">
    <img src="https://github.com/SOLO-ARC/Animebox/raw/main/fireflytransparentlogo.png" alt="FireFly Logo" width="180">
  </a>
</p><p align="center">
  <a href="https://github.com/SOLO-ARC/Animebox/releases/latest"><strong>Download Latest APK</strong></a>
</p>[!CAUTION]
FireFly is fully and permanently shut down. There will be no further updates, maintenance, troubleshooting, or fixes. GitHub releases are planned for removal, so download an APK only if you understand that the app is unsupported and may stop working at any time. Do not expect broken streams, metadata, downloads, or other features to be repaired.

FireFly was an Android application built for exploring and tracking anime on mobile devices. It fetched anime metadata, ratings, and series information from AniList and provided a customizable viewing experience, multi-profile support, discovery tools, and experimental AI-powered features.

This README is also a record of the project and the features that were built. The app is no longer maintained.
Why FireFly Exists
Many mainstream anime services can be frustrating for dedicated fans. Catalogues may vary by region, titles can become unavailable, and the viewing experience or subtitle options may not meet everyone's preferences. FireFly was created to explore a smoother, more flexible mobile experience for anime discovery, organization, and playback.

The project grew beyond a basic anime browser. It included custom player controls, personal collections, studio-based discovery, video enhancement experiments, an in-app assistant, and AI subtitle transcription.
[!IMPORTANT]
This source code does not contain or host streaming sources or copyrighted video content.
- For streaming clients: Any streaming API endpoints or provider integrations must be supplied and operated by the developer.
- For metadata and tracking apps: AniList or TMDB can be used for metadata, and users can be directed to official legal streaming services available in their region.

Screenshots
The screenshots below show selected parts of the app and some of the features developed during the project.
Home, search, and anime discovery

Discover by animation studio
Browse collections grouped by animation studios, including MAPPA, ufotable, and WIT STUDIO.

Atlus in-app AI assistant
Atlus was designed to understand anime-related requests and help users find relevant episodes or perform small in-app actions. It used an OpenRouter API key supplied by the user.

Anime 4K enhancement
An experimental video enhancement feature intended primarily for capable devices, with alternative presets and custom tuning for other hardware.

AI subtitle transcription
AI-powered subtitle transcription was one of the project's most important experiments, intended to make some unsubtitled or older anime more accessible.

Personal collections
Organize anime into personal collections and keep related titles together.

Features
- AniList Data Integration: Anime information, ratings, genres, and cover art sourced from AniList.
- Multiple Profiles: Separate user profiles, including dedicated kids profiles.
- Home & Banner Feed: A featured-title carousel and continue-watching section.
- Custom Video Player: Playback controls, volume and brightness gestures, intro/outro skipping, quality selection, and subtitle controls.
- Search & Discovery: Anime search with category and tag filters.
- Studio Collections: Discover anime grouped by animation studio.
- Personal Watchlists & Collections: Save titles, organize collections, and track viewing history per profile.
- Backup & Restore: Export profile data and app settings to JSON and import them later.
- Atlus AI Assistant: An in-app assistant that could answer some anime-related queries and perform limited actions. It required the user's own OpenRouter API key.
- Anime 4K: Experimental video enhancement for supported devices, with additional presets and custom tuning.
- AI Subtitle Transcription: Experimental speech transcription intended to generate subtitles for content that may not have subtitles available.
- Downloads: Multiple provider/server choices, with performance dependent on the selected server.
- Experimental YouTube-style Player: An alternate playback interface, separate from the recommended default player.
Shutdown Notice & Final Status
This is a full and final shutdown, not a partial shutdown.

There will be no more app updates. I will no longer troubleshoot or fix broken streams, metadata loading, downloads, player issues, or other bugs. Please consider FireFly closed. It may continue to work for some users for a while, but that is not guaranteed.

The GitHub releases are planned for removal. If you still want an APK, download it from the GitHub Releases page while a release is available. Once releases are removed, they may no longer be accessible here.

The app currently includes multiple servers, and some may continue working for a few months. This is not a promise of continued availability, and none of those servers will be maintained by me. If the app stops working, please move to another application rather than expecting a fix.
Known issues and final guidance
The following notes are provided for existing users. They are not promises of support, and I will not be fixing these issues.
Streams and metadata loading
A major cause of slow or failed stream and metadata loading was DNS handling. DNS was not implemented consistently in the video player and other parts of the app. Changing DNS had previously helped with AniList metadata, but streams could still fail on networks where providers block requests.

If you are still using FireFly, trying a different DNS provider or waiting a little longer for the first stream may help in some cases. Results will vary, and I will no longer investigate or repair these problems.
In-app support, tickets, and comments
For questions about the app, users can still try the in-app support and ticket system while it remains available. The chat system requires an AniList login.

Comments in the app are provided through Dantotsu. Please be respectful when using them. Inappropriate behavior may result in a commenting ban. AniList login is also required for comments.
Atlus
Atlus is not as capable as Google AI Search or Gemini, particularly on newer Android versions where those services may already be integrated into the system. Its main advantage was being built directly into FireFly and being able to perform a limited number of small in-app actions.

It may still be useful to users on older Android versions without convenient Gemini integration. It should be treated as a limited assistant, not a replacement for a full AI search service.
Downloads
Download speeds depend on the server and its current performance. Choose whichever available server works best for you. Some servers may produce downloads that do not play correctly. This will not be fixed.
Anime 4K
Anime 4K was designed primarily for high-end devices and was developed alongside the video player. Lower-end devices can try the other presets or custom tuning, but performance and results will vary.

The feature can be particularly useful for experimenting with older, blurry, or low-quality anime. It is not guaranteed to improve every video or run smoothly on every device.
Experimental YouTube-style player
The experimental YouTube-style player generally works best with Japanese and English content, but it can be slow and is usually a downgrade from the default player. The default player is recommended.

You are welcome to try the experimental player if it works well on your device, but it has known drawbacks and will not receive further fixes.
Third-party server acknowledgements
Some servers from the open-source Anilili project were also added to FireFly. Thanks to the original creator for making that project open source.
Collections
The collections feature was explained in an earlier project post. Please refer to that post for its original explanation and details.
A Note About AI Subtitle Transcription
Of all the experimental features, AI subtitle transcription is the one I cared about most.

I have watched a lot of anime, especially older shows whose hand-drawn art styles remind me of my childhood. I love Studio Ghibli films for the same reason. Some older anime never received subtitles, and I wanted to experiment with a way to make that content more accessible.

The transcription feature is still imperfect, but it reached a state where the generated subtitles could be watched and followed. Some errors may remain, and viewers may need to mentally correct parts of the transcription.

This feature was a personal experiment as much as a technical one, and it represents one of the main reasons I enjoyed working on FireFly.
Alternative Apps
If you are looking for another app, you can explore alternatives such as:
- Tsuzuku
- Aniwings
- Animechicken
- Bibianime
- Mizuki
- Anicore
- Weebox
- MiruDL (downloads only)
- Anistation
- Anymex (extension-based)
- Cloudstream (extension-based)
- Zangetsu
- Hikari
- Th3-Anime
Availability, safety, features, and suitability can change. Please check each project's current status and use services responsibly.
Building from Source
The repository may remain available even after the app is discontinued. Building the project does not restore its hosted services or guarantee that its integrations will work.
1. Clone this repository.
2. Open the animebox project folder in Android Studio.
3. Sync the Gradle dependencies.
4. Run assembleDebug or install the build on a compatible Android device running Android 8.0 or later.
Important: The source code does not provide streaming sources or copyrighted video files. Developers experimenting with the code must supply their own lawful APIs, provider integrations, or direct watch URLs. Third-party services may change or become unavailable independently of this repository.

Final Words
I will remain in the community mostly as a spectator and may still offer guidance from time to time. If I work on another project in the future, it may be something different and is more likely to happen in collaboration with other developers. It will not be a continuation of FireFly.

Thank you to everyone who used the app, tested features, reported issues, shared feedback, or supported the project along the way.

FireFly is now fully shut down. I will not be returning to maintain it.
